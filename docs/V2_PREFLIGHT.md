# v0.2 首轮预检（非最终结果）

Lead 在所有实现写入冻结、两个只读审阅仍进行时运行：

`bash scripts/build-local.sh :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --stacktrace`

- 完整日志：项目根 `v02-preflight.log`（被 gitignore 排除）。
- 退出码 1；Kotlin/Compose 编译已通过，但 app 单元测试阻止本轮全部任务成功。
- App 单测运行32项，1项失败：`RootProtocolTest.fixedAllowlistAndLimits` 第25行，期望478，实际479。
- 需要核对完整固定节点白名单是否应包含独立 thermalservice 记录，再修实现或测试期望；不要仅为绿色结果删除断言或盲改数字。
- 其他审阅问题仍应按 V2_ROOT_REVIEW/V2_DATA_REVIEW 修复。
- 本轮未安装/启动 APK，也未执行 su；不得把当前输出目录可能留存的首版 APK 当作 v0.2 已成功交付。

此预检已结束，不再占用 Gradle；最终 Terra 集成任务可以安全进行后续构建。
