#!/usr/bin/env python3
"""Prepare a project-local ARM64 toolchain. Requires explicit SDK license consent.
SPDX-License-Identifier: Apache-2.0
No root commands, credentials, mirrors or changes to other projects.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import stat
import time
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / ".toolchain"
DOWNLOADS = TOOLS / "downloads"
SDK = TOOLS / "android-sdk"
RECORDS = []


def fetch(url, destination, expected=None, algorithm="sha256"):
    destination.parent.mkdir(parents=True, exist_ok=True)
    if not destination.exists():
        for attempt in range(3):
            try:
                request = urllib.request.Request(url, headers={"User-Agent": "LibreMonitor-toolchain/0.1"})
                with urllib.request.urlopen(request, timeout=120) as response, destination.open("wb") as out:
                    shutil.copyfileobj(response, out, 1024 * 1024)
                break
            except Exception:
                if attempt == 2:
                    raise
                time.sleep(2 * (attempt + 1))
    actual = hashlib.new(algorithm, destination.read_bytes()).hexdigest()
    if expected and actual.lower() != expected.lower():
        raise RuntimeError(f"Checksum mismatch for {destination.name}; remove this verified path manually before retry: {destination.resolve()}")
    digest = hashlib.sha256(destination.read_bytes()).hexdigest()
    print(f"Downloaded {destination.name}: {destination.stat().st_size} bytes, sha256={digest}", flush=True)
    RECORDS.append({"url": url, "file": destination.name, "bytes": destination.stat().st_size,
                    "sha256": digest, "verified_with_upstream_checksum": bool(expected)})
    return destination


def text(url):
    request = urllib.request.Request(url, headers={"User-Agent": "LibreMonitor-toolchain/0.1"})
    with urllib.request.urlopen(request, timeout=90) as response:
        return response.read(20 * 1024 * 1024).decode("utf-8")


def extract(archive, destination, strip_root=False):
    destination.mkdir(parents=True, exist_ok=True)
    base = destination.resolve()
    with zipfile.ZipFile(archive) as z:
        for entry in z.infolist():
            parts = Path(entry.filename).parts
            if strip_root:
                parts = parts[1:]
            if not parts:
                continue
            target = destination.joinpath(*parts)
            if not target.resolve().is_relative_to(base):
                raise RuntimeError("Archive path traversal rejected")
            if entry.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with z.open(entry) as source, target.open("wb") as out:
                    shutil.copyfileobj(source, out)
                mode = (entry.external_attr >> 16) & 0o777
                if mode:
                    target.chmod(mode)


def children(element, local):
    return [x for x in element if x.tag.split("}")[-1] == local]


def child(element, local):
    return next(iter(children(element, local)), None)


def prepare_sdk(accepted):
    if not accepted:
        raise RuntimeError("Review https://developer.android.com/studio/terms then pass --accept-android-sdk-license")
    metadata = text("https://dl.google.com/android/repository/repository2-1.xml")
    repository = ET.fromstring(metadata)
    packages = {node.attrib["path"]: node for node in repository.iter()
                if node.tag.split("}")[-1] == "remotePackage"}
    licenses = {node.attrib["id"]: (node.text or "") for node in repository.iter()
                if node.tag.split("}")[-1] == "license"}
    for package_name, destination in [
        ("platforms;android-35", SDK / "platforms" / "android-35"),
        ("build-tools;35.0.0", SDK / "build-tools" / "35.0.0"),
    ]:
        package = packages[package_name]
        archives = child(package, "archives")
        selected = None
        for archive in children(archives, "archive"):
            host = child(archive, "host-os")
            if host is None or host.text == "linux":
                selected = child(archive, "complete")
                break
        if selected is None:
            raise RuntimeError(f"No Linux/portable archive for {package_name}")
        filename = child(selected, "url").text
        checksum = child(selected, "checksum")
        archive = fetch("https://dl.google.com/android/repository/" + filename,
                        DOWNLOADS / filename, checksum.text.strip(), checksum.attrib.get("type", "sha1"))
        if not (destination / "source.properties").exists():
            extract(archive, destination, strip_root=True)
        reference = child(package, "uses-license")
        if reference is not None:
            identifier = reference.attrib["ref"]
            license_text = licenses[identifier].strip()
            license_dir = SDK / "licenses"
            license_dir.mkdir(parents=True, exist_ok=True)
            (license_dir / identifier).write_text(hashlib.sha1(license_text.encode()).hexdigest() + "\n")
            (TOOLS / (identifier + ".txt")).write_text(license_text)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--accept-android-sdk-license", action="store_true")
    args = parser.parse_args()
    if platform.machine() not in ("aarch64", "arm64"):
        raise RuntimeError("This helper is specifically for ARM64; use normal Android SDK tools elsewhere")
    TOOLS.mkdir(parents=True, exist_ok=True)
    expected = text("https://services.gradle.org/distributions/gradle-8.9-bin.zip.sha256").strip()
    gradle = fetch("https://services.gradle.org/distributions/gradle-8.9-bin.zip", DOWNLOADS / "gradle-8.9-bin.zip", expected)
    if not (TOOLS / "gradle-8.9" / "bin" / "gradle").exists():
        extract(gradle, TOOLS)
    prepare_sdk(args.accept_android_sdk_license)
    release = json.loads(text("https://api.github.com/repos/lzhiyong/android-sdk-tools/releases/tags/35.0.2"))
    asset = next(a for a in release["assets"] if a["name"] == "android-sdk-tools-static-aarch64.zip")
    digest = asset.get("digest")
    expected_digest = digest.split(":", 1)[1] if digest and digest.startswith("sha256:") else None
    archive = fetch(asset["browser_download_url"], DOWNLOADS / asset["name"], expected_digest)
    # Reviewed first-download baseline, not an independent upstream signature.
    # The release asset is mutable; never silently execute different bytes on a rerun.
    reviewed_sha256 = "db1cea2c4454d5f9c5a802646b2d1cf560b4ee7badbe23e51ab8e1881bb50fc2"
    if hashlib.sha256(archive.read_bytes()).hexdigest() != reviewed_sha256:
        raise RuntimeError("Third-party aapt2 archive changed; review provenance and update the pinned baseline explicitly")
    native = TOOLS / "android-native"
    extract(archive, native)
    matches = list(native.rglob("aapt2"))
    if len(matches) != 1:
        raise RuntimeError("Expected exactly one ARM64 aapt2")
    aapt2 = matches[0].resolve()
    aapt2.chmod(aapt2.stat().st_mode | stat.S_IXUSR)
    (TOOLS / "toolchain-provenance.json").write_text(json.dumps({
        "gradle_version": "8.9", "android_sdk_license_accepted_by_user": True,
        "native_tool_release": release["html_url"], "native_tool_tag_target": release["target_commitish"],
        "native_tool_release_asset_updated_at": asset["updated_at"],
        "warning": "aapt2 is third-party static AOSP build, not an official Google ARM64 binary. Local SHA256 is provenance, not independent verification when upstream digest is absent.",
        "downloads": RECORDS,
    }, indent=2))
    (ROOT / "local.properties").write_text("sdk.dir=" + str(SDK.resolve()) + "\n")
    (TOOLS / "aapt2-path").write_text(str(aapt2) + "\n")
    print("Toolchain prepared. Run file/readelf and aapt2 version before building.", flush=True)


if __name__ == "__main__":
    main()
