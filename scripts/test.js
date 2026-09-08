const { spawnSync } = require("node:child_process");

const command = process.platform === "win32" ? "gradlew.bat" : "./gradlew";
const result = spawnSync(command, ["test"], {
  stdio: "inherit",
  shell: process.platform === "win32",
});

if (result.error) console.error(result.error.message);
process.exit(result.status ?? 1);
