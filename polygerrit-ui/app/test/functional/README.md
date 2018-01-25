# Functional test suite

## Building a Docker image

Should be done once only for development purposes.

```
~/gerrit $ docker build -t gerrit/polygerrit-functional:v1 \
  polygerrit-ui/app/test/functional
```

## Running a smoke test

Running a smoke test from gerrit checkout path:

```
~/gerrit $ ./polygerrit-ui/app/test/functional/run_functional.sh
```
