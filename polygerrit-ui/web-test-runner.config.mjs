import { esbuildPlugin } from "@web/dev-server-esbuild";
import { defaultReporter, summaryReporter } from "@web/test-runner";
import { visualRegressionPlugin } from "@web/test-runner-visual-regression/plugin";

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
    visualRegressionPlugin({
      update: process.argv.includes("--update-visual-baseline"),
    }),
  ],
  reporters: [defaultReporter(), summaryReporter()],
};
export default config;
