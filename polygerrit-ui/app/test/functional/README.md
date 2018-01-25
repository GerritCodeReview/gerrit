# Functional test suite

## Installing Docker (OSX)

Basically, following are required:

- Docker-Machine
- VirtualBox
- Docker engine

Simplest way to install all of those is to use Homebrew:

```
$ brew cask install docker
```

This will install a Docker in Applications. To run if from the command-line:

```
$ open /Applications/Docker.app
```

It'll require privileged access and will require user password to be entered.

To validate Docker is installed correctly, run hello-world image:

```
$ docker run hello-world
```

## Building a Docker image

Should be done once only for development purposes.

```
~/gerrit $ docker build -t gerrit/polygerrit-functional:v1 \
  polygerrit-ui/app/test/functional
```

## Running a smoke test

Running a smoke test from Gerrit checkout path:

```
~/gerrit $ ./polygerrit-ui/app/test/functional/run_functional.sh
```

The successful output should be something similar to this:

```
Starting local server..
Starting Webdriver..
Started
.


1 spec, 0 failures
Finished in 2.565 seconds
```
