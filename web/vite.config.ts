import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

// In development the API is proxied so the browser sees one origin and no CORS setup is needed.
const api = process.env.FEEDLAB_API ?? "http://localhost:8080";

export default defineConfig({
  plugins: [react()],
  server: { proxy: { "/api": api } },
  test: { environment: "jsdom", include: ["src/**/*.test.{ts,tsx}"] },
});
