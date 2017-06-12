# Gerrit Code Review

[Gerrit](https://www.gerritcodereview.com) is a code review and project
management tool for Git based projects.

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
There is a mirror of the repository on [Github](https://github.com/gerrit-review/gerrit).

## Reporting bugs

Please report bugs on the [issue tracker](https://bugs.chromium.org/p/gerrit/issues/list).

## Contribute

Gerrit is the work of hundreds of contributors. We appreciate your help!

Please read the [contribution guidelines](https://gerrit.googlesource.com/gerrit/+/master/SUBMITTING_PATCHES).

Note that we do not accept Pull Requests via the Github mirror.

## Getting in contact

The IRC channel on freenode is #gerrit. An archive is available at:
[echelog.com](http://echelog.com/logs/browse/gerrit).

The Developer Mailing list is [repo-discuss on Google Groups](https://groups.google.com/forum/#!forum/repo-discuss).

## License

Gerrit is provided under the Apache License 2.0.

## Build

Install [Buck](http://facebook.github.io/buck/setup/install.html) and run the following:

        git clone --recursive https://gerrit.googlesource.com/gerrit
        cd gerrit && buck build release

## Install binary packages (Deb/Rpm)

The instruction how to configure GerritForge/BinTray repositories is
[here](http://gitenterprise.me/2015/02/27/gerrit-2-10-rpm-and-debian-packages-available)

On Debian/Ubuntu run:

        apt-get update & apt-get install gerrit=<version>-<release>

_NOTE: release is a counter that starts with 1 and indicates the number of packages that have
been released with the same version of the software._

On CentOS/RedHat run:

        yum clean all && yum install gerrit-<version>[-<release>]

_NOTE: release is optional. Last released package of the version is installed if the release
number is omitted._

