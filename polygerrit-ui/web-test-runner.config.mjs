import path from 'path';
import { esbuildPlugin } from '@web/dev-server-esbuild';
import { defaultReporter, summaryReporter } from '@web/test-runner';
import { visualRegressionPlugin } from '@web/test-runner-visual-regression/plugin';

function testRunnerHtmlFactory() {
  return (testFramework) => `
    <!DOCTYPE html>
    <html>
      <head>
        <link rel="stylesheet" href="polygerrit-ui/app/styles/main.css">
        <link rel="stylesheet" href="polygerrit-ui/app/styles/fonts.css">
        <link rel="stylesheet" href="polygerrit-ui/app/styles/material-icons.css">
      </head>
      <body>
        <script type="module" src="${testFramework}"></script>
      </body>
    </html>
  `;
}

const runUnderBazel = !!process.env['RUNFILES_DIR'];

function getModulesDir() {
  return runUnderBazel
    ? [
        path.join(process.cwd(), 'external/plugins_npm/node_modules'),
        path.join(process.cwd(), 'external/ui_npm/node_modules'),
        path.join(process.cwd(), 'external/ui_dev_npm/node_modules'),
      ]
    : [
        path.join(process.cwd(), 'plugins/node_modules'),
        path.join(process.cwd(), 'app/node_modules'),
        path.join(process.cwd(), 'node_modules'),
      ];
}

function getArgValue(flag) {
  const withEquals = process.argv.find((arg) => arg.startsWith(`${flag}=`));
  if (withEquals) return withEquals.split('=')[1];

  const index = process.argv.indexOf(flag);
  if (index !== -1 && process.argv[index + 1] && !process.argv[index + 1].startsWith('--')) {
    return process.argv[index + 1];
  }

  return undefined;
}

const pathPrefix = runUnderBazel ? 'polygerrit-ui/' : '';
const testFiles = getArgValue('--test-files') ?? `${pathPrefix}app/**/*_test.{ts,js}`;
const rootDir = getArgValue('--root-dir') ?? `${path.resolve(process.cwd())}/`;
const tsConfig = getArgValue('--ts-config');

/** @type {import('@web/test-runner').TestRunnerConfig} */
const config = {
  // TODO: https://g-issues.gerritcodereview.com/issues/365565157 - undo the
  // change once the underlying issue is fixed.
  concurrency: 1,

  files: [
    testFiles,
    `!${pathPrefix}**/node_modules/**/*`,
    ...(process.argv.includes('--run-screenshots')
      ? []
      : [`!${pathPrefix}app/**/*_screenshot_test.{ts,js}`]),
  ],

  port: 9876,

  nodeResolve: {
    moduleDirectories: getModulesDir(),
  },

  testFramework: {
    config: {
      ui: 'tdd',
      timeout: 5000,
    },
  },

  plugins: [
    esbuildPlugin({
      ts: true,
      target: 'es2020',
      tsconfig: tsConfig,
    }),
    visualRegressionPlugin({
      diffOptions: { threshold: 0.8 },
      update: process.argv.includes('--update-screenshots'),
    }),
  ],

  // serve from gerrit root directory so that we can serve fonts from
  // /lib/fonts/, see middleware.
  rootDir,

  reporters: [defaultReporter(), summaryReporter()],

  middleware: [
    // Fonts are in /lib/fonts/, but css tries to load from
    // /polygerrit-ui/app/fonts/. In production this works because our build
    // copies them over, see /polygerrit-ui/BUILD
    async (context, next) => {
      if (context.url.startsWith('/polygerrit-ui/app/fonts/')) {
        context.url = context.url.replace('/polygerrit-ui/app/', '/lib/');
      }
      await next();
    },
  ],

  testRunnerHtml: testRunnerHtmlFactory(),
};

export default config;
