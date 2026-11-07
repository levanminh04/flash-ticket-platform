// Runner không thêm package: tái sử dụng Playwright CLI và các browser checks hiện có.
const fs = require("node:fs");
const path = require("node:path");
const { spawnSync } = require("node:child_process");

const root = path.resolve(__dirname, "../..");
const outputDir = path.join(root, "output/playwright");
fs.mkdirSync(outputDir, {recursive: true});
const args = process.argv.slice(2);
if (args.some(arg => !["--fixtures", "--live"].includes(arg)) || args.length !== 1) {
  console.error("Dùng: node tests/run-integration.cjs --fixtures | --live");
  process.exit(1);
}
const npx = process.platform === "win32"
  ? [process.execPath, path.join(path.dirname(process.execPath), "node_modules/npm/bin/npx-cli.js")]
  : ["npx"];
const cli = ["--yes", "--package", "@playwright/cli", "playwright-cli", args[0] === "--live" ? "-s=task14-live" : "-s=task14-fixtures"];
const readTest = file => fs.readFileSync(path.join(__dirname, file), "utf8").trim().replace(/;$/, "");
let credentials;

function runCli(commandArgs, timeout = 300000) {
  const result = spawnSync(npx[0], [...npx.slice(1), ...cli, ...commandArgs], {
    cwd: root, encoding: "utf8", timeout, maxBuffer: 4 * 1024 * 1024,
  });
  // CLI echo lại code sau kết quả; bỏ phần đó vì live code có credentials trong bộ nhớ.
  let output = (result.stdout || result.stderr || "").split("### Ran Playwright code")[0];
  if (credentials) {
    output = output.split(credentials.password).join("<redacted>").split(credentials.username).join("<redacted>");
  }
  if (result.status !== 0) throw new Error(output.trim() || result.error?.message || "Playwright CLI failed");
  const json = output.split("### Result\n")[1]?.trim();
  return json ? JSON.parse(json) : null;
}

try {
  // Chỉ dùng origin phục vụ FE đang mở; API/Keycloak do Vite nạp từ root .env.
  const origin = "http://127.0.0.1:5173";
  runCli(["open", origin, "--headed"], 60000);
  let result;
  if (args[0] === "--fixtures") {
    const groups = [
      ["public-api.browser.js"], ["header-responsive.browser.js"],
      ["home-layout.browser.js", "home-colors.browser.js", "home-polish.browser.js"],
      ["booking-flow.browser.js"], ["keycloak-nonce.browser.js"],
    ];
    const calls = groups.map(group => `{
      const context = await page.context().browser().newContext();
      try { const target = await context.newPage();
        ${group.map(file => `try { const result = await (${readTest(file)})(target);
          results.push({file:${JSON.stringify(file)},status:"PASS",...result});
        } catch(error) { results.push({file:${JSON.stringify(file)},status:"FAIL",error:error.message.split("\\n")[0]}); }`).join("\n")}
      } finally { await context.close(); }
    }`).join("\n");
    const runner = path.join(outputDir, "task14-regressions.browser.js");
    fs.writeFileSync(runner, `async page => { const results=[]; ${calls} return results; }`);
    result = runCli(["run-code", "--filename", runner]);
  } else {
    const seed = fs.readFileSync(path.join(root, "deploy/keycloak/postman/SEED-README.md"), "utf8");
    const row = seed.split(/\r?\n/).find(line => line.includes("| buyer@gmail.com |"));
    if (!row) throw new Error("Không có buyer seed trong SEED-README.md");
    const cells = row.split("|").map(cell => cell.trim());
    credentials = {username: cells[2], password: cells[3]};
    if (!credentials.password) throw new Error("Buyer seed thiếu mật khẩu");
    // Command line Windows có giới hạn độ dài; chỉ truyền config nhỏ qua bộ nhớ server.
    const code = `async page => {page.__task14Config=${JSON.stringify({origin, outputDir: outputDir.replace(/\\/g, "/"), ...credentials})}; return {configured:true};}`;
    runCli(["run-code", code]);
    result = runCli(["run-code", "--filename", path.join(__dirname, "integration-live.browser.js")]);
  }
  const file = path.join(outputDir, args[0] === "--fixtures" ? "task14-fixtures.json" : "task14-live.json");
  fs.writeFileSync(file, JSON.stringify(result, null, 2) + "\n");
  console.log(JSON.stringify(result, null, 2));
  console.log(`Báo cáo: ${file}`);
  // Không trả exit0 khi chỉ fixture pass nhưng nghiệm thu thật FAIL/BLOCKED.
  process.exitCode = Array.isArray(result) ? (result.some(suite => suite.status !== "PASS") ? 1 : 0) : (result.status === "PASS" ? 0 : 2);
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
} finally {
  try { runCli(["close"], 30000); } catch { /* Giữ lỗi kiểm thử ban đầu. */ }
}
