= Gerrit Code Review - Developer Setup

Facebook Buck is needed to compile the code, and an SQL database to
house the review metadata.  H2 is recommended for development
databases, as it requires no external server process.


== Getting the Source

Create a new client workspace:

----
  git clone --recursive https://gerrit.googlesource.com/gerrit
  cd gerrit
----

The `--recursive` option is needed on `git clone` to ensure that
the core plugins, which are included as git submodules, are also
cloned.


== Compiling

For details on how to build the source code with Buck, refer to:
link:dev-buck.html#build[Building on the command line with Buck].


== Switching between branches

When switching between branches with `git checkout`, be aware that
submodule revisions are not altered.  This may result in the wrong
plugin revisions being present, unneeded plugins being present, or
expected plugins being missing.

After switching branches, make sure the submodules are at the correct
revisions for the new branch with the commands:

----
  git submodule update
  git clean -fdx
----


== Configuring Eclipse

To use the Eclipse IDE for development, please see
link:dev-eclipse.html[Eclipse Setup].

For details on how to configure the Eclipse workspace with Buck,
refer to: link:dev-buck.html#eclipse[Eclipse integration with Buck].


== Configuring IntelliJ IDEA

To use IntelliJ IDEA for development, the easiest way is to follow
Eclipse integration and then open it as Eclipse project in IDEA.
You need the Eclipse plugin activated in IntelliJ IDEA.

Once you start compiling using both buck and your Gerrit project in
IDEA, you will likely need to mark the below directories as generated
sources roots. You can do so using the IDEA "Project" view. In the
context menu of each one of these, use "Mark Directory As" to mark
them as "Generated Sources Root":

----
  __auto_value_tests_gen__
  __httpd_gen__
  __server_gen__
----

== Mac OS X

On Mac OS X ensure "Java For Mac OS X 10.5 Update 4" (or later) has
been installed, and that `JAVA_HOME` is set to the
link:install.html#Requirements[required Java version].

Java installations can typically be found in
"/System/Library/Frameworks/JavaVM.framework/Versions".

You can check the installed Java version by running `java -version` in
the terminal.

[[init]]
== Site Initialization

After compiling (above), run Gerrit's 'init' command to create a
testing site for development use:

----
  java -jar buck-out/gen/gerrit/gerrit.war init -d ../gerrit_testsite
----

Accept defaults by pressing Enter until 'init' completes, or add
the '--batch' command line option to avoid them entirely.  It is
recommended to change the listen addresses from '*' to 'localhost' to
prevent outside connections from contacting the development instance.

The daemon will automatically start in the background and a web
browser will launch to the start page, enabling login via OpenID.

Shutdown the daemon after registering the administrator account
through the web interface:

----
  ../gerrit_testsite/bin/gerrit.sh stop
----


== Testing


[[tests]]
=== Running the Acceptance Tests

Gerrit has a set of integration tests that test the Gerrit daemon via
REST, SSH and the git protocol.

A new review site is created for each test and the Gerrit daemon is
started on that site. When the test has finished the Gerrit daemon is
shutdown.

For instructions on running the integration tests with Buck,
please refer to:
link:dev-buck.html#tests[Running integration tests with Buck].


=== Running the Daemon

The daemon can be directly launched from the build area, without
copying to the test site:

----
  java -jar buck-out/gen/gerrit/gerrit.war daemon -d ../gerrit_testsite
----

=== Running the Daemon with Gerrit Inspector

link:dev-inspector.html[Gerrit Inspector] is an interactive scriptable
environment to inspect and modify internal state of the system.

This environment is available on the system console after
the system starts. Leaving the Inspector will shutdown the Gerrit
instance.

The environment allows interactive work as well as running of
Python scripts for troubleshooting.

Gerrit Inspect can be started by adding '-s' option to the
command used to launch the daemon:

----
  java -jar buck-out/gen/gerrit/gerrit.war daemon -d ../gerrit_testsite -s
----

Gerrit Inspector examines Java libraries first, then loads
its initialization scripts and then starts a command line
prompt on the console:

----
  Welcome to the Gerrit Inspector
  Enter help() to see the above again, EOF to quit and stop Gerrit
  Jython 2.5.2 (Release_2_5_2:7206, Mar 2 2011, 23:12:06)
  [OpenJDK 64-Bit Server VM (Sun Microsystems Inc.)] on java1.6.0 running for Gerrit 2.3-rc0-163-g01967ef
  >>>
----

With the Inspector enabled Gerrit can be used normally and all
interfaces (HTTP, SSH etc.) are available.

Care must be taken not to modify internal state of the system
when using the Inspector.

=== Querying the Database

The embedded H2 database can be queried and updated from the
command line.  If the daemon is not currently running:

----
  java -jar buck-out/gen/gerrit/gerrit.war gsql -d ../gerrit_testsite
----

Or, if it is running and the database is in use, connect over SSH
using an administrator user account:

----
  ssh -p 29418 user@localhost gerrit gsql
----


[[debug-javascript]]
=== Debugging JavaScript

When debugging browser specific issues add `?dbg=1` to the URL so the
resulting JavaScript more closely matches the Java sources.  The debug
pages use the GWT pretty format, where function and variable names
match the Java sources.

----
  http://localhost:8080/?dbg=1
----


== Client-Server RPC

The client-server RPC implementation is gwtjsonrpc, not the stock RPC
system that comes with GWT.  This buys us automatic XSRF protection.
It also makes all of the messages readable and writable by any JSON
implementation, facilitating "mashups" and 3rd party clients.

The programming API is virtually identical, except service interfaces
extend RemoteJsonService instead of RemoteService.


== Why GWT?

We like it.  Plus we can write Java code once and run it both in
the browser and on the server side.


== External Links

Google Web Toolkit:

* http://code.google.com/webtoolkit/download.html[Download]

Apache SSHD:

* http://mina.apache.org/sshd/[SSHD]

H2:

* http://www.h2database.com/[H2]
* http://www.h2database.com/html/grammar.html[SQL Reference]

PostgreSQL:

* http://www.postgresql.org/download/[Download]
* http://www.postgresql.org/docs/[Documentation]


GERRIT
------
Part of link:index.html[Gerrit Code Review]

SEARCHBOX
---------
