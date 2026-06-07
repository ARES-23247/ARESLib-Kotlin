const fs = require("fs");
const path = require("path");
const http = require("http");
const https = require("https");
const { execSync, spawn } = require("child_process");
const readline = require("readline");

// 1. Auto-installation of dependencies if missing
const daemonDir = __dirname;
const nodeModulesDir = path.join(daemonDir, "node_modules");

if (!fs.existsSync(nodeModulesDir)) {
  console.log("[Daemon] node_modules not found. Executing automatic dependencies installation...");
  try {
    execSync("npm install", { cwd: daemonDir, stdio: "inherit" });
    console.log("[Daemon] Dependencies installed successfully.");
  } catch (err) {
    console.error("[Daemon] Failed to install dependencies. Make sure npm is installed and on your PATH:", err);
    process.exit(1);
  }
}

// Now we can safely import ws
const WebSocket = require("ws");

// Constants & Configurations
const PORT = process.env.PORT || 8080;
const REPO_ROOT = path.resolve(daemonDir, "../..");
const ALLOWED_ORIGINS = [
  "https://aresfirst-portal.web.app",
  "http://localhost:3000",
  "http://localhost:5173",
  "http://localhost:8080"
];

// Active state
let activeProcess = null;

// 2. Perform Environment Diagnostics
function checkEnvironment() {
  const diagnostics = {
    jdkValid: false,
    jdkVersion: "Unknown",
    gradlewExists: false,
  };

  // Check JDK
  try {
    let javaVersionOutput = "";
    try {
      javaVersionOutput = execSync("java -version", { stdio: "pipe" }).toString();
    } catch (err) {
      javaVersionOutput = (err.stdout ? err.stdout.toString() : "") + (err.stderr ? err.stderr.toString() : "");
    }
    
    if (!javaVersionOutput.trim()) {
      try {
        javaVersionOutput = execSync("java -version 2>&1", { stdio: "pipe" }).toString();
      } catch (e) {
        javaVersionOutput = e.toString();
      }
    }

    const versionMatch = javaVersionOutput.match(/(?:version\s*\"|openjdk\s*version\s*\"|openjdk\s*)([0-9\._a-zA-Z\-]+)/i);
    if (versionMatch) {
      diagnostics.jdkVersion = versionMatch[1];
      const cleanVer = versionMatch[1].replace(/[^0-9\.]/g, "");
      const major = parseInt(cleanVer.split(".")[0]);
      const realMajor = major === 1 ? parseInt(cleanVer.split(".")[1]) : major;
      diagnostics.jdkValid = realMajor >= 17;
    } else {
      const fallbackMatch = javaVersionOutput.match(/(\d+\.\d+\.\d+|\d+)/);
      if (fallbackMatch) {
        diagnostics.jdkVersion = fallbackMatch[1];
        const major = parseInt(fallbackMatch[1].split(".")[0]);
        const realMajor = major === 1 ? parseInt(fallbackMatch[1].split(".")[1]) : major;
        diagnostics.jdkValid = realMajor >= 17;
      }
    }
  } catch (err) {
    diagnostics.jdkVersion = "Not Installed / Not on PATH";
  }

  // Check Gradle Wrapper
  const gradlewName = process.platform === "win32" ? "gradlew.bat" : "gradlew";
  diagnostics.gradlewExists = fs.existsSync(path.join(REPO_ROOT, gradlewName));

  return diagnostics;
}

const env = checkEnvironment();
console.log("\n=== ARES Sim Launcher Daemon Diagnostics ===");
console.log(`Repository Root: ${REPO_ROOT}`);
console.log(`JDK Version:     ${env.jdkVersion} ${env.jdkValid ? "✅ (>= 17)" : "❌ (Requires JDK 17+)"}`);
console.log(`Gradle Wrapper:  ${env.gradlewExists ? "✅ Found" : "❌ Missing"}`);
console.log("============================================\n");

// 3. Initialize WebSocket Server with Secure/Unsecure HTTP
let server;
const sslDir = path.join(daemonDir, "ssl");
const certPath = path.join(sslDir, "cert.pem");
const keyPath = path.join(sslDir, "key.pem");

const hasSSL = fs.existsSync(certPath) && fs.existsSync(keyPath);

if (hasSSL) {
  console.log(`[Daemon] SSL Certificates found. Starting secure server (WSS)...`);
  const options = {
    cert: fs.readFileSync(certPath),
    key: fs.readFileSync(keyPath)
  };
  server = https.createServer(options, (req, res) => {
    res.writeHead(200);
    res.end("ARES Sim Launcher Daemon running securely (WSS).\n");
  });
} else {
  console.log(`[Daemon] SSL Certificates not found at ${certPath}. Starting standard server (WS)...`);
  console.log(`[Daemon] NOTE: Web browsers visiting production HTTPS portals will reject unsecure WS connections.`);
  console.log(`[Daemon] To enable WSS, generate certificates using mkcert and place them under:`);
  console.log(`         ${certPath}`);
  console.log(`         ${keyPath}\n`);
  server = http.createServer((req, res) => {
    res.writeHead(200);
    res.end("ARES Sim Launcher Daemon running (WS).\n");
  });
}

// Configure CORS and Handshake Origin Verification
const wss = new WebSocket.Server({
  server,
  verifyClient: (info) => {
    const origin = info.origin;
    // Allow development connections and explicitly approved production portal origins
    const isAllowed = ALLOWED_ORIGINS.some((allowed) => {
      if (allowed.includes("*")) {
        return origin.startsWith(allowed.replace("*", ""));
      }
      return origin === allowed;
    });

    if (!isAllowed) {
      console.warn(`[Daemon] Rejected connection from unauthorized origin: ${origin}`);
    }
    return isAllowed;
  }
});

wss.on("connection", (ws) => {
  console.log("[Daemon] Client connected.");

  // Send initial status check info
  ws.send(JSON.stringify({
    type: "status",
    status: activeProcess ? "running" : "idle",
    diagnostics: env
  }));

  ws.on("message", (messageStr) => {
    try {
      const msg = JSON.parse(messageStr);

      if (msg.type === "status") {
        ws.send(JSON.stringify({
          type: "status",
          status: activeProcess ? "running" : "idle",
          diagnostics: checkEnvironment()
        }));
      }

      else if (msg.type === "start") {
        if (activeProcess) {
          ws.send(JSON.stringify({ type: "log", line: "[Daemon Error] Simulator is already running." }));
          return;
        }

        const buildEnv = checkEnvironment();
        if (!buildEnv.jdkValid) {
          ws.send(JSON.stringify({ type: "log", line: `[Daemon Error] Invalid JDK version (${buildEnv.jdkVersion}). JDK 17+ is required.` }));
          ws.send(JSON.stringify({ type: "exit", code: 1, success: false }));
          return;
        }
        if (!buildEnv.gradlewExists) {
          ws.send(JSON.stringify({ type: "log", line: "[Daemon Error] Gradle wrapper not found in repository root." }));
          ws.send(JSON.stringify({ type: "exit", code: 1, success: false }));
          return;
        }

        // Handle EKF overrides config if provided
        const configOverridePath = path.join(REPO_ROOT, "config_override.json");
        if (msg.params) {
          try {
            fs.writeFileSync(configOverridePath, JSON.stringify(msg.params, null, 2));
            const logParams = { ...msg.params };
            if (logParams.obstacles) {
              logParams.obstacles = `[${logParams.obstacles.length} obstacles]`;
            }
            if (logParams.elements) {
              logParams.elements = `[${logParams.elements.length} elements]`;
            }
            if (logParams.elementTypes) {
              logParams.elementTypes = `[${logParams.elementTypes.length} element types]`;
            }
            ws.send(JSON.stringify({ type: "log", line: `[Daemon] Wrote config overrides: ${JSON.stringify(logParams)}` }));
          } catch (e) {
            ws.send(JSON.stringify({ type: "log", line: `[Daemon Warning] Failed to write config_override.json: ${e.message}` }));
          }
        } else {
          // Clean up any stale override files
          if (fs.existsSync(configOverridePath)) {
            try { fs.unlinkSync(configOverridePath); } catch (e) {}
          }
        }

        ws.send(JSON.stringify({ type: "log", line: "[Daemon] Spawning Gradle simulator task..." }));

        const gradlewCmd = process.platform === "win32" ? "gradlew.bat" : "./gradlew";
        const args = [":simulator:run"];

        try {
          activeProcess = spawn(gradlewCmd, args, {
            cwd: REPO_ROOT,
            shell: true,
            env: { ...process.env, JAVA_OPTS: "-XX:+UseG1GC" }
          });

          // Line-by-line stdout parsing
          const stdoutRl = readline.createInterface({ input: activeProcess.stdout });
          stdoutRl.on("line", (line) => {
            if (ws.readyState === WebSocket.OPEN) {
              ws.send(JSON.stringify({ type: "log", line }));
            }
          });

          // Line-by-line stderr parsing
          const stderrRl = readline.createInterface({ input: activeProcess.stderr });
          stderrRl.on("line", (line) => {
            if (ws.readyState === WebSocket.OPEN) {
              ws.send(JSON.stringify({ type: "log", line: `[ERROR] ${line}` }));
            }
          });

          activeProcess.on("exit", (code) => {
            console.log(`[Daemon] Simulator exited with code ${code}`);
            activeProcess = null;
            // Clean up config_override.json
            const configOverridePath = path.join(REPO_ROOT, "config_override.json");
            if (fs.existsSync(configOverridePath)) {
              try { fs.unlinkSync(configOverridePath); } catch (e) {}
            }
            if (ws.readyState === WebSocket.OPEN) {
              ws.send(JSON.stringify({
                type: "exit",
                code: code ?? 0,
                success: code === 0
              }));
            }
          });

          activeProcess.on("error", (err) => {
            console.error("[Daemon] Failed to start subprocess:", err);
            activeProcess = null;
            if (ws.readyState === WebSocket.OPEN) {
              ws.send(JSON.stringify({ type: "log", line: `[Daemon Subprocess Error] ${err.message}` }));
              ws.send(JSON.stringify({ type: "exit", code: 1, success: false }));
            }
          });

        } catch (spawnErr) {
          console.error("[Daemon] Spawning error:", spawnErr);
          activeProcess = null;
          ws.send(JSON.stringify({ type: "log", line: `[Daemon Spawn Error] ${spawnErr.message}` }));
          ws.send(JSON.stringify({ type: "exit", code: 1, success: false }));
        }
      }

      else if (msg.type === "stop") {
        if (!activeProcess) {
          ws.send(JSON.stringify({ type: "log", line: "[Daemon] Simulator is not running." }));
          return;
        }

        console.log("[Daemon] Force-stopping simulator process tree...");
        ws.send(JSON.stringify({ type: "log", line: "[Daemon] Sending termination signal to process tree..." }));

        // Gracefully kill process tree
        if (process.platform === "win32") {
          // Use taskkill to kill the spawned process and any of its children recursively (/T)
          try {
            execSync(`taskkill /pid ${activeProcess.pid} /f /t`);
          } catch (e) {
            // If taskkill fails, fallback to standard kill
            activeProcess.kill();
          }
        } else {
          // Send SIGTERM to process group
          activeProcess.kill("SIGTERM");
        }
      }
    } catch (parseErr) {
      console.error("[Daemon] Failed to process incoming WS frame:", parseErr);
    }
  });

  ws.on("close", () => {
    console.log("[Daemon] Client disconnected.");
    // Clean up config_override.json
    const configOverridePath = path.join(REPO_ROOT, "config_override.json");
    if (fs.existsSync(configOverridePath)) {
      try { fs.unlinkSync(configOverridePath); } catch (e) {}
    }
    // Safety check: Kill simulator if connection drops to prevent headless memory leaks
    if (activeProcess) {
      console.log("[Daemon] WebSocket closed. Terminating active simulator process...");
      if (process.platform === "win32") {
        try { execSync(`taskkill /pid ${activeProcess.pid} /f /t`); } catch (e) { activeProcess.kill(); }
      } else {
        activeProcess.kill("SIGTERM");
      }
      activeProcess = null;
    }
  });
});

server.listen(PORT, () => {
  const protocol = hasSSL ? "wss" : "ws";
  console.log(`[Daemon] Server listening on port ${PORT} (${protocol}://localhost:${PORT})`);
});
