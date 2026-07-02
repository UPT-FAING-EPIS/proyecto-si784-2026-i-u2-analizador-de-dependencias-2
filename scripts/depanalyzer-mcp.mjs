#!/usr/bin/env node

import { spawn } from "node:child_process";
import { existsSync, mkdtempSync, readFileSync, rmSync, statSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, "..");
const gradleWrapper = resolve(repoRoot, "gradlew.bat");
const launcher = resolve(repoRoot, "build", "install", "depanalyzer", "bin", "depanalyzer.bat");

let inputBuffer = Buffer.alloc(0);

process.stdin.on("data", (chunk) => {
  inputBuffer = Buffer.concat([inputBuffer, chunk]);
  drainMessages();
});

function drainMessages() {
  while (true) {
    const headerEnd = inputBuffer.indexOf("\r\n\r\n");
    if (headerEnd === -1) return;

    const header = inputBuffer.subarray(0, headerEnd).toString("utf8");
    const lengthMatch = /^Content-Length:\s*(\d+)$/im.exec(header);
    if (!lengthMatch) {
      inputBuffer = inputBuffer.subarray(headerEnd + 4);
      continue;
    }

    const contentLength = Number.parseInt(lengthMatch[1], 10);
    const messageStart = headerEnd + 4;
    const messageEnd = messageStart + contentLength;
    if (inputBuffer.length < messageEnd) return;

    const rawMessage = inputBuffer.subarray(messageStart, messageEnd).toString("utf8");
    inputBuffer = inputBuffer.subarray(messageEnd);

    try {
      handleMessage(JSON.parse(rawMessage));
    } catch (error) {
      writeError(null, -32700, `Invalid JSON: ${error.message}`);
    }
  }
}

async function handleMessage(message) {
  if (!message || typeof message !== "object") return;
  const { id, method, params } = message;

  if (id === undefined) {
    return;
  }

  try {
    switch (method) {
      case "initialize":
        writeResult(id, {
          protocolVersion: params?.protocolVersion ?? "2025-06-18",
          capabilities: {
            tools: {}
          },
          serverInfo: {
            name: "depanalyzer-mcp",
            version: "1.0.0"
          }
        });
        break;
      case "tools/list":
        writeResult(id, {
          tools: [
            {
              name: "analyze_dependencies",
              description: "Analiza un proyecto Maven, Gradle, npm o Python con depanalyzer y devuelve el reporte JSON.",
              inputSchema: {
                type: "object",
                properties: {
                  projectPath: {
                    type: "string",
                    description: "Ruta absoluta o relativa al proyecto que se va a analizar. Por defecto usa el repo actual."
                  },
                  dynamic: {
                    type: "boolean",
                    description: "Fuerza analisis dinamico con Maven/Gradle dependency tree."
                  },
                  offline: {
                    type: "boolean",
                    description: "Usa analisis estatico y evita Maven dependency:tree."
                  },
                  source: {
                    type: "string",
                    enum: ["auto", "oss", "nvd"],
                    description: "Fuente de vulnerabilidades. auto permite fallback."
                  },
                  showChains: {
                    type: "boolean",
                    description: "Incluye cadenas de vulnerabilidades."
                  },
                  treeDepth: {
                    type: "integer",
                    minimum: 1,
                    description: "Profundidad maxima del arbol de dependencias."
                  },
                  timeoutSeconds: {
                    type: "integer",
                    minimum: 1,
                    description: "Timeout del analisis en segundos."
                  }
                },
                additionalProperties: false
              }
            },
            {
              name: "depanalyzer_help",
              description: "Muestra la ayuda del CLI depanalyzer o de un subcomando.",
              inputSchema: {
                type: "object",
                properties: {
                  command: {
                    type: "string",
                    enum: ["root", "analyze", "tui", "update"],
                    description: "Comando para el que se mostrara ayuda."
                  }
                },
                additionalProperties: false
              }
            }
          ]
        });
        break;
      case "tools/call":
        writeResult(id, await callTool(params));
        break;
      default:
        writeError(id, -32601, `Unsupported method: ${method}`);
    }
  } catch (error) {
    writeError(id, -32000, error.message);
  }
}

async function callTool(params) {
  const name = params?.name;
  const args = params?.arguments ?? {};

  if (name === "analyze_dependencies") {
    return analyzeDependencies(args);
  }

  if (name === "depanalyzer_help") {
    return cliHelp(args);
  }

  throw new Error(`Unknown tool: ${name}`);
}

async function analyzeDependencies(args) {
  await ensureLauncher();

  const targetPath = resolveProjectPath(args.projectPath ?? repoRoot);
  const tempDir = mkdtempSync(resolve(tmpdir(), "depanalyzer-mcp-"));
  const cliArgs = ["--no-telemetry", "analyze", targetPath, "--output", "json", "--no-color"];

  if (args.dynamic) cliArgs.push("--dynamic");
  if (args.offline) cliArgs.push("--offline");
  if (args.showChains) cliArgs.push("--show-chains");
  if (args.treeDepth !== undefined) cliArgs.push("--tree-depth", String(args.treeDepth));
  if (args.timeoutSeconds !== undefined) cliArgs.push("--timeout", String(args.timeoutSeconds));
  if (args.source === "oss") cliArgs.push("--oss");
  if (args.source === "nvd") cliArgs.push("--nvd");

  try {
    const result = await runCommand(launcher, cliArgs, { cwd: tempDir, timeoutMs: (args.timeoutSeconds ?? 1800) * 1000 + 30000 });
    const reportPath = resolve(tempDir, "dependency-report.json");
    const report = existsSync(reportPath) ? JSON.parse(readFileSync(reportPath, "utf8")) : null;
    const summary = report
      ? {
          projectName: report.projectName,
          upToDate: report.upToDate?.length ?? 0,
          outdated: report.outdated?.length ?? 0,
          directVulnerable: report.directVulnerable?.length ?? 0,
          transitiveVulnerable: report.transitiveVulnerable?.length ?? 0,
          vulnerabilityChains: report.vulnerabilityChains?.length ?? 0
        }
      : null;

    return toolText({
      ok: result.exitCode === 0,
      command: `${launcher} ${cliArgs.join(" ")}`,
      exitCode: result.exitCode,
      summary,
      report,
      stdout: result.stdout.trim(),
      stderr: result.stderr.trim()
    });
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
}

async function cliHelp(args) {
  await ensureLauncher();

  const command = args.command ?? "root";
  const cliArgs = ["--no-telemetry"];
  if (command !== "root") cliArgs.push(command);
  cliArgs.push("--help");

  const result = await runCommand(launcher, cliArgs, { cwd: repoRoot, timeoutMs: 30000 });
  return toolText({
    ok: result.exitCode === 0,
    command: `${launcher} ${cliArgs.join(" ")}`,
    exitCode: result.exitCode,
    stdout: result.stdout.trim(),
    stderr: result.stderr.trim()
  });
}

async function ensureLauncher() {
  if (existsSync(launcher)) return;
  if (!existsSync(gradleWrapper)) {
    throw new Error(`No existe el wrapper de Gradle: ${gradleWrapper}`);
  }

  const result = await runCommand(gradleWrapper, ["installDist"], { cwd: repoRoot, timeoutMs: 10 * 60 * 1000 });
  if (result.exitCode !== 0 || !existsSync(launcher)) {
    throw new Error(`No se pudo construir depanalyzer.\nSTDOUT:\n${result.stdout}\nSTDERR:\n${result.stderr}`);
  }
}

function resolveProjectPath(projectPath) {
  const targetPath = resolve(repoRoot, projectPath);
  if (!existsSync(targetPath)) {
    throw new Error(`La ruta no existe: ${targetPath}`);
  }
  if (!statSync(targetPath).isDirectory()) {
    throw new Error(`La ruta debe ser un directorio: ${targetPath}`);
  }
  return targetPath;
}

function runCommand(command, args, { cwd, timeoutMs }) {
  return new Promise((resolvePromise, reject) => {
    const child = spawn(command, args, {
      cwd,
      env: {
        ...process.env,
        NO_COLOR: "1"
      },
      windowsHide: true
    });

    let stdout = "";
    let stderr = "";
    let settled = false;
    const timer = setTimeout(() => {
      if (settled) return;
      settled = true;
      child.kill("SIGTERM");
      reject(new Error(`Timeout ejecutando ${command}`));
    }, timeoutMs);

    child.stdout.on("data", (chunk) => {
      stdout += chunk.toString("utf8");
    });
    child.stderr.on("data", (chunk) => {
      stderr += chunk.toString("utf8");
    });
    child.on("error", (error) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      reject(error);
    });
    child.on("close", (exitCode) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      resolvePromise({ exitCode, stdout, stderr });
    });
  });
}

function toolText(value) {
  return {
    content: [
      {
        type: "text",
        text: JSON.stringify(value, null, 2)
      }
    ]
  };
}

function writeResult(id, result) {
  writeMessage({
    jsonrpc: "2.0",
    id,
    result
  });
}

function writeError(id, code, message) {
  writeMessage({
    jsonrpc: "2.0",
    id,
    error: {
      code,
      message
    }
  });
}

function writeMessage(message) {
  const body = JSON.stringify(message);
  process.stdout.write(`Content-Length: ${Buffer.byteLength(body, "utf8")}\r\n\r\n${body}`);
}
