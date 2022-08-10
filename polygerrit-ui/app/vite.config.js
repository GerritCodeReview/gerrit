import { defineConfig } from "vite";

export default defineConfig({
  server: {
    proxy: {
      "/auth-check": {
        target: "http://localhost:8080",
        changeOrigin: true,
        secure: false,
      },
      "/accounts/": {
        target: "http://localhost:8080",
        changeOrigin: true,
        secure: false,
      },
      "/changes/": {
        target: "http://localhost:8080",
        changeOrigin: true,
        secure: false,
      },
      "/config/": {
        target: "http://localhost:8080",
        changeOrigin: true,
        secure: false,
      },
      "/Documentation/": {
        target: "http://localhost:8080",
        changeOrigin: true,
        secure: false,
      },
      "^.*/fonts/.*.woff2": {
        target: "http://localhost:8080",
        changeOrigin: true,
        secure: false,
      },
      "/projects/": {
        target: "http://localhost:8080",
        changeOrigin: true,
        secure: false,
      },
    },
  },
});
