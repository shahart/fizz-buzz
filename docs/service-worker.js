const CACHE_NAME = "seven-boom-shell-v3";
const APP_SHELL = [
  "./",
  "./index.html",
  "./styles.css",
  "./manifest.webmanifest",
  "./icon-192.png",
  "./icon-512.png",
  "./icon-new.png",
  "./icon.svg",
  "./firebase-config.js",
  "./game-core.mjs",
  "./build/game-core.mjs",
  "./build/src/app.js",
  "./build/src/copy.js",
  "./build/src/events.js",
  "./build/src/render.js",
  "./wrong-buzzer-6268.mp3",
  "./u_o8xh7gwsrj-cute_happy_victory-476376.mp3",
  "./cyberwave-orchestra-cinematic-tension-suspenseful-thriller-music-loop-297627.mp3",
];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(APP_SHELL)));
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil((async () => {
    const names = await caches.keys();
    await Promise.all(
      names.filter((name) => name.startsWith("seven-boom-shell-") && name !== CACHE_NAME)
        .map((name) => caches.delete(name)),
    );
    await self.clients.claim();
  })());
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== "GET" || url.origin !== self.location.origin) return;

  event.respondWith((async () => {
    const cache = await caches.open(CACHE_NAME);
    try {
      const response = await fetch(request);
      if (response.ok && response.status === 200) {
        try { await cache.put(request, response.clone()); } catch { /* Keep the live response if storage is unavailable. */ }
      }
      return response;
    } catch {
      const cached = await cache.match(request);
      if (cached) return cached;
      if (request.mode === "navigate") return cache.match("./index.html");
      return Response.error();
    }
  })());
});
