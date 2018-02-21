# Prepopulated Gerrit Docker image

## Building a Docker image

```
docker build -t gerrit/polygerrit-prepopulated:v1 \
  polygerrit-ui/app/test/functional/prepopulate
```

## Running the Docker image

```
docker run --rm -it -p 8080:8080 -p 29418:29418 \
  gerrit/polygerrit-prepopulated:v1
```

## Prepopulating the image

FIXME: Add more content here

Use following to create and update the sample content:

https://gerrit-review.googlesource.com/Documentation/cmd-gsql.html
