# PolyGerrit

## Installing [Node.js](https://nodejs.org/en/download/)

```sh
# Debian/Ubuntu
sudo apt-get install nodejs-legacy

# OS X with Homebrew
brew install node
```

All other platforms: [download from
nodejs.org](https://nodejs.org/en/download/).

## Local UI, Production Data

To test the local UI against gerrit-review.googlesource.com:

```sh
./run-server.sh
```

Then visit http://localhost:8081

## Local UI, Test Data

One-time setup:

1. [Install Buck](https://gerrit-review.googlesource.com/Documentation/dev-buck.html#_installation)
   for building Gerrit.
2. [Build Gerrit](https://gerrit-review.googlesource.com/Documentation/dev-buck.html#_gerrit_development_war_file)
   and set up a [local test site](https://gerrit-review.googlesource.com/Documentation/dev-readme.html#init).

Run a test server:

```sh
buck build polygerrit && \
java -jar buck-out/gen/polygerrit/polygerrit.war daemon --polygerrit-dev -d ../gerrit_testsite --console-log --show-stack-trace
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
buck test --include web
```

If you need to pass additional arguments to `wct`:

```sh
WCT_ARGS='-p --some-flag="foo bar"' buck test --no-results-cache --include web
```
