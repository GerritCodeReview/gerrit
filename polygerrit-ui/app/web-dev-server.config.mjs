import cors from "@koa/cors";

/** @type {import('@web/dev-server').DevServerConfig} */
export default {
  nodeResolve: {
    moduleDirectories: ["../../../polygerrit-ui/app/node_modules"],
  },
  rootDir: "../../.ts-out/polygerrit-ui/app",
  middleware: [
    // Allow files served from the localhost domain to be used on any domain
    // (ex: gerrit-review.googlesource.com), which happens during local
    // development with Gerrit HE Helper extension.
    cors({ origin: "*" }),
    // The issue solved here is that our production index.html does not load
    // 'gr-app.js' as an ESM module due to our build process, but in development
    // all our source code is written as ESM modules. When using the Gerrit FE
    // Helper extension to see our local changes on a production site we see a
    // syntax error due to this mismatch. The trick used to fix this is to
    // rewrite the response for 'gr-app.js' to be a dynamic import() statement
    // for a fake file 'gr-app.mjs'. This fake file will be loaded as an ESM
    // module and when the server receives the request it returns the real
    // contents of 'gr-app.js'.
    async (context, next) => {
      const isGrAppMjs = context.url.includes("gr-app.mjs");
      if (isGrAppMjs) {
        context.url = context.url.replace("gr-app.mjs", "gr-app.js");
      }

      // Pass control to the next middleware which eventually loads the file.
      // see https://koajs.com/#cascading
      await next();

      if (!isGrAppMjs && context.url.includes("gr-app.js")) {
        context.body = "import('./gr-app.mjs')";
      }
    },
  ],
};
