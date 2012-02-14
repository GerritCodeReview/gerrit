Gerrit Code Review - Developer Setup
====================================

Apache Maven is needed to compile the code, and a SQL database
to house the review metadata.  H2 is recommended for development
databases, as it requires no external server process.

Get the Source
--------------

Create a new client workspace:

----
  git clone https://gerrit.googlesource.com/gerrit
  cd gerrit
----


Configuring Eclipse
-------------------

To use the Eclipse IDE for development, please see
link:dev-eclipse.html[Eclipse Setup] for more details on how to
configure the workspace with the Maven build scripts.


[[build]]
Building
--------

From the command line:

----
  mvn package
----

Output executable WAR will be placed in:

----
  gerrit-war/target/gerrit-*.war
----

Mac OS X
~~~~~~~~
On Mac OS X ensure "Java For Mac OS X 10.5 Upate 4" (or later) has
been installed, and that `JAVA_HOME` is set to
"/System/Library/Frameworks/JavaVM.framework/Versions/1.6/Home".
Check the installed version by running `java -version` and looking
for 'build 1.6.0_13-b03-211'.  Versions of Java 6 prior to this
version crash during the build due to a bug in the JIT compiler.


[[init]]
Site Initialization
-------------------

After compiling (above), run Gerrit's 'init' command to create a
testing site for development use:

----
  java -jar gerrit-war/target/gerrit-*.war init -d ../test_site
----

Accept defaults by pressing Enter until 'init' completes, or add
the '\--batch' command line option to avoid them entirely.  It is
recommended to change the listen addresses from '*' to 'localhost' to
prevent outside connections from contacting the development instance.

The daemon will automatically start in the background and a web
browser will launch to the start page, enabling login via OpenID.

Shutdown the daemon after registering the administrator account
through the web interface:

----
  ../test_site/bin/gerrit.sh stop
----


Testing
-------

Running the Daemon
~~~~~~~~~~~~~~~~~~

The daemon can be directly launched from the build area, without
copying to the test site:

----
  java -jar gerrit-war/target/gerrit-*.war daemon -d ../test_site
----


Querying the Database
~~~~~~~~~~~~~~~~~~~~~

The embedded H2 database can be queried and updated from the
command line.  If the daemon is not currently running:

----
  java -jar gerrit-war/target/gerrit-*.war gsql -d ../test_site
----

Or, if it is running and the database is in use, connect over SSH
using an administrator user account:

----
  ssh -p 29418 user@localhost gerrit gsql
----


Debugging JavaScript
~~~~~~~~~~~~~~~~~~~~

When debugging browser specific issues use `-Dgwt.style=DETAILED`
so the resulting JavaScript more closely matches the Java sources.
This can be used to help narrow down what code line 30,400 in the
JavaScript happens to be.

----
  mvn package -Dgwt.style=DETAILED
----


Release Builds
--------------

To create a release build for a production server, or deployment
through the download site:

----
  ./tools/release.sh
----

If AsciiDoc isn't installed or is otherwise unavailable, the WAR
can still be built without the embedded documentation by passing
an additional flag:

----
  ./tools/release.sh --without-documentation
----


Client-Server RPC
-----------------

The client-server RPC implementation is gwtjsonrpc, not the stock RPC
system that comes with GWT.  This buys us automatic XSRF protection.
It also makes all of the messages readable and writable by any JSON
implementation, facilitating "mashups" and 3rd party clients.

The programming API is virtually identical, except service interfaces
extend RemoteJsonService instead of RemoteService.


Why GWT?
--------

We like it.  Plus we can write Java code once and run it both in
the browser and on the server side.


External Links
--------------

Google Web Toolkit:

* http://code.google.com/webtoolkit/download.html[Download]

Apache Maven:

* http://maven.apache.org/download.html[Download]
* http://maven.apache.org/run-maven/index.html[Running]

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
