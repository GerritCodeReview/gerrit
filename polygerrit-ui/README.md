# PolyGerrit

## Installing [Node.js](https://nodejs.org/en/download/)

The minimum nodejs version supported is 6.x+

```sh
# Debian experimental
sudo apt-get install nodejs-legacy

# OS X with Homebrew
brew install node
```

All other platforms: [download from
nodejs.org](https://nodejs.org/en/download/).

## Installing [Bazel](https://bazel.build/)

Follow the instructions
[here](https://gerrit-review.googlesource.com/Documentation/dev-bazel.html#_installation)
to get and install Bazel.

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
bazel build gerrit &&
  $(bazel info output_base)/external/local_jdk/bin/java \
  -jar bazel-bin/gerrit.war daemon --polygerrit-dev \
  -d ../gerrit_testsite --console-log --show-stack-trace
```

## Running Tests

One-time setup:

```sh
# Debian/Ubuntu
sudo apt-get install npm

# OS X with Homebrew
brew install npm

# All platforms (including those above)
sudo npm install -g web-component-tester
```

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

## Style guide

We follow the [Google JavaScript Style Guide](https://google.github.io/styleguide/javascriptguide.xml)
with a few exceptions. When in doubt, remain consistent with the code around you.

In addition, we encourage the use of [ESLint](http://eslint.org/).
It is available as a command line utility, as well as a plugin for most editors
and IDEs. It, along with a few dependencies, can also be installed through NPM:

```sh
sudo npm install -g eslint eslint-config-google eslint-plugin-html
```

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

We also use the polylint tool to lint use of Polymer. To install polylint,
execute the following command.

```sh
npm install -g polylint
```

To run polylint, execute the following command.

```sh
bazel test //polygerrit-ui/app:polylint_test
```

