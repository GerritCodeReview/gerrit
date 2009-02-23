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
* http://code.google.com/docreader/#p=google-web-toolkit-doc-1-5[Developer's Guide]

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
  cd src/main/java
  cp GerritServer.properties_example GerritServer.properties
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
  mvn package
====

Output WAR will be placed in:

====
  target/gerrit-*.war
====


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
the browser and on the server side.  This will be very useful as
we implement offline support in Gerrit.
