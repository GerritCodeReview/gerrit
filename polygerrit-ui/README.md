# Gerrit Polymer Frontend

Follow the
[setup instructions for Gerrit backend developers](https://gerrit-review.googlesource.com/Documentation/dev-readme.html)
where applicable, the most important command is:

```
git clone --recurse-submodules https://gerrit.googlesource.com/gerrit
```

The --recurse-submodules option is needed on git clone to ensure that the core plugins, which are included as git submodules, are also cloned.

## Installing [Bazel](https://bazel.build/)

Follow the instructions
[here](https://gerrit-review.googlesource.com/Documentation/dev-bazel.html#_installation)
to get and install Bazel.

## Installing [Node.js](https://nodejs.org/en/download/) and npm packages

**Note**: Switch between an old branch with bower_components and a new branch with ui-npm
packages (or vice versa) can lead to some build errors. To avoid such errors clean up the build
repository:
```sh
rm -rf node_modules/ \
    polygerrit-ui/node_modules/ \
    polygerrit-ui/app/node_modules \
    tools/node_tools/node_modules

bazel clean
```

If it doesn't help also try to run
```sh
bazel clean --expunge
```

The minimum nodejs version supported is 8.x+

```sh
# Debian experimental
sudo apt-get install nodejs
sudo apt-get install npm

# OS X with Homebrew
brew install node
brew install npm
```

All other platforms:
[download from nodejs.org](https://nodejs.org/en/download/).

or use [nvm - Node Version Manager](https://github.com/nvm-sh/nvm).

### Additional packages

We have several bazel commands to install packages we may need for FE developmemt.

For first time users to get the local server up, `npm start` should be enough and will take care of all of them for you.

```sh
# Install packages from root-level packages.json
bazel fetch @npm//:node_modules

# Install packages from polygerrit-ui/app/packages.json
bazel fetch @ui_npm//:node_modules

# Install packages from polygerrit-ui/packages.json
bazel fetch @ui_dev_npm//:node_modules

# Install packages from tools/node_tools/packages.json
bazel fetch @tools_npm//:node_modules
```

More information for installing and using nodejs rules can be found here https://bazelbuild.github.io/rules_nodejs/install.html

## Serving files locally

#### Go server

To test the local Polymer frontend against production data or a local test site execute:

```sh
./polygerrit-ui/run-server.sh

// or
npm run start
```

These commands start the [simple hand-written Go webserver](https://gerrit.googlesource.com/gerrit/+/master/polygerrit-ui/server.go).
Mostly it just switches between serving files locally and proxying the real
server based on the file name. It also does some basic response rewriting, e.g.
it patches the `config/server/info` response with plugin information provided on
the command line:

```sh
./polygerrit-ui/run-server.sh --plugins=plugins/my_plugin/static/my_plugin.js,plugins/my_plugin/static/my_plugin.html
```

If any issues occured, please refer to the Troubleshooting section at the bottom or contact the team!

## Running locally against production data

### Local website

Start [Go server](#go-server) and then visit http://localhost:8081

The biggest draw back of this method is that you cannot log in, so cannot test
scenarios that require it.

#### Chrome extension: Gerrit FE Dev Helper

To be able to bypass the auth and also help improve the productivity of Gerrit FE developers,
we created this chrome extension: [Gerrit FE Dev Helper](https://chrome.google.com/webstore/detail/gerrit-fe-dev-helper/jimgomcnodkialnpmienbomamgomglkd).

It basically works as a proxy that will block / redirect requests from current sites to any given url base on certain rules.

The source code is in [Gerrit - gerrit-fe-dev-helper](https://gerrit-review.googlesource.com/q/project:gerrit-fe-dev-helper), contributions are welcomed!

To use this extension, just follow its [readme here](https://gerrit.googlesource.com/gerrit-fe-dev-helper/+/master/README.md).

## Running locally against a Gerrit test site

Set up a local test site once:

1. [Build Gerrit](https://gerrit-review.googlesource.com/Documentation/dev-bazel.html#_gerrit_development_war_file)
2. [Set up a local test site](https://gerrit-review.googlesource.com/Documentation/dev-readme.html#init).
3. Optionally [populate](https://gerrit.googlesource.com/gerrit/+/master/contrib/populate-fixture-data.py) your test site with some test data.

For running a locally built Gerrit war against your test instance use
[this command](https://gerrit-review.googlesource.com/Documentation/dev-readme.html#run_daemon).

If you want to serve the Polymer frontend directly from the sources in `polygerrit_ui/app/` instead of from the war:
1. Start [Go server](#go-server)
2. Add the `--dev-cdn` option:

```sh
$(bazel info output_base)/external/local_jdk/bin/java \
    -DsourceRoot=$(bazel info workspace) \
    -jar bazel-bin/gerrit.war daemon \
    -d $GERRIT_SITE \
    --console-log \
    --dev-cdn http://localhost:8081
```

*NOTE* You can use any other cdn here, for example: https://cdn.googlesource.com/polygerrit_ui/678.0

## Running Tests

For daily development you typically only want to run and debug individual tests.
Run the local [Go proxy server](#go-server) and navigate for example to
<http://localhost:8081/elements/shared/gr-account-entry/gr-account-entry_test.html>.
Check "Disable cache" in the "Network" tab of Chrome's dev tools, so code
changes are picked up on "reload".

Our CI integration ensures that all tests are run when you upload a change to
Gerrit, but you can also run all tests locally in headless mode:

```sh
npm test
```

To allow the tests to run in Safari:

* In the Advanced preferences tab, check "Show Develop menu in menu bar".
* In the Develop menu, enable the "Allow Remote Automation" option.

To run Chrome tests in headless mode:

```sh
WCT_HEADLESS_MODE=1 WCT_ARGS='--verbose -l chrome' ./polygerrit-ui/app/run_test.sh
```

## Style guide

We follow the [Google JavaScript Style Guide](https://google.github.io/styleguide/javascriptguide.xml)
with a few exceptions. When in doubt, remain consistent with the code around you.

In addition, we encourage the use of [ESLint](http://eslint.org/).
It is available as a command line utility, as well as a plugin for most editors
and IDEs.

`eslint-config-google` is a port of the Google JS Style Guide to an ESLint
config module, and `eslint-plugin-html` allows ESLint to lint scripts inside
HTML.
We have an .eslintrc.json config file in the polygerrit-ui/ directory configured
to enforce the preferred style of the PolyGerrit project.
After installing, you can use `eslint` on any new file you create.
In addition, you can supply the `--fix` flag to apply some suggested fixes for
simple style issues.
If you modify JS inside of `<script>` tags, like for test suites, you may have
to supply the `--ext .html` flag.

Some useful commands:

* To run ESLint on the whole app, less some dependency code:

```sh
npm run eslint
```

* To run ESLint on just the subdirectory you modified:

```sh
node_modules/eslint/bin/eslint.js --ext .html,.js polygerrit-ui/app/$YOUR_DIR_HERE
```

* To run the linter on all of your local changes:

```sh
git diff --name-only HEAD | xargs node_modules/eslint/bin/eslint.js --ext .html,.js
```

We also use the `polylint` tool to lint use of Polymer. To install polylint,
execute the following command.

To run polylint, execute the following command.

```sh
bazel test //polygerrit-ui/app:polylint_test
```

or

```sh
npm run polylint
```

## Template Type Safety

> **Warning**: This feature is temporary disabled, because it doesn't work with Polymer 2 and Polymer 3. Some of the checks are made by polymer linter.

Polymer elements are not type checked against the element definition, making it
trivial to break the display when refactoring or moving code. We now run
additional tests to help ensure that template types are checked.

A few notes to ensure that these tests pass
- Any functions with optional parameters will need closure annotations.
- Any Polymer parameters that are nullable or can be multiple types (other than
  the one explicitly delared) will need type annotations.

These tests require the `typescript` and `fried-twinkie` npm packages.

To run on all files, execute the following command:

```sh
./polygerrit-ui/app/run_template_test.sh
```

or

```sh
npm run test-template
```

To run on a specific top level directory (ex: change-list)
```sh
TEMPLATE_NO_DEFAULT=true ./polygerrit-ui/app/run_template_test.sh //polygerrit-ui/app:template_test_change-list
```

To run on a specific file (ex: gr-change-list-view), execute the following command:
```sh
TEMPLATE_NO_DEFAULT=true ./polygerrit-ui/app/run_template_test.sh //polygerrit-ui/app:template_test_<TOP_LEVEL_DIRECTORY> --test_arg=<VIEW_NAME>
```

```sh
TEMPLATE_NO_DEFAULT=true ./polygerrit-ui/app/run_template_test.sh //polygerrit-ui/app:template_test_change-list --test_arg=gr-change-list-view
```

## Contributing

Our users report bugs / feature requests related to the UI through [Monorail Issues - PolyGerrit](https://bugs.chromium.org/p/gerrit/issues/list?q=component%3APolyGerrit).

If you want to help, feel free to grab one from those `New` issues without
assignees and send us a change.

If you don't know who to assign to review your code change, you can use
this special account: `gerrit-fe-reviewers@api-project-164060093628.iam.gserviceaccount.com`
and just assign to that account, it will automatically pick two volunteers
from the queue we have for FE reviewers.

If you are willing to join the queue and help the community review changes,
you can create an issue through Monorail and request to join the queue!
We will review your request and start from there.

## Troubleshotting & Frequently asked questions

1. Local host is blank page and console shows missing files from `polymer-bridges`

Its likely you missed the `polymer-bridges` submodule when you clone the `gerrit` repo.

To fix that, run:
```
// fetch the submodule
git submodule update --init --recursive

// reset the workspace (please save your local changes before running this command)
npm run clean

// install all dependencies and start the server
npm start
```