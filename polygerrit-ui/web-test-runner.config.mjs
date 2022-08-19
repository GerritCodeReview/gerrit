import { esbuildPlugin } from "@web/dev-server-esbuild";

/** @type {import('@web/test-runner').TestRunnerConfig} */
const config = {
  files: "app/**/*_test.ts",
  nodeResolve: true,
  watch: true,
  testFramework: {
    config: {
      ui: "tdd",
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
