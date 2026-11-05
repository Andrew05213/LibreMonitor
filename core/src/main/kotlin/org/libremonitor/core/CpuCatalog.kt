/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.core

enum class CoreTier(val label: String) { PRIME("超大核"), PERFORMANCE("性能核"), EFFICIENCY("能效核"), UNKNOWN("待识别核心") }
data class CpuCoreIdentity(val id: Int, val tier: CoreTier, val architecture: String?, val source: String)
data class SocEntry(val aliases: List<String>, val name: String, val vendor: String,
    val prime: Int, val performance: Int, val efficiency: Int, val reference: String)
data class CpuIdentity(
    val name: Reading<String> = Reading.unavailable("SoC", "系统没有提供可识别的芯片型号"),
    val rawModel: String = "",
    val cores: List<CpuCoreIdentity> = emptyList(),
    val catalog: SocEntry? = null,
)

object CpuCatalog {
    /** Maintained TSV of interface identifiers and factual product metadata; never fuzzy-matches a phone name. */
    fun parse(text: String): List<SocEntry> {
        val entries = text.lineSequence().filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
            val p = line.split('\t')
            require(p.size == 7) { "Invalid SoC catalog row" }
            SocEntry(p[0].split(',').map { it.trim().uppercase() }, p[1], p[2], p[3].toInt(), p[4].toInt(), p[5].toInt(), p[6])
                .also { require(it.prime >= 0 && it.performance >= 0 && it.efficiency >= 0 && it.prime + it.performance + it.efficiency > 0) }
        }.toList()
        val aliases = entries.flatMap { it.aliases }
        require(aliases.size == aliases.distinct().size) { "Duplicate SoC alias" }
        return entries
    }

    fun identify(entries: List<SocEntry>, models: List<String>, cpuInfo: String, capacities: Map<Int, Long>): CpuIdentity {
        val clean = models.map { it.trim() }.filter { it.isNotEmpty() && !it.equals("unknown", true) }
        val entry = clean.firstNotNullOfOrNull { model -> entries.firstOrNull { model.uppercase() in it.aliases } }
        val parts = linkedMapOf<Int, Pair<Int, Int>>()
        var id: Int? = null
        var implementer: Int? = null
        cpuInfo.lineSequence().forEach { line ->
            val key = line.substringBefore(':').trim()
            val value = line.substringAfter(':', "").trim()
            when (key.lowercase()) {
                "processor" -> { id = value.toIntOrNull(); implementer = null }
                "cpu implementer" -> implementer = value.removePrefix("0x").toIntOrNull(16)
                "cpu part" -> if (id != null && implementer != null) value.removePrefix("0x").toIntOrNull(16)?.let {
                    if (id!! in 0..255) parts[id!!] = implementer!! to it
                }
            }
        }
        val ids = (parts.keys + capacities.keys).distinct().sorted()
        val levels = capacities.values.filter { it > 0 }.distinct().sorted()
        val cores = ids.map { core ->
            val arm = parts[core]?.takeIf { it.first == 0x41 }?.second?.let(::armPart)
            if (arm != null) CpuCoreIdentity(core, arm.second, arm.first, "/proc/cpuinfo ARM CPU part")
            else {
                val capacity = capacities[core]
                val tier = if (levels.size >= 3 && capacity != null) when (capacity) {
                    levels.first() -> CoreTier.EFFICIENCY
                    levels.last() -> CoreTier.PRIME
                    else -> CoreTier.PERFORMANCE
                } else CoreTier.UNKNOWN
                CpuCoreIdentity(core, tier, null, if (tier == CoreTier.UNKNOWN) "未提供可核实的核心身份" else "按内核 cpu_capacity 分组推断")
            }
        }
        val name = when {
            entry != null -> Reading(entry.name, "本地 SoC 型号库：${entry.reference}", reason = "精确匹配 ${clean.firstOrNull { it.uppercase() in entry.aliases }}")
            clean.isNotEmpty() -> Reading(clean.first(), "Android Build SoC / 硬件标识", reason = "型号库尚未收录，显示系统原始标识")
            else -> Reading.unavailable("SoC", "系统没有提供可识别的芯片型号")
        }
        return CpuIdentity(name, clean.joinToString(" / "), cores, entry)
    }

    private fun armPart(part: Int): Pair<String, CoreTier>? = when (part) {
        0xd03 -> "Cortex-A53" to CoreTier.EFFICIENCY
        0xd05 -> "Cortex-A55" to CoreTier.EFFICIENCY
        0xd46 -> "Cortex-A510" to CoreTier.EFFICIENCY
        0xd80 -> "Cortex-A520" to CoreTier.EFFICIENCY
        0xd0b -> "Cortex-A76" to CoreTier.PERFORMANCE
        0xd0d -> "Cortex-A77" to CoreTier.PERFORMANCE
        0xd41 -> "Cortex-A78" to CoreTier.PERFORMANCE
        0xd47 -> "Cortex-A710" to CoreTier.PERFORMANCE
        0xd4d -> "Cortex-A715" to CoreTier.PERFORMANCE
        0xd81 -> "Cortex-A720" to CoreTier.PERFORMANCE
        0xd44 -> "Cortex-X1" to CoreTier.PRIME
        0xd48 -> "Cortex-X2" to CoreTier.PRIME
        0xd4e -> "Cortex-X3" to CoreTier.PRIME
        0xd82 -> "Cortex-X4" to CoreTier.PRIME
        else -> null
    }
}
