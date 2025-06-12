import path from 'path';
import { esbuildPlugin } from '@web/dev-server-esbuild';
import { defaultReporter, summaryReporter } from '@web/test-runner';
import { visualRegressionPlugin } from '@web/test-runner-visual-regression/plugin';
import { playwrightLauncher } from '@web/test-runner-playwright';

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
const testFiles = getArgValue('--test-files');
const runScreenshots = process.argv.includes('--run-screenshots');
const rootDir = getArgValue('--root-dir') ?? `${path.resolve(process.cwd())}/`;
const tsConfig = getArgValue('--ts-config') ?? `${pathPrefix}app/tsconfig.json`;

/** @type {import('@web/test-runner').TestRunnerConfig} */
const config = {
  // Default is CPU cores / 2. Use default
  // concurrency: 5,
  // WORKAROUND: Prevents tests from failing or timing out when run concurrently.
  // Recent Chrome versions aggressively throttle inactive tabs, which interferes with
  // parallel tests. These flags disable that behavior, ensuring tests that rely on
  // visibility or timers run reliably.
  // Some details: https://g-issues.gerritcodereview.com/issues/365565157
  browsers: [
    playwrightLauncher({
      product: 'chromium',
      launchOptions: {
        args: [
          '--disable-background-timer-throttling',
          '--disable-backgrounding-occluded-windows',
          '--disable-renderer-backgrounding',
        ],
      },
    }),
  ],

  files: runScreenshots
      ? [
          // If --run-screenshots is set, ONLY run screenshot tests.
          testFiles ?? `${pathPrefix}app/**/*_screenshot_test.{ts,js}`,
          `!${pathPrefix}**/node_modules/**/*`,
        ]
      : [
          // Otherwise, run all tests EXCEPT screenshot tests
          testFiles ?? `${pathPrefix}app/**/*_test.{ts,js}`,
          `!${pathPrefix}**/node_modules/**/*`,
          `!${pathPrefix}app/**/*_screenshot_test.{ts,js}`,
        ],

  port: 9876,

  nodeResolve: {
    modulePaths: getModulesDir(),
  },

  testFramework: {
    config: {
      ui: 'tdd',
      timeout: 2000,
    },
  },

  coverageConfig: {
    report: true,
    reportDir: 'coverage',
    reporters: ['lcov', 'text'],
  },

  plugins: [
    esbuildPlugin({
      ts: true,
      target: 'es2020',
      tsconfig: tsConfig,
    }),
    visualRegressionPlugin({
      diffOptions: { threshold: 0.8 },
      failureThreshold: 1,
      failureThresholdType: 'percent',
      update: process.argv.includes('--update-screenshots'),
    }),
  ],

  // serve from gerrit root directory so that we can serve fonts from
  // /lib/fonts/ for screenshots tests, see middleware.
  rootDir: runScreenshots ? '..' : rootDir,

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
