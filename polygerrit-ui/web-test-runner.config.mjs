import { esbuildPlugin } from "@web/dev-server-esbuild";

/** @type {import('@web/test-runner').TestRunnerConfig} */
const config = {
  files: ["app/**/*_test.{ts,js}", "!**/node_modules/**/*"],
  port: 9876,
  nodeResolve: true,
  testFramework: {
    config: {
      ui: "tdd",
      timeout: 5000,
    },
  },
  plugins: [
    esbuildPlugin({
      ts: true,
      target: "es2020",
      tsconfig: "app/tsconfig.json",
    }),
  ],
};
export default config;
