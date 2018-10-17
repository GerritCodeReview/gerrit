= Gerrit Code Review: Developer Setup

To build a developer instance, you'll need link:https://bazel.build/[Bazel] to
compile the code.

== Getting the Source

Create a new client workspace:

----
  git clone --recurse-submodules https://gerrit.googlesource.com/gerrit
  cd gerrit
----

The `--recursive` option is needed on `git clone` to ensure that
the core plugins, which are included as git submodules, are also
cloned.

[[compile_project]]
== Compiling

For details, see <<dev-bazel#,Building with Bazel>>.

== Configuring Eclipse

To use the Eclipse IDE for development, see
link:dev-eclipse.html[Eclipse Setup].

To configure the Eclipse workspace with Bazel, see
link:dev-bazel.html#eclipse[Eclipse integration with Bazel].

== Configuring IntelliJ IDEA

See <<dev-intellij#,IntelliJ Setup>> for details.

== MacOS

On MacOS, ensure that "Java for MacOS X 10.5 Update 4" (or higher) is installed
and that `JAVA_HOME` is set to the
link:install.html#Requirements[required Java version].

Java installations can typically be found in
"/System/Library/Frameworks/JavaVM.framework/Versions".

To check the installed version of Java, open a terminal window and run:

`java -version`

[[init]]
== Site Initialization

After you compile the project <<compile_project,(above)>>, run the Gerrit
`init`
command to create a test site:

----
  $(bazel info output_base)/external/local_jdk/bin/java \
     -jar bazel-bin/gerrit.war init -d ../gerrit_testsite
----

[[special_bazel_java_version]]
NOTE: You must use the same Java version that Bazel used for the build, which
is available at `$(bazel info output_base)/external/local_jdk/bin/java`.

During initialization, change two settings from the defaults:

*  To ensure the development instance is not externally accessible, change the
listen addresses from '*' to 'localhost'.
*  To allow yourself to create and act as arbitrary test accounts on your
development instance, change the auth type from 'OPENID' to 'DEVELOPMENT_BECOME_ANY_ACCOUNT'.

After initializing the test site, Gerrit starts serving in the background. A
web browser displays the Start page.

On the Start page, you can:

.  Log in as the account you created during the initialization process.
.  Register additional accounts.
.  Create projects.

To shut down the daemon, run:

----
  ../gerrit_testsite/bin/gerrit.sh stop
----


[[localdev]]
== Working with the Local Server

To create more accounts on your development instance:

.  Click 'become' in the upper right corner.
.  Select 'Switch User'.
.  Register a new account.

Use the `ssh` protocol to clone from and push to the local server. For
example, to clone a repository that you've created through the admin
interface, run:

----
git clone ssh://username@localhost:29418/projectname
----

To create changes as users of Gerrit would, run:

----
git push origin HEAD:refs/for/master
----

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

[[run_daemon]]
=== Running the Daemon

The daemon can be launched directly from the build area, without
copying to the test site:

----
  $(bazel info output_base)/external/local_jdk/bin/java \
     -jar bazel-bin/gerrit.war daemon -d ../gerrit_testsite \
     --console-log
----

NOTE: To learn why using `java -jar` isn't sufficient, see
<<special_bazel_java_version,this explanation>>.

To debug the Gerrit server of this test site:

.  Open a debug port (such as port 5005). To do so, insert the following code
immediately after `-jar` in the previous command. To learn how to attach
IntelliJ, see <<dev-intellij#remote-debug,Debugging a remote Gerrit server>>.

----
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
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
     -jar bazel-bin/gerrit.war daemon -d ../gerrit_testsite -s
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

=== Querying the database

The embedded H2 database can be queried and updated from the command line. If
the daemon is not running, run:

----
  $(bazel info output_base)/external/local_jdk/bin/java \
     -jar bazel-bin/gerrit.war gsql -d ../gerrit_testsite -s
----

NOTE: To learn why using `java -jar` isn't sufficient, see
<<special_bazel_java_version,this explanation>>.

Alternatively, if the daemon is running and the database is in use, use an
administrator user account to connect over SSH:

----
  ssh -p 29418 user@localhost gerrit gsql
----


== Switching between branches

When using `git checkout` without `--recurse-submodules` to switch between
branches, submodule revisions are not altered, which can result in:

*  Incorrect or unneeded plugin revisions.
*  Missing plugins.

After you switch branches, ensure that you have the correct versions of
the submodules.

CAUTION: If you store Eclipse or IntelliJ project files in the Gerrit source
directories, do *_not_* run `git clean -fdx`. Doing so may remove untracked files and damage your project. For more information, see
link:https://git-scm.com/docs/git-clean[git-clean].

Run the following:

----
  git submodule update
  git clean -ffd
----

GERRIT
------
Part of link:index.html[Gerrit Code Review]

SEARCHBOX
---------
