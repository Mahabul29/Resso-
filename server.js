// Zero-dependency server: serves index.html + sw.js (Monetag verification) and a small notification API.
const http = require("http");
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const PORT = process.env.PORT || 8000;
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || "";
const DATA_FILE = path.join(__dirname, "data.json");
const MAX_HISTORY = 50;

// Only these files are public static files.
const STATIC = {
  "/": ["index.html", "text/html; charset=utf-8"],
  "/index.html": ["index.html", "text/html; charset=utf-8"],
  "/sw.js": ["sw.js", "application/javascript; charset=utf-8"],
};

let notifications = [];
try {
  notifications = JSON.parse(fs.readFileSync(DATA_FILE, "utf8"));
} catch (_) {}

function save() {
  try { fs.writeFileSync(DATA_FILE, JSON.stringify(notifications)); } catch (_) {}
}

function send(res, code, body, headers = {}) {
  const isObj = typeof body === "object" && body !== null;
  res.writeHead(code, {
    "Content-Type": isObj ? "application/json; charset=utf-8" : "text/plain; charset=utf-8",
    "Cache-Control": "no-store",
    ...headers,
  });
  res.end(isObj ? JSON.stringify(body) : body);
}

// Very small brute-force limiter: 10 wrong passwords / 10 min / IP
const fails = new Map();
function isAdmin(req) {
  const ip = (req.headers["x-forwarded-for"] || req.socket.remoteAddress || "").split(",")[0].trim();
  const now = Date.now();
  const rec = (fails.get(ip) || []).filter((t) => now - t < 10 * 60 * 1000);
  if (rec.length >= 10) return "blocked";
  if (!ADMIN_PASSWORD) return "nopass";
  const given = String(req.headers["x-admin-key"] || "");
  const a = crypto.createHash("sha256").update(given).digest();
  const b = crypto.createHash("sha256").update(ADMIN_PASSWORD).digest();
  if (crypto.timingSafeEqual(a, b)) return true;
  rec.push(now);
  fails.set(ip, rec);
  return false;
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = "";
    req.on("data", (c) => {
      data += c;
      if (data.length > 20000) { reject(new Error("too big")); req.destroy(); }
    });
    req.on("end", () => resolve(data));
    req.on("error", reject);
  });
}

function cleanUrl(v) {
  v = String(v || "").trim();
  if (!v) return "";
  return /^https?:\/\//i.test(v) ? v.slice(0, 1000) : "";
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, "http://localhost");
  const p = url.pathname;

  // ---- Static ----
  if (req.method === "GET" && STATIC[p]) {
    const [file, type] = STATIC[p];
    try {
      const buf = fs.readFileSync(path.join(__dirname, file));
      return send(res, 200, buf.toString("utf8"), { "Content-Type": type });
    } catch (_) {
      return send(res, 404, "Not found");
    }
  }

  // ---- Public: app reads the latest notification ----
  if (req.method === "GET" && p === "/api/notification") {
    const latest = notifications[0] || null;
    return send(res, 200, { notification: latest }, { "Access-Control-Allow-Origin": "*" });
  }

  // ---- Admin API ----
  if (p.startsWith("/api/admin") ) {
    const auth = isAdmin(req);
    if (auth === "nopass") return send(res, 500, { error: "ADMIN_PASSWORD is not set on the server" });
    if (auth === "blocked") return send(res, 429, { error: "Too many attempts, try later" });
    if (auth !== true) return send(res, 401, { error: "Wrong password" });

    if (req.method === "GET" && p === "/api/admin/list") {
      return send(res, 200, { notifications });
    }

    if (req.method === "POST" && p === "/api/admin/send") {
      let body;
      try { body = JSON.parse(await readBody(req)); } catch (_) { return send(res, 400, { error: "Bad JSON" }); }
      const title = String(body.title || "").trim().slice(0, 100);
      const message = String(body.message || "").trim().slice(0, 500);
      if (!title || !message) return send(res, 400, { error: "Title and message are required" });
      const item = {
        id: crypto.randomUUID(),
        title,
        message,
        imageUrl: cleanUrl(body.imageUrl),
        linkUrl: cleanUrl(body.linkUrl),
        createdAt: Date.now(),
      };
      notifications.unshift(item);
      notifications = notifications.slice(0, MAX_HISTORY);
      save();
      return send(res, 200, { ok: true, notification: item });
    }

    if (req.method === "DELETE" && p.startsWith("/api/admin/delete/")) {
      const id = decodeURIComponent(p.split("/").pop());
      notifications = notifications.filter((n) => n.id !== id);
      save();
      return send(res, 200, { ok: true });
    }
  }

  send(res, 404, "Not found");
});

server.listen(PORT, () => console.log("Admin panel running on port " + PORT));
