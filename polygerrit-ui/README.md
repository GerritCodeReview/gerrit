# PolyGerrit

## Installing [Bazel](https://bazel.build/)

Follow the instructions
[here](https://gerrit-review.googlesource.com/Documentation/dev-bazel.html#_installation)
to get and install Bazel.

## Installing [Node.js](https://nodejs.org/en/download/) and npm packages

The minimum nodejs version supported is 6.x+

```sh
# Debian experimental
sudo apt-get install nodejs-legacy
sudo apt-get install npm

# OS X with Homebrew
brew install node
brew install npm
```

All other platforms: [download from
nodejs.org](https://nodejs.org/en/download/).

Various steps below require installing additional npm packages. The full list of
dependencies can be installed with:

```sh
sudo npm install -g \
  eslint \
  eslint-config-google \
  eslint-plugin-html \
  typescript \
  fried-twinkie \
  polylint \
  web-component-tester
```

It may complain about a missing `typescript@2.3.4` peer dependency, which is
harmless.

If you're interested in the details, keep reading.

## Local UI, Production Data

This is a quick and easy way to test your local changes against real data.
Unfortunately, you can't sign in, so testing certain features will require
you to use the "test data" technique described below.

### Installing [go](https://golang.org/)

This is required for running the `run-server.sh` script below.

```sh
# Debian/Ubuntu
sudo apt-get install golang

# OS X with Homebrew
brew install go
```

All other platforms: [download from golang.org](https://golang.org/)

Then add go to your path:

```
PATH=$PATH:/usr/local/go/bin
```

### Running the server

To test the local UI against gerrit-review.googlesource.com:

```sh
./run-server.sh
```

Then visit http://localhost:8081

## Local UI, Test Data

One-time setup:

1. [Build Gerrit](https://gerrit-review.googlesource.com/Documentation/dev-bazel.html#_gerrit_development_war_file)
2. Set up a local test site. Docs
   [here](https://gerrit-review.googlesource.com/Documentation/install-quick.html) and
   [here](https://gerrit-review.googlesource.com/Documentation/dev-readme.html#init).

When your project is set up and works using the classic UI, run a test server
that serves PolyGerrit:

```sh
bazel build polygerrit &&
  $(bazel info output_base)/external/local_jdk/bin/java \
  -jar bazel-bin/polygerrit.war daemon --polygerrit-dev \
  -d ../gerrit_testsite --console-log --show-stack-trace
```

Serving plugins

> Local dev plugins must be put inside of gerrit/plugins

Loading a single plugin file:

```sh
./run-server.sh --plugins=plugins/my_plugin/static/my_plugin.js
```

Loading multiple plugin files:

```sh
./run-server.sh --plugins=plugins/my_plugin/static/my_plugin.js,plugins/my_plugin/static/my_plugin.html
```

## Running Tests

This step requires the `web-component-tester` npm module.

Note: it may be necessary to add the options `--unsafe-perm=true --allow-root`
to the `npm install` command to avoid file permission errors.

Run all web tests:

```sh
./polygerrit-ui/app/run_test.sh
```

To allow the tests to run in Safari:

* In the Advanced preferences tab, check "Show Develop menu in menu bar".
* In the Develop menu, enable the "Allow Remote Automation" option.

If you need to pass additional arguments to `wct`:

```sh
WCT_ARGS='-p --some-flag="foo bar"' ./polygerrit-ui/app/run_test.sh
```

For interactively working on a single test file, do the following:

```sh
./polygerrit-ui/run-server.sh
```

Then visit http://localhost:8081/elements/foo/bar_test.html

To run Chrome tests in headless mode:

```sh
WCT_HEADLESS_MODE=1 ./polygerrit-ui/app/run_test.sh
```

Toolchain requirements for headless mode:

* Chrome: 59+
* web-component-tester: v6.5.0+

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
`eslint --ignore-pattern 'bower_components/' --ignore-pattern 'gr-linked-text' --ignore-pattern 'scripts/vendor' --ext .html,.js polygerrit-ui/app`
* To run ESLint on just the subdirectory you modified:
`eslint --ext .html,.js polygerrit-ui/app/$YOUR_DIR_HERE`
* To run the linter on all of your local changes:
`git diff --name-only master | xargs eslint --ext .html,.js`

We also use the `polylint` tool to lint use of Polymer. To install polylint,
execute the following command.

To run polylint, execute the following command.

```sh
bazel test //polygerrit-ui/app:polylint_test
```
## Template Type Safety
Polymer elements are not type checked against the element definition, making it trivial to break the display when refactoring or moving code. We now run additional tests to help ensure that template types are checked.

A few notes to ensure that these tests pass
- Any functions with optional parameters will need closure annotations.
- Any Polymer parameters that are nullable or can be multiple types (other than the one explicitly delared) will need type annotations.

These tests require the `typescript` and `fried-twinkie` npm packages.

To run on all files, execute the following command:

```sh
./polygerrit-ui/app/run_template_test.sh
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
