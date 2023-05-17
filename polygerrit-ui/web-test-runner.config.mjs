import { esbuildPlugin } from "@web/dev-server-esbuild";
import { defaultReporter, summaryReporter } from "@web/test-runner";
import { visualRegressionPlugin } from "@web/test-runner-visual-regression/plugin";

function testRunnerHtmlFactory(options) {
  const setNewDiffExp = `<script type="text/javascript">window.ENABLED_EXPERIMENTS = ['UiFeature__new_diff'];</script>`;
  return (testFramework) => `
    <!DOCTYPE html>
    <html>
      <head>
        <link rel="stylesheet" href="polygerrit-ui/app/styles/main.css">
        <link rel="stylesheet" href="polygerrit-ui/app/styles/fonts.css">
        <link
          rel="stylesheet"
          href="polygerrit-ui/app/styles/material-icons.css">
      </head>
      <body>
        ${options.newDiff ? setNewDiffExp : ''}
        <script type="module" src="${testFramework}"></script>
      </body>
    </html>
  `;
}

/** @type {import('@web/test-runner').TestRunnerConfig} */
const config = {
  files: [
    "app/**/*_test.{ts,js}",
    "!app/embed/diff/gr-context-controls/**/*_test.{ts,js}",
    "!app/embed/diff/gr-diff/**/*_test.{ts,js}",
    "!app/embed/diff/gr-diff-builder/**/*_test.{ts,js}",
    "!app/embed/diff/gr-diff-cursor/**/*_test.{ts,js}",
    "!app/embed/diff/gr-diff-highlight/**/*_test.{ts,js}",
    "!app/embed/diff/gr-diff-model/**/*_test.{ts,js}",
    "!app/embed/diff/gr-diff-processor/**/*_test.{ts,js}",
    "!app/embed/diff/gr-diff-selection/**/*_test.{ts,js}",
    "!**/node_modules/**/*",
    ...(process.argv.includes("--run-screenshots")
      ? []
      : ["!app/**/*_screenshot_test.{ts,js}"]),
  ],
  // TODO(newdiff-cleanup): Remove once newdiff migration is completed.
  groups: [
    {
      name: "new-diff",
      files: [
        "app/embed/diff/**/*_test.{ts,js}",
        "app/elements/change/gr-file-list/gr-file-list_test.{ts,js}",
        "app/elements/diff/gr-apply-fix-dialog/gr-apply-fix-dialog_test.{ts,js}",
        "app/elements/diff/gr-diff-host/gr-diff-host_test.{ts,js}",
        "app/elements/diff/gr-diff-view/gr-diff-view_test.{ts,js}",
        "app/elements/shared/gr-comment-thread/gr-comment-thread_test.{ts,js}",
      ],
      testRunnerHtml: testRunnerHtmlFactory({newDiff: true}),
    },
  ],
  port: 9876,
  nodeResolve: true,
  testFramework: { config: { ui: "tdd", timeout: 5000 } },
  plugins: [
    esbuildPlugin({
      ts: true,
      target: "es2020",
      tsconfig: "app/tsconfig.json",
    }),
    visualRegressionPlugin({
      diffOptions: {
        threshold: 0.8,
      },
      update: process.argv.includes("--update-screenshots"),
    }),
  ],
  // serve from gerrit root directory so that we can serve fonts from
  // /lib/fonts/, see middleware.
  rootDir: "..",
  reporters: [defaultReporter(), summaryReporter()],
  middleware: [
    // Fonts are in /lib/fonts/, but css tries to load from
    // /polygerrit-ui/app/fonts/. In production this works because our build
    // copies them over, see /polygerrit-ui/BUILD
    async (context, next) => {
      if (context.url.startsWith("/polygerrit-ui/app/fonts/")) {
        context.url = context.url.replace("/polygerrit-ui/app/", "/lib/");
      }
      await next();
    },
  ],
  testRunnerHtml: testRunnerHtmlFactory({newDiff: false}),
};
export default config;
