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

For documentation about how to install and use Gerrit, visit
https://gerrit-review.googlesource.com/Documentation/index.html.

## Source

Our canonical Git repository is located at https://gerrit.googlesource.com/gerrit.
There is a mirror of the repository at https://github.com/gerrit-review/gerrit.

## Reporting issues

Please report issues here: https://code.google.com/p/gerrit/issues/list.

## Contribute

Gerrit is the work of hundreds of contributors. We appreciate your help!
Read the contribution guidelines:
https://gerrit.googlesource.com/gerrit/+/master/SUBMITTING_PATCHES.
Please note, that we do not accept Pull Requests.

## Getting in contact

IRC chanel on freenode is #gerrit. Archive is under:
http://echelog.com/logs/browse/gerrit. Developer Mailing list is:
https://groups.google.com/forum/#!forum/repo-discuss.

## License

Gerrit is provided under the Apache License 2.0.

## Build

Install [Buck](http://facebook.github.io/buck/setup/install.html) and run the following:

        git clone --recursive https://gerrit.googlesource.com/gerrit
        cd gerrit && buck build all

## Install binary packages (Deb/Rpm)

The instruction how to configure GerritForge/BinTray repositories is
[here](http://gitenterprise.me/2015/02/27/gerrit-2-10-rpm-and-debian-packages-available)

On Debian/Ubuntu run:

        apt-get update & apt-get install gerrit=<version>

On CentOS/RedHat run:

        yum clean all && yum install gerrit-<version>

## Events

Next developer conference is September 2015, Berlin.
Next user conference is November 2015, MV, CA. Registration form is
[here](http://goo.gl/forms/fifi2YQTc7).
