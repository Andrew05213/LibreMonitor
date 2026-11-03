# 最终集成补充：先写完整脚本、再读 stdout 的循环等待

Lead 静态核对到 `RootReader.readFrame()` 的 operation 顺序是先 `writeScript(outputStream, frameScript(...))`，再 `RootProtocol.readFrame(inputStream, ...)`。当前脚本约92KB，且是直接逐条执行的命令序列。

如果 stdin/stdout 管道较小、或较早的 `/proc/stat` 输出较大，shell 可能在尚未消费完整输入脚本时因 stdout 写满而阻塞；调用方又因剩余脚本尚未写完而阻塞，无法开始读取 stdout。2秒超时可以关闭会话，能安全失败，但真实 Root 读数可能始终不可用。这是功能性问题，不能仅用小 ByteArray fixture 证明不存在。

请最终集成至少选一种修复：
1. 将整份固定帧脚本包成一个完整的 POSIX compound command（例如 `{\n ... \n}\n`），让 shell 在执行输出前先读完这一完整语法单元，避免写脚本阶段提前输出；或
2. 在统一 deadline 与 owned-session 清理规则下并行发送固定脚本和解析输出，保证两边异常均使会话失效，不增加无限线程/队列。

建议补小容量双向管道/无特权 shell 合成回归。不得为测试执行 su，也不要为了通过测试取消帧完整性、输出上限或超时控制。上述为静态时序风险，尚未声称已在本机 Root 上复现。
