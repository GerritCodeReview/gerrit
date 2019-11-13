# Gerrit Polymer Frontend

Follow the
[setup instructions for Gerrit backend developers](https://gerrit-review.googlesource.com/Documentation/dev-readme.html)
where applicable.

## Installing [Bazel](https://bazel.build/)

Follow the instructions
[here](https://gerrit-review.googlesource.com/Documentation/dev-bazel.html#_installation)
to get and install Bazel.

## Installing [Node.js](https://nodejs.org/en/download/) and npm packages

The minimum nodejs version supported is 8.x+

```sh
# Debian experimental
sudo apt-get install nodejs-legacy
sudo apt-get install npm

# OS X with Homebrew
brew install node
brew install npm
```

All other platforms:
[download from nodejs.org](https://nodejs.org/en/download/).

Various steps below require installing additional npm packages. The full list of
dependencies can be installed with:

```sh
npm install
```

It may complain about a missing `typescript@2.3.4` peer dependency, which is
harmless.

## Running locally against production data

#### Go server

To test the local Polymer frontend against gerrit-review.googlesource.com
simply execute:

```sh
./polygerrit-ui/run-server.sh
```

Then visit <http://localhost:8081>.

This method is based on a
[simple hand-written Go webserver](https://gerrit.googlesource.com/gerrit/+/master/polygerrit-ui/server.go).
Mostly it just switches between serving files locally and proxying the real
server based on the file name. It also does some basic response rewriting, e.g.
it patches the `config/server/info` response with plugin information provided on
the command line:

```sh
./polygerrit-ui/run-server.sh --plugins=plugins/my_plugin/static/my_plugin.js,plugins/my_plugin/static/my_plugin.html
```

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
[this command](https://gerrit-review.googlesource.com/Documentation/dev-readme.html#run_daemon),
and add the `--polygerrit-dev` option, if you want to serve the Polymer frontend
directly from the sources in `polygerrit_ui/app/` instead of from the war:

```sh
$(bazel info output_base)/external/local_jdk/bin/java \
    -DsourceRoot=$(bazel info workspace) \
    -jar bazel-bin/gerrit.war daemon \
    -d $GERRIT_SITE \
    --console-log \
    --polygerrit-dev
```

## Running Tests

This step requires the `web-component-tester` npm module.

Note: it may be necessary to add the options `--unsafe-perm=true --allow-root`
to the `npm install` command to avoid file permission errors.

For daily development you typically only want to run and debug individual tests.
Run the local [Go proxy server](#go-server) and navigate for example to
<http://localhost:8081/elements/change/gr-account-entry/gr-account-entry_test.html>.
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
git diff --name-only master | xargs node_modules/eslint/bin/eslint.js --ext .html,.js
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
