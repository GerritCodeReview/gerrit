Gerrit2 - Developer Setup
=========================

You need Apache Maven to compile the code, and a SQL database
to house the Gerrit2 metadata.  PostgreSQL is currently the only
supported database.

To create a new client workspace:

====
  mkdir gerrit2
  cd gerrit2
  repo init -u git://android.git.kernel.org/tools/manifest.git
====

Important Links
---------------

Google Web Toolkit:

* http://code.google.com/webtoolkit/download.html[Download]

Apache Maven:

* http://maven.apache.org/download.html[Download]
* http://maven.apache.org/run-maven/index.html[Running]

PostgreSQL:

* http://www.postgresql.org/download/[Download]
* http://www.postgresql.org/docs/[Documentation]

Apache SSHD:

* http://mina.apache.org/sshd/[SSHD]


Setting up the Database
-----------------------

You'll need to configure your development workspace to use a database
gwtorm supports (or add the necessary dialect support to gwtorm,
and then configure your workspace anyway).

====
  cp gerrit-war/src/main/webapp/WEB-INF/extra/GerritServer.properties_example GerritServer.properties
====

Now edit GerritServer.properties to uncomment the database you are
going to use, and possibly update properties such as "user" and
"password" to reflect the actual connection information used.

====
  # PostgreSQL
  database.driver = org.postgresql.Driver
  database.url = jdbc:postgresql:reviewdb
  database.user = gerrit2
  database.password = letmein
====

PostgreSQL Setup
~~~~~~~~~~~~~~~~

Initialize and start PostgreSQL (where $data is the location of your data):

====
  initdb --locale en_US.UTF-8 -D $data
  postmaster -D $data >/tmp/logfile 2>&1 &
====

Create the JDBC user as a normal user (no superuser access) and
assign it an encrypted password:

====
  createuser -A -D -P -E gerrit2
====

Create the database listed in your GerritServer.properties and set
the JDBC user as the owner of that database:

====
  createdb -E UTF-8 -O gerrit2 reviewdb
====


Configuring Eclipse
-------------------

If you want to use the Eclipse IDE for development work, please
see link:dev-eclipse.html[Eclipse Setup] for more details on how
to configure your workspace.


Building
--------

From the command line:

====
  mvn clean package
====

Output WAR will be placed in:

====
  gerrit-war/target/gerrit-*.war
====

When debugging browser specific issues use gwtStyle `DETAILED` so
the resulting JavaScript more closely matches the Java sources.
For example, this can help narrow down what code line 30,400 in
the JavaScript happens to be.

====
  mvn package -DgwtStyle=DETAILED
====

On Mac OS X ensure 'Java For Mac OS X 10.5 Upate 4' has been installed,
and that `JAVA_HOME` is set to `/System/Library/Frameworks/JavaVM.framework/Versions/1.6/Home`.
You can check this by running `java -version` and looking for
`build 1.6.0_13-b03-211`.  Versions of Java 6 prior to this version
crash during the build due to a bug in the JIT compiler.

Production Compile
------------------

*Always* use

----
  mvn clean package
----

to create a production build.  The `./to_hosted.sh` script that
setups the development environment for Eclipse hosted mode also
creates a state that produces a corrupt production build.

Final Setup
-----------

Since you are creating a Gerrit instance for testing, you need to
also follow the other steps outlined under "Initialize the Schema"
in the Installation Guide:

* link:install.html[Installation Guide]
* link:project-setup.html[Project Setup]


Client-Server RPC
-----------------

The client-server RPC implementation is gwtjsonrpc, not the stock RPC
system that comes with GWT.  This buys us automatic XSRF protection.
It also makes all of the messages readable and writable by any JSON
implementation, facilitating "mashups" and 3rd party clients.

The programming API is virtually identical (you just need to extend
RemoteJsonService instead of RemoteService).


Why GWT?
--------

We like it.  Plus we can write Java code once and run it both in
the browser and on the server side.

GERRIT
------
Part of link:index.html[Gerrit Code Review]
