// PomoSolo Android（安卓端部门）
// 用途：把源项目（electron_pomodoro）的 PWA 构建产物同步到本工程 assets/
// 用法：node tools/copy-pwa-assets.mjs [pwa-dist路径]
// 默认源：D:/文件/lwq临时文件夹/软件工程/electron_pomodoro/pwa-dist
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, "..");

const DEFAULT_SOURCE = "D:/文件/lwq临时文件夹/软件工程/electron_pomodoro/pwa-dist";
const source = path.resolve(process.argv[2] ?? DEFAULT_SOURCE);
const dest = path.join(REPO_ROOT, "app", "src", "main", "assets");

console.log(`[copy-pwa-assets] source = ${source}`);
if (!fs.existsSync(source)) {
  console.error("[copy-pwa-assets] 源目录不存在，请用参数指定 pwa-dist 路径。");
  process.exit(1);
}

function copyDir(from, to) {
  fs.mkdirSync(to, { recursive: true });
  for (const entry of fs.readdirSync(from, { withFileTypes: true })) {
    const srcPath = path.join(from, entry.name);
    const dstPath = path.join(to, entry.name);
    if (entry.isDirectory()) {
      copyDir(srcPath, dstPath);
    } else if (entry.isFile()) {
      fs.copyFileSync(srcPath, dstPath);
    }
  }
}

try {
  fs.rmSync(dest, { recursive: true, force: true });
  copyDir(source, dest);

  let count = 0;
  function walk(dir) {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else count++;
    }
  }
  walk(dest);
  console.log(`[copy-pwa-assets] done: ${count} files synced`);
} catch (err) {
  console.error("[copy-pwa-assets] 复制失败:", err);
  process.exit(1);
}
