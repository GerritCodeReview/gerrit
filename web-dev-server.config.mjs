import { esbuildPlugin } from "@web/dev-server-esbuild";
import cors from "@koa/cors";
import path from 'node:path';
import fs from 'node:fs';
import { createRequire } from 'node:module';

// esbuild is not a direct dependency: use the copy that
// @web/dev-server-esbuild runs on.
const esbuild = createRequire(
  createRequire(import.meta.url).resolve("@web/dev-server-esbuild")
)("esbuild");

// Scripts that production serves as bundles built by Bazel, see
// polygerrit-ui/app/rules.bzl and lib/highlightjs/BUILD. Web workers load them
// with importScripts(), so they must be classic scripts, not ES modules.
const bundles = {
  "/workers/syntax-worker.js": {
    entryPoints: ["polygerrit-ui/app/workers/syntax-worker.ts"],
    tsconfig: "polygerrit-ui/app/tsconfig.json",
  },
  "/bower_components/highlightjs/highlight.min.js": {
    entryPoints: ["lib/highlightjs/index.js"],
    globalName: "hljs",
    footer: { js: "hljs = hljs.default;" },
  },
};

/** @type {import('@web/dev-server').DevServerConfig} */
export default {
  port: 8081,
  plugins: [
    esbuildPlugin({
      ts: true,
      target: "es2020",
      tsconfig: "polygerrit-ui/app/tsconfig.json",
    }),
  ],
  nodeResolve: true,
  rootDir: "polygerrit-ui/app",
  middleware: [
    // Allow files served from the localhost domain to be used on any domain
    // (ex: gerrit-review.googlesource.com), which happens during local
    // development with Gerrit FE Helper extension.
    cors({ origin: "*" }),
    // Map some static assets.
    // When creating the bundle, the files are moved by polygerrit_bundle() in
    // polygerrit-ui/app/rules.bzl
    async (context, next) => {

      if ( context.url.startsWith( "/fonts/" ) ) {
        const fontFile = path.join( "lib/fonts", path.basename(context.url) );
        context.body = fs.createReadStream( fontFile );
      }
      await next();
    },
    // Bundle the worker scripts on request, see `bundles` above.
    async (context, next) => {
      const options = bundles[context.path];
      if (!options) {
        await next();
        return;
      }
      const result = await esbuild.build({
        ...options,
        bundle: true,
        format: "iife",
        target: "es2020",
        write: false,
      });
      context.type = "application/javascript";
      context.body = result.outputFiles[0].text;
    },
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
        // Load the .ts file of the entrypoint instead of .js to trigger esbuild
        // which will convert every .ts file to .js on request.
        context.url = context.url.replace("gr-app.mjs", "gr-app.ts");
      }

      // Pass control to the next middleware which eventually loads the file.
      // see https://koajs.com/#cascading
      await next();

      if (!isGrAppMjs && context.url.includes("gr-app.js")) {
        context.set('Content-Type', 'application/javascript; charset=utf-8');
        context.body = "import('./gr-app.mjs')";
      }
    },
  ],
};
