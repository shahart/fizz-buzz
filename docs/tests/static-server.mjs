import { createReadStream } from "node:fs";
import { stat } from "node:fs/promises";
import { createServer } from "node:http";
import { extname, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(fileURLToPath(new URL("..", import.meta.url)));
const types = {
  ".css": "text/css; charset=utf-8",
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".mjs": "text/javascript; charset=utf-8",
  ".svg": "image/svg+xml",
};

createServer(async (request, response) => {
  const pathname = decodeURIComponent(new URL(request.url, "http://localhost").pathname);
  const requested = resolve(root, `.${pathname === "/" ? "/index.html" : pathname}`);
  if (requested !== root && !requested.startsWith(`${root}${sep}`)) {
    response.writeHead(403).end();
    return;
  }
  try {
    const info = await stat(requested);
    if (!info.isFile()) throw new Error("Not a file");
    response.writeHead(200, { "content-type": types[extname(requested)] || "application/octet-stream" });
    createReadStream(requested).pipe(response);
  } catch {
    response.writeHead(404).end("Not found");
  }
}).listen(4179, "127.0.0.1");
