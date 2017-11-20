---
title: " Gerrit Code Review - Building plugins"
sidebar: gerritdoc_sidebar
permalink: dev-build-plugins.html
---
From build process perspective there are three types of plugins:

  - Maven driven

  - Bazel tree driven

  - Bazel standalone

These types can be combined: if both files in plugin’s root directory
exist:

  - `BUILD`

  - `pom.xml`

the plugin can be built with both Bazel and Maven.

## Maven driven build

If plugin contains `pom.xml` file, it can be built with Maven as
usually:

    mvn clean package

Exceptions from the rule above:

### Exception 1:

Plugin’s `pom.xml` references snapshot version of plugin API:
`2.8-SNAPSHOT`. In this case there are two possibilities:

  - switch to release API. Change plugin API version in `pom.xml` from
    `2.8-SNAPSHOT` to `2.8.1` and repeat step 1 above.

  - build and install `SNAPSHOT` version of plugin API in local Maven
    repository:

<!-- end list -->

    ./tools/maven/api.sh install

### Exception 2:

Plugin’s `pom.xml` references other own or foreign (unpublished)
libraries or even other Gerrit plugins. These libraries and/or plugins
must be built and installed in local Maven repository. Clone the related
projects and issue

    mvn install

Repeat step 1. above.

## Bazel in tree driven

The fact that plugin contains `BUILD` file doesn’t mean that building
this plugin from the plugin directory works.

Bazel in tree driven means it can only be built from within Gerrit tree.
Clone or link the plugin into gerrit/plugins directory:

    cd gerrit
    bazel build plugins/<plugin-name>:<plugin-name>

The output can be normally found in the following directory:

    bazel-genfiles/plugins/<plugin-name>/<plugin-name>.jar

Some plugins describe their build process in
`src/main/resources/Documentation/build.md` file. It may worth checking.

### Plugins with external dependencies

If the plugin has external dependencies, then they must be included from
Gerrit’s own WORKSPACE file. This can be achieved by including them in
`external_plugin_deps.bzl`. During the build in Gerrit tree, this file
must be copied over the dummy one in `plugins` directory.

Example for content of `external_plugin_deps.bzl` file:

    load("//tools/bzl:maven_jar.bzl", "maven_jar")
    
    def external_plugin_deps():
      maven_jar(
          name = 'org_apache_tika_tika_core',
          artifact = 'org.apache.tika:tika-core:1.12',
          sha1 = '5ab95580d22fe1dee79cffbcd98bb509a32da09b',
      )

## Bazel standalone driven

Only few plugins support that mode for now:

    cd reviewers
    bazel build reviewers

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

