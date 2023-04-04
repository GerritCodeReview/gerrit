:linkattrs:
= Gerrit Code Review: Developer Setup

To build a developer instance, you'll need link:https://bazel.build/[Bazel,role=external,window=_blank] to
compile the code, preferably launched with link:https://github.com/bazelbuild/bazelisk[Bazelisk,role=external,window=_blank].

== Git Setup

[[clone]]
=== Getting the Source

Create a new client workspace:

----
  git clone --recurse-submodules https://gerrit.googlesource.com/gerrit
----

The `--recurse-submodules` option is needed on `git clone` to ensure that the
core plugins, which are included as git submodules, are also cloned.

Next setup the commit-hook. This is necessary to ensure that each commit has a
`Change-Id`.

----
  cd gerrit && (
    cd .git/hooks
    ln -s ../../resources/com/google/gerrit/server/tools/root/hooks/commit-msg
  )
----

=== Switching between branches

When using `git checkout` without `--recurse-submodules` to switch between
branches, submodule revisions are not altered, which can result in:

*  Incorrect or unneeded plugin revisions.
*  Missing plugins.

After you switch branches, ensure that you have the correct versions of
the submodules.

CAUTION: If you store Eclipse or IntelliJ project files in the Gerrit source
directories, do *_not_* run `git clean -fdx`. Doing so may remove untracked files and damage your project. For more information, see
link:https://git-scm.com/docs/git-clean[git-clean,role=external,window=_blank].

Run the following:

----
  git submodule update
  git clean -ffd
----

[[compile_project]]
== Compiling

For details, see <<dev-bazel#,Building with Bazel>>.


== Testing

[[tests]]
=== Running the acceptance tests

Gerrit contains acceptance tests that validate the Gerrit daemon via REST, SSH,
and the Git protocol.

A new review site is created for each test and the Gerrit daemon is
then started on that site. When the test is completed, the Gerrit daemon is
shut down.

For instructions on running the acceptance tests with Bazel,
see <<dev-bazel#tests,Running Unit Tests with Bazel>>.

[[e2e]]
=== End-to-end tests

<<dev-e2e-tests#,This document>> describes how `e2e` (load or functional) test
scenarios are implemented using link:https://gatling.io/[`Gatling`,role=external,window=_blank].


== Local server

[[init]]
=== Site Initialization

After you compile the project <<compile_project,(above)>>, run the Gerrit
`init`
command to create a test site:

----
  export GERRIT_SITE=~/gerrit_testsite
  $(bazel info output_base)/external/local_jdk/bin/java \
      -jar bazel-bin/gerrit.war init --batch --dev -d $GERRIT_SITE
----

[[special_bazel_java_version]]
NOTE: You must use the same Java version that Bazel used for the build, which
is available at `$(bazel info output_base)/external/local_jdk/bin/java`.

This command takes two parameters:

* `--batch` assigns default values to several Gerrit configuration
    options. To learn more about these options, see
    link:config-gerrit.html[Configuration].
* `--dev` configures the Gerrit server to use the authentication
  option, `DEVELOPMENT_BECOME_ANY_ACCOUNT`, which enables you to
  switch between different users to explore how Gerrit works. To learn more
  about setting up Gerrit for development, see
  link:dev-readme.html[Gerrit Code Review: Developer Setup].

After initializing the test site, Gerrit starts serving in the background. A
web browser displays the Start page.

On the Start page, you can:

.  Log in as the account you created during the initialization process.
.  Register additional accounts.
.  Create projects.

To shut down the daemon, run:

----
  $GERRIT_SITE/bin/gerrit.sh stop
----


[[localdev]]
=== Working with the Local Server

To create more accounts on your development instance:

.  Click 'become' in the upper right corner.
.  Select 'Switch User'.
.  Register a new account.
.  link:user-upload.html#ssh[Configure your SSH key].

Use the `ssh` protocol to clone from and push to the local server. For
example, to clone a repository that you've created through the admin
interface, run:

----
git clone ssh://username@localhost:29418/projectname
----

To use the `HTTP` protocol, run:

----
git clone http://username@localhost:8080/projectname
----

The default password for user `admin` is `secret`. You can regenerate a
password in the UI under User Settings -- HTTP credentials. The password can be
stored locally to avoid retyping it:

----
git config --global credential.helper store
git pull
----

To create changes as users of Gerrit would, run:

----
git push origin HEAD:refs/for/master
----

[[run_daemon]]
=== Running the Daemon

The daemon can be launched directly from the build area, without
copying to the test site:

----
  $(bazel info output_base)/external/local_jdk/bin/java \
     -jar bazel-bin/gerrit.war daemon -d $GERRIT_SITE \
     --console-log
----

NOTE: To learn why using `java -jar` isn't sufficient, see
<<special_bazel_java_version,this explanation>>.

NOTE: When launching the daemon this way, the settings from the `[container]` section from the
`$GERRIT_SITE/etc/gerrit.config` are not honored.

To debug the Gerrit server of this test site:

.  Open a debug port (such as port 5005). To do so, insert the following code
immediately after `-jar` in the previous command. To learn how to attach
IntelliJ, see <<dev-intellij#remote-debug,Debugging a remote Gerrit server>>.

----
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
----

=== Running the Daemon honoring the [container] section settings

To run the Daemon and honor the `[container]` section settings use the `gerrit.sh` script:

----
  $ cd $GERRIT_SITE
  $ bin/gerrit.sh run
----

To run the Daemon in debug mode use the `--debug` option:

----
  $ bin/gerrit.sh run --debug
----

The default debug port is `8000`. To specify a different debug port use the `--debug-port` option:

----
  $ bin/gerrit.sh run --debug --debug-port=5005
----

The `--debug-address` option also exists and is a synonym for the ``--debug-port` option:

----
  $ bin/gerrit.sh run --debug --debug-address=5005
----

Note that, by default, the debugger will only accept connections from the localhost. To enable
debug connections from other host(s) provide also a host name or wildcard in the `--debug-address`
value:

----
  $ bin/gerrit.sh run --debug --debug-address=*:5005
----

Debugging the Daemon startup requires starting the JVM in suspended debug mode. The JVM will await
for a debugger to attach before proceeding with the start. Use the `--suspend` option for that
scenario:

----
  $ bin/gerrit.sh run --debug --suspend
----

=== Running the Daemon with Gerrit Inspector

link:dev-inspector.html[Gerrit Inspector] is an interactive scriptable
environment you can use to inspect and modify the internal state of the system.

Gerrit Inspector appears on the system console whenever the system starts.
Leaving the Inspector shuts down the Gerrit instance.

To troubleshoot, the Inspector enables interactive work as well as running of
Python scripts.

To start the Inspector, add the '-s' option to the daemon start command:

----
  $(bazel info output_base)/external/local_jdk/bin/java \
     -jar bazel-bin/gerrit.war daemon -d $GERRIT_SITE -s
----

NOTE: To learn why using `java -jar` isn't sufficient, see
<<special_bazel_java_version,this explanation>>.

Inspector examines Java libraries, loads the initialization scripts, and
starts a command line prompt on the console:

----
  Welcome to the Gerrit Inspector
  Enter help() to see the above again, EOF to quit and stop Gerrit
  Jython 2.5.2 (Release_2_5_2:7206, Mar 2 2011, 23:12:06)
  [OpenJDK 64-Bit Server VM (Sun Microsystems Inc.)] on java1.6.0 running for
  Gerrit 2.3-rc0-163-g01967ef
  >>>
----

When the Inspector is enabled, you can use Gerrit as usual and all
interfaces (including HTTP and SSH) are available.

CAUTION: When using the Inspector, be careful not to modify the internal state
of the system.


== Setup for backend developers

=== Configuring Eclipse

To use the Eclipse IDE for development, see
link:dev-eclipse.html[Eclipse Setup].

To configure the Eclipse workspace with Bazel, see
link:dev-bazel.html#eclipse[Eclipse integration with Bazel].

=== Configuring IntelliJ IDEA

See <<dev-intellij#,IntelliJ Setup>> for details.

== Setup for frontend developers
See link:https://gerrit.googlesource.com/gerrit/+/master/polygerrit-ui/README.md[Frontend Developer Setup].


GERRIT
------
Part of link:index.html[Gerrit Code Review]

SEARCHBOX
---------
