# Gerrit Code Review

[Gerrit](https://www.gerritcodereview.com) is a code review and project
management tool for Git based projects.

[![Build Status](https://gerrit-ci.gerritforge.com/job/Gerrit-master/badge/icon)](https://gerrit-ci.gerritforge.com/job/Gerrit-master/)

## Objective

Gerrit makes reviews easier by showing changes in a side-by-side display,
and allowing inline comments to be added by any reviewer.

Gerrit simplifies Git based project maintainership by permitting any
authorized user to submit changes to the master Git repository, rather
than requiring all approved changes to be merged in by hand by the project
maintainer.

## Documentation

For information about how to install and use Gerrit, refer to
[the documentation](https://gerrit-review.googlesource.com/Documentation/index.html).

## Source

Our canonical Git repository is located on [googlesource.com](https://gerrit.googlesource.com/gerrit).
There is a mirror of the repository on [Github](https://github.com/GerritCodeReview/gerrit).

## Reporting bugs

Please report bugs on the [issue tracker](https://bugs.chromium.org/p/gerrit/issues/list).

## Contribute

Gerrit is the work of hundreds of contributors. We appreciate your help!

Please read the [contribution guidelines](https://gerrit.googlesource.com/gerrit/+/master/SUBMITTING_PATCHES).

Note that we do not accept Pull Requests via the Github mirror.

## Getting in contact

The IRC channel on freenode is #gerrit. An archive is available at:
[echelog.com](https://echelog.com/logs/browse/gerrit).

The Developer Mailing list is [repo-discuss on Google Groups](https://groups.google.com/forum/#!forum/repo-discuss).

## License

Gerrit is provided under the Apache License 2.0.

## Build

Install [Bazel](https://bazel.build/versions/master/docs/install.html) and run the following:

        git clone --recurse-submodules https://gerrit.googlesource.com/gerrit
        cd gerrit && bazel build release

## Install binary packages (Deb/Rpm)

The instruction how to configure GerritForge/BinTray repositories is
[here](https://gitenterprise.me/2015/02/27/gerrit-2-10-rpm-and-debian-packages-available/)

On Debian/Ubuntu run:

        apt-get update & apt-get install gerrit=<version>-<release>

_NOTE: release is a counter that starts with 1 and indicates the number of packages that have
been released with the same version of the software._

On CentOS/RedHat run:

        yum clean all && yum install gerrit-<version>[-<release>]

On Fedora run:

        dnf clean all && dnf install gerrit-<version>[-<release>]

## Use pre-built Gerrit images on Docker

Docker images of Gerrit are available on [DockerHub](https://hub.docker.com/u/gerritforge/)

To run a CentOS 7 based Gerrit image:

        docker run -p 8080:8080 gerritforge/gerrit-centos7[:version]

To run a Ubuntu 15.04 based Gerrit image:

        docker run -p 8080:8080 gerritforge/gerrit-ubuntu15.04[:version]

_NOTE: release is optional. Last released package of the version is installed if the release
number is omitted._
