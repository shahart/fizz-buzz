import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./tests/browser",
  fullyParallel: true,
  reporter: "list",
  use: {
    baseURL: "http://127.0.0.1:4179",
    ...devices["Desktop Chrome"],
  },
  webServer: {
    command: "node tests/static-server.mjs",
    url: "http://127.0.0.1:4179",
    reuseExistingServer: !process.env.CI,
  },
});
