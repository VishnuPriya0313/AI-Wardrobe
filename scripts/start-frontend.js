const { spawn, spawnSync } = require("node:child_process");
const path = require("node:path");

const repoDir = path.resolve(__dirname, "..");
const frontendDir = path.join(repoDir, "frontend");
const port = process.argv[2] || "5173";

function commandExists(command) {
  return spawnSync(command, ["--version"], { stdio: "ignore" }).status === 0;
}

const python = ["python3", "python"].find(commandExists);
if (!python) {
  console.error("Python 3 is required to serve the frontend, but it was not found on PATH.");
  process.exit(1);
}

const child = spawn(
  python,
  ["-m", "http.server", port, "--bind", "0.0.0.0", "--directory", frontendDir],
  { cwd: repoDir, stdio: "inherit" },
);

child.on("error", (error) => {
  console.error(`Could not start the frontend: ${error.message}`);
  process.exit(1);
});

child.on("exit", (code, signal) => {
  if (signal) process.kill(process.pid, signal);
  process.exit(code ?? 1);
});
