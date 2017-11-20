---
title: " Gerrit Code Review - Developer Setup"
sidebar: gerritdoc_sidebar
permalink: dev-readme.html
---
Google Bazel is needed to compile the code, and an SQL database to house
the review metadata. H2 is recommended for development databases, as it
requires no external server process.

## Getting the Source

Create a new client workspace:

``` 
  git clone --recursive https://gerrit.googlesource.com/gerrit
  cd gerrit
```

The `--recursive` option is needed on `git clone` to ensure that the
core plugins, which are included as git submodules, are also cloned.

## Compiling

Please refer to [Building with Bazel](#dev-bazel#).

## Switching between branches

When switching between branches with `git checkout`, be aware that
submodule revisions are not altered. This may result in the wrong plugin
revisions being present, unneeded plugins being present, or expected
plugins being missing.

After switching branches, make sure the submodules are at the correct
revisions for the new branch with the commands:

``` 
  git submodule update
  git clean -fdx
```

> **Caution**
> 
> If you decide to store your Eclipse/IntelliJ project files in the
> Gerrit source directories, executing `git clean -fdx` will remove them
> and hence screw up your project.

## Configuring Eclipse

To use the Eclipse IDE for development, please see [Eclipse
Setup](dev-eclipse.html).

For details on how to configure the Eclipse workspace with Bazel, refer
to: [Eclipse integration with Bazel](dev-bazel.html#eclipse).

## Configuring IntelliJ IDEA

Please refer to [IntelliJ Setup](#dev-intellij#) for detailed
instructions.

## Mac OS X

On Mac OS X ensure "Java For Mac OS X 10.5 Update 4" (or later) has been
installed, and that `JAVA_HOME` is set to the [required Java
version](install.html#Requirements).

Java installations can typically be found in
"/System/Library/Frameworks/JavaVM.framework/Versions".

You can check the installed Java version by running `java -version` in
the terminal.

## Site Initialization

After compiling [(above)](#compile_project), run Gerrit’s *init* command
to create a testing site for development use:

``` 
  $(bazel info output_base)/external/local_jdk/bin/java \
     -jar bazel-bin/gerrit.war init -d ../gerrit_testsite
```

> **Note**
> 
> You must use the same Java version that Bazel used for the build. This
> Java version is available at `$(bazel info
> output_base)/external/local_jdk/bin/java`.

During initialization, make two changes to the default settings:

  - Change the listen addresses from *\** to *localhost* to prevent
    outside connections from contacting the development instance; and

  - Change the auth type from *OPENID* to
    *DEVELOPMENT\_BECOME\_ANY\_ACCOUNT* to allow yourself to create and
    act as arbitrary test accounts on your development instance.

Continue through init until it completes. The daemon will automatically
start in the background and a web browser will launch to the start page.
From here you can sign in as the account created during init, register
additional accounts, create projects, and more.

When you want to shut down the daemon, simply run:

``` 
  ../gerrit_testsite/bin/gerrit.sh stop
```

## Working with the Local Server

If you need to create additional accounts on your development instance,
click *become* in the upper right corner, select *Switch User*, and then
register a new account.

Use the `ssh` protocol to clone from and push to the local server. For
example, to clone a repository that you’ve created through the admin
interface, run:

    git clone ssh://username@localhost:29418/projectname

Then you’ll be able to create changes the same way users do, with

    git push origin HEAD:refs/for/master

## Testing

### Running the Acceptance Tests

Gerrit has a set of integration tests that test the Gerrit daemon via
REST, SSH and the git protocol.

A new review site is created for each test and the Gerrit daemon is
started on that site. When the test has finished the Gerrit daemon is
shutdown.

For instructions on running the integration tests with Bazel, please
refer to: [Running Unit Tests with Bazel](#dev-bazel#tests).

### Running the Daemon

The daemon can be directly launched from the build area, without copying
to the test site:

``` 
  $(bazel info output_base)/external/local_jdk/bin/java \
     -jar bazel-bin/gerrit.war daemon -d ../gerrit_testsite \
     --console-log
```

> **Note**
> 
> Please refer to [this explanation](#special_bazel_java_version) for
> details why using `java -jar` isn’t sufficient.

If you want to debug the Gerrit server of this test site, you can open a
debug port (for example port 5005) by inserting

    -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005

directly after `-jar` of the previous command. Please refer to
[Debugging a remote Gerrit server](#dev-intellij#remote-debug) for
instructions of how to attach IntelliJ.

### Running the Daemon with Gerrit Inspector

[Gerrit Inspector](dev-inspector.html) is an interactive scriptable
environment to inspect and modify internal state of the system.

This environment is available on the system console after the system
starts. Leaving the Inspector will shutdown the Gerrit instance.

The environment allows interactive work as well as running of Python
scripts for troubleshooting.

Gerrit Inspect can be started by adding *-s* option to the command used
to launch the daemon:

``` 
  $(bazel info output_base)/external/local_jdk/bin/java \
     -jar bazel-bin/gerrit.war daemon -d ../gerrit_testsite -s
```

> **Note**
> 
> Please refer to [this explanation](#special_bazel_java_version) for
> details why using `java -jar` isn’t sufficient.

Gerrit Inspector examines Java libraries first, then loads its
initialization scripts and then starts a command line prompt on the
console:

``` 
  Welcome to the Gerrit Inspector
  Enter help() to see the above again, EOF to quit and stop Gerrit
  Jython 2.5.2 (Release_2_5_2:7206, Mar 2 2011, 23:12:06)
  [OpenJDK 64-Bit Server VM (Sun Microsystems Inc.)] on java1.6.0 running for Gerrit 2.3-rc0-163-g01967ef
  >>>
```

With the Inspector enabled Gerrit can be used normally and all
interfaces (HTTP, SSH etc.) are available.

Care must be taken not to modify internal state of the system when using
the Inspector.

### Querying the Database

The embedded H2 database can be queried and updated from the command
line. If the daemon is not currently running:

``` 
  $(bazel info output_base)/external/local_jdk/bin/java \
     -jar bazel-bin/gerrit.war gsql -d ../gerrit_testsite -s
```

> **Note**
> 
> Please refer to [this explanation](#special_bazel_java_version) for
> details why using `java -jar` isn’t sufficient.

Or, if it is running and the database is in use, connect over SSH using
an administrator user account:

``` 
  ssh -p 29418 user@localhost gerrit gsql
```

### Debugging JavaScript

When debugging browser specific issues add `?dbg=1` to the URL so the
resulting JavaScript more closely matches the Java sources. The debug
pages use the GWT pretty format, where function and variable names match
the Java sources.

``` 
  http://localhost:8080/?dbg=1
```

## Client-Server RPC

The client-server RPC implementation is gwtjsonrpc, not the stock RPC
system that comes with GWT. This buys us automatic XSRF protection. It
also makes all of the messages readable and writable by any JSON
implementation, facilitating "mashups" and 3rd party clients.

The programming API is virtually identical, except service interfaces
extend RemoteJsonService instead of RemoteService.

## Why GWT?

We like it. Plus we can write Java code once and run it both in the
browser and on the server side.

## External Links

Google Web Toolkit:

  - [Download](http://code.google.com/webtoolkit/download.html)

Apache SSHD:

  - [SSHD](http://mina.apache.org/sshd/)

H2:

  - [H2](http://www.h2database.com/)

  - [SQL Reference](http://www.h2database.com/html/grammar.html)

PostgreSQL:

  - [Download](http://www.postgresql.org/download/)

  - [Documentation](http://www.postgresql.org/docs/)

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

