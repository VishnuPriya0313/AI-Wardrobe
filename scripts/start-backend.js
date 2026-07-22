const { spawn, spawnSync } = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");

const repoDir = path.resolve(__dirname, "..");
const backendDir = path.join(repoDir, "backend");

function loadEnvFile(filePath) {
  if (!fs.existsSync(filePath)) return;

  for (const rawLine of fs.readFileSync(filePath, "utf8").split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) continue;

    const separator = line.indexOf("=");
    if (separator <= 0) continue;

    const name = line.slice(0, separator).trim();
    let value = line.slice(separator + 1).trim();
    if (
      value.length >= 2 &&
      ((value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'")))
    ) {
      value = value.slice(1, -1);
    }

    if (process.env[name] === undefined) process.env[name] = value;
  }
}

function commandExists(command) {
  return spawnSync(command, ["--version"], { stdio: "ignore" }).status === 0;
}

loadEnvFile(path.join(repoDir, ".env"));

const mavenArguments = process.argv.slice(2);
if (!mavenArguments.length) mavenArguments.push("spring-boot:run");
const maven = process.platform === "win32" ? "mvn.cmd" : "mvn";

if (!commandExists(maven)) {
  console.error("Maven is required to start the backend, but it is not installed or not on PATH.");
  console.error("On macOS with Homebrew, install it with: brew install maven");
  console.error("You can also run the Spring Boot configuration from IntelliJ after adding the .env variables.");
  process.exit(1);
}

const child = spawn(maven, mavenArguments, {
  cwd: backendDir,
  env: process.env,
  stdio: "inherit",
});

child.on("error", (error) => {
  console.error(`Could not start Maven: ${error.message}`);
  process.exit(1);
});

child.on("exit", (code, signal) => {
  if (signal) process.kill(process.pid, signal);
  process.exit(code ?? 1);
});
