---
title: " Making a Snapshot Release of JGit"
sidebar: gerritdoc_sidebar
permalink: dev-release-jgit.html
---
This step is only necessary if we need to create an unofficial JGit
snapshot release and publish it to the [Google Cloud
Storage](https://developers.google.com/storage/).

## Prepare the Maven Environment

First, make sure you have done the necessary [configuration in Maven
`settings.xml`](dev-release-deploy-config.html#deploy-configuration-settings-xml).

To apply the necessary settings in JGit’s `pom.xml`, follow the
instructions in [Configuration for Subprojects in
`pom.xml`](dev-release-deploy-config.html#deploy-configuration-subprojects),
or apply the provided diff by executing the following command in the
JGit workspace:

``` 
  git apply /path/to/gerrit/tools/jgit-snapshot-deploy-pom.diff
```

## Prepare the Release

Since JGit has its own release process we do not push any release tags.
Instead we will use the output of `git describe` as the version of the
current JGit snapshot.

In the JGit workspace, execute the following command:

``` 
  ./tools/version.sh --release $(git describe)
```

## Publish the Release

To deploy the new snapshot, execute the following command in the JGit
workspace:

``` 
  mvn deploy
```

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

