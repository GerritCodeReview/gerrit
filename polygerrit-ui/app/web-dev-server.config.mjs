import cors from "@koa/cors";

/** @type {import('@web/dev-server').DevServerConfig} */
const config = {
  nodeResolve: {
    moduleDirectories: ["../../../polygerrit-ui/app/node_modules"],
  },
  rootDir: "../../.ts-out/polygerrit-ui/app",
  middleware: [cors({ origin: "*" })],
};

export default config;
