# Functional test suite

## Building a Docker image

Should be done once only for development purposes.

```
cd polygerrit-ui/app/test/functional
docker build -t gerrit/polygerrit-functional:v1 .
```

## Running a Docker image

Running a sample `test.js` from gerrit checkout path:

```
docker run --rm -v `pwd`/polygerrit-ui/app/test/functional:/tests \
  -it gerrit/polygerrit-functional:v1 /tests/test.js
```
