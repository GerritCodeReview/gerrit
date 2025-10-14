# Gerrit Lit Frontend

Follow the
[setup instructions for Gerrit backend developers](https://gerrit-review.googlesource.com/Documentation/dev-readme.html)
where applicable. The most important command is:

```sh
git clone --recurse-submodules https://gerrit.googlesource.com/gerrit
```

The `--recurse-submodules` option is needed on `git clone` to ensure that the
core plugins, which are included as git submodules, are also cloned.

Then make sure to install the commit hook that will set up the `Change-Id` for
each push to Gerrit:

```sh
cd gerrit && (
  cd .git/hooks
  ln -s ../../resources/com/google/gerrit/server/tools/root/hooks/commit-msg
)
```

## Installing [Bazel](https://bazel.build/)

Follow the instructions
[here](https://gerrit-review.googlesource.com/Documentation/dev-bazel.html#_installation)
to get and install Bazel. Using Bazelisk is usually the easiest option.

## Installing [Node.js](https://nodejs.org/en/download/) yarn and pnpm

Use a recent Node.js version that is supported by the repository. If in doubt,
use the version that is used in CI or documented in the root `package.json`.

Examples:

```sh
# Debian / Ubuntu
sudo apt-get install nodejs npm

# macOS with Homebrew
brew install node
```

All other platforms:
[download from nodejs.org](https://nodejs.org/en/download/),

or use [nvm - Node Version Manager](https://github.com/nvm-sh/nvm).

Install Yarn:

```sh
npm install -g yarn
```

Install pnpm:

```sh
npm install -g pnpm
```

## Dependency Management (Yarn → pnpm Transition)

- yarn.lock is authoritative
- pnpm-lock.yaml is generated via `pnpm import`
- Do NOT edit pnpm-lock.yaml manually

## Expected Bazel Behavior

First run may fail with:

pnpm-lock.yaml file updated. Please run your build again.

Rerun the same command.

## Workflow

1. Edit package.json
2. Run: yarn install
3. Run: bazel build gerrit
4. If lock updated → rerun build

## Why Yarn stays

- gradual migration from `yarn` to `pnpm`
- stable dependency resolution
- avoids breaking workflows

## DO NOT RUN

```sh
pnpm install
```

It creates a different dependency graph.

## Long-term

Eventually:
- pnpm-lock.yaml becomes canonical
- yarn.lock removed

## Setup TypeScript support in the IDE

Modern IDEs should automatically handle TypeScript settings from
`polygerrit-ui/app/tsconfig.json`. The `tsc` compiler places compiled files in
the `.ts-out/pg` directory at the root of the Gerrit workspace, and you can
configure the IDE to exclude the whole `.ts-out` directory. In IntelliJ IDEA,
right-click the directory and select **Mark Directory As > Excluded**.

If your IDE still reports errors, then you can try configuring TypeScript
manually. For example, if IntelliJ IDEA shows
`Cannot find parent 'tsconfig.json'`, then set the TypeScript options to:

```sh
--project polygerrit-ui/app/tsconfig.json
```

## Developing locally

The preferred method for development is to serve the web files locally using the
Web Dev Server and then view a running Gerrit instance (local or otherwise) and
replace its web client with the local one using the Gerrit FE Dev Helper
extension.

### Web Dev Server

The [Web Dev Server](https://modern-web.dev/docs/dev-server/overview/) serves
the compiled web files and dependencies unbundled over localhost. Start it with:

```sh
pnpm start
```

To inject plugins or other files, we use the
[Gerrit FE Dev Helper](https://chrome.google.com/webstore/detail/gerrit-fe-dev-helper/jimgomcnodkialnpmienbomamgomglkd)
Chrome extension.

If you run into problems, refer to the troubleshooting section below or contact
the team.

### Chrome extension: Gerrit FE Dev Helper

To help frontend development, including bypassing auth in local dev workflows,
we created this Chrome extension:
[Gerrit FE Dev Helper](https://chrome.google.com/webstore/detail/gerrit-fe-dev-helper/jimgomcnodkialnpmienbomamgomglkd).

It basically works as a proxy that blocks or redirects requests from the current
site to a configured URL according to rules.

The source code is in
[Gerrit - gerrit-fe-dev-helper](https://gerrit-review.googlesource.com/q/project:gerrit-fe-dev-helper),
and contributions are welcome.

To use this extension, follow its
[README](https://gerrit.googlesource.com/gerrit-fe-dev-helper/+/master/README.md).

### Running locally against a Gerrit test site

Set up a local test site once:

1. [Build Gerrit](https://gerrit-review.googlesource.com/Documentation/dev-bazel.html#_gerrit_development_war_file)
2. [Set up a local test site](https://gerrit-review.googlesource.com/Documentation/dev-readme.html#init)
3. Optionally [populate](https://gerrit.googlesource.com/gerrit/+/master/contrib/populate-fixture-data.py)
   your test site with test data

For running a locally built Gerrit war against your test instance use
[this command](https://gerrit-review.googlesource.com/Documentation/dev-readme.html#run_daemon).

If you want to serve the Lit frontend directly from the sources in
`polygerrit-ui/app/` instead of from the war:

1. Start the [Web Dev Server](#web-dev-server)
2. Add the `--dev-cdn` option:

```sh
$(bazel info output_base)/external/local_jdk/bin/java \
    -DsourceRoot=$(bazel info workspace) \
    -jar bazel-bin/gerrit.war daemon \
    -d $GERRIT_SITE \
    --console-log \
    --dev-cdn http://localhost:8081
```

The Web Dev Server currently does not serve fonts or some other static assets.
Follow
[Issue 40015119](https://issues.gerritcodereview.com/issues/40015119)
for updates.

*Note:* You can also use another CDN here, for example:
https://cdn.googlesource.com/polygerrit_ui/678.0

## Running tests

For daily development you typically only want to run and debug individual tests.
Our tests run using the
[Web Test Runner](https://modern-web.dev/docs/test-runner/overview/).

Common commands:

* Run all tests once:

```sh
pnpm test
```

* Run all tests in watch mode. Changing a file reruns affected tests:

```sh
pnpm test:watch
```

* Run all tests once under Bazel:

```sh
./polygerrit-ui/app/run_test.sh
```

* Run a single test file and rerun when affected files change:

```sh
pnpm test:single "**/gr-comment_test.ts"
```

### Screenshot tests

We use screenshot tests to prevent unintended visual regressions.

To run the screenshot tests:

```sh
pnpm test:screenshot
```

Or via Bazel, which matches what CI runs and uses the Bazel-managed
dependency tree:
```sh
bazelisk test //polygerrit-ui:web_test_runner_screenshots
```

The `//polygerrit-ui:web_test_runner_all` target runs both the unit and
screenshot buckets in a single invocation. Both screenshot targets are
tagged `manual`, so they are never picked up by a bare `bazel test //...`
and must be named explicitly.

If a test fails, then the component's appearance has changed. New screenshots
are generated in the `polygerrit-ui/screenshots/Chromium/failed/` directory.
If an existing baseline differs, then a diff image is also created there.

If the change is intended, then approve the new screenshots as the baseline by
moving the new screenshot files from `failed` to `baseline`, overwriting the old
ones. The diff images in `failed` can be deleted.

```sh
mv polygerrit-ui/screenshots/Chromium/failed/*.png \
  polygerrit-ui/screenshots/Chromium/baseline/
```

After moving the file(s), run `pnpm test:screenshot` again to confirm they pass.

### Compiling code

```sh
# Compile frontend once to check for type errors
pnpm compile

# Watch mode
pnpm compile:watch
```

## Style guide

We follow the
[Google JavaScript Style Guide](https://google.github.io/styleguide/javascriptguide.xml)
with a few exceptions. When in doubt, remain consistent with the surrounding
code.

In addition, we encourage the use of [ESLint](http://eslint.org/). It is
available as a command-line utility as well as a plugin for most editors and
IDEs.

We have an ESLint flat config in `polygerrit-ui/app/eslint-bazel.config.js`
configured to enforce the preferred style of the PolyGerrit project.

Some useful commands:

* Run ESLint on the whole app:

```sh
pnpm eslint
```

* Run ESLint and apply automatic fixes:

```sh
pnpm eslintfix
```

* Run ESLint on a specific subdirectory:

```sh
pnpm exec eslint --config polygerrit-ui/app/eslint-bazel.config.js \
  --ext .html,.js,.ts polygerrit-ui/app/$YOUR_DIR_HERE
```

* Run ESLint on all locally changed frontend files:

```sh
git diff --name-only HEAD | xargs pnpm exec eslint \
  --config polygerrit-ui/app/eslint-bazel.config.js \
  --ext .html,.js,.ts
```

When running ESLint manually, invoke it from the repository root so that
repo-root-relative paths in the flat config resolve correctly.

## Contributing

Our users report bugs and feature requests related to the UI through the Gerrit
Tracker in the
[WebFrontend](https://issues.gerritcodereview.com/issues?q=componentid:1369968)
component.

If you want to help, feel free to pick one of the `New` issues without assignees
and send us a change.

If you don't know who to assign as reviewer for your change, then you can use
this special account:

`gerrit-fe-reviewers@api-project-164060093628.iam.gserviceaccount.com`

Assigning that account automatically picks two volunteers from the frontend
reviewer queue.

If you are willing to join that queue and help review community changes, then
create an issue through Gerrit issue tracker and ask to join.
