# PolyGerrit

## Local UI, Production Data

To test the local UI against gerrit-review.googlesource.com:

```sh
cd polygerrit-ui
npm install
bower install
go run server.go
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

```sh
npm install -g web-component-tester
wct
```
