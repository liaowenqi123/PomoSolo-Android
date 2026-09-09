// 安卓端部门工具：在源项目（electron_pomodoro）目录内执行 git 命令
// 用法：node tools/run-git-electron.mjs <git args...>（例如 status）
// 说明：通过 Node child_process 在中文路径的工作目录运行 git，规避控制台编码问题。
import { execFileSync } from "node:child_process";

const REPO = "D:/文件/lwq临时文件夹/软件工程/electron_pomodoro";
const args = process.argv.slice(2);
if (!args.length) {
  console.error("用法: node tools/run-git-electron.mjs <git参数...>");
  process.exit(1);
}

try {
  const out = execFileSync("git", args, {
    cwd: REPO,
    encoding: "utf8",
    stdio: ["ignore", "pipe", "inherit"],
    env: { ...process.env, GIT_TERMINAL_PROMPT: "0" },
  });
  process.stdout.write(out);
} catch (err) {
  process.stderr.write(String(err.stderr ?? err.message));
  process.exit(err.status ?? 1);
}
