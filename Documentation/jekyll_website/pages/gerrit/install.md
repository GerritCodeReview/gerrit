---
title: " Gerrit Code Review - Standalone Daemon Installation Guide"
sidebar: gerritdoc_sidebar
permalink: install.html
---
## Requirements

To run the Gerrit service, the following requirements must be met on the
host:

  - JRE, minimum version 1.8
    [Download](http://www.oracle.com/technetwork/java/javase/downloads/index.html)

You’ll also need an SQL database to house the review metadata. You have
the choice of either using the embedded H2 or to host your own MySQL or
PostgreSQL.

## Configure Java for Strong Cryptography

Support for extra strength cryptographic ciphers: *AES128CTR*,
*AES256CTR*, *ARCFOUR256*, and *ARCFOUR128* can be enabled by
downloading the *Java Cryptography Extension (JCE) Unlimited Strength
Jurisdiction Policy Files* from Oracle and installing them into your
JRE.

> **Note**
> 
> Installing JCE extensions is optional and export restrictions may
> apply.

1.  Download the unlimited strength JCE policy files.
    
      - [JDK7 JCE policy
        files](http://www.oracle.com/technetwork/java/javase/downloads/jce-7-download-432124.html)
    
      - [JDK8 JCE policy
        files](http://www.oracle.com/technetwork/java/javase/downloads/jce8-download-2133166.html)

2.  Uncompress and extract the downloaded file.
    
    The downloaded file contains the following files:
    
    <table>
    <colgroup>
    <col width="50%" />
    <col width="50%" />
    </colgroup>
    <tbody>
    <tr class="odd">
    <td><p>README.txt</p></td>
    <td><p>Information about JCE and installation guide</p></td>
    </tr>
    <tr class="even">
    <td><p>local_policy.jar</p></td>
    <td><p>Unlimited strength local policy file</p></td>
    </tr>
    <tr class="odd">
    <td><p>US_export_policy.jar</p></td>
    <td><p>Unlimited strength US export policy file</p></td>
    </tr>
    </tbody>
    </table>

3.  Install the unlimited strength policy JAR files by following
    instructions found in `README.txt`.

## Download Gerrit

Current and past binary releases of Gerrit can be obtained from the
[Gerrit Releases
site](https://www.gerritcodereview.com/download/index.html).

Download any current `*.war` package. The war will be referred to as
`gerrit.war` from this point forward, so you may find it easier to
rename the downloaded file.

If you would prefer to build Gerrit directly from source, review the
notes under [developer setup](dev-readme.html).

## Database Setup

During the init phase of Gerrit you will need to specify which database
to use.

### H2

If you choose H2, Gerrit will automatically set up the embedded H2
database as backend so no set up or configuration is necessary.

Using the embedded H2 database is the easiest way to get a Gerrit site
up and running, making it ideal for proof of concepts or small team
servers. On the flip side, H2 is not the recommended option for large
corporate installations. This is because there is no easy way to
interact with the database while Gerrit is offline, it’s not easy to
backup the data, and it’s not possible to set up H2 in a load
balanced/hotswap configuration.

If this option interests you, you might want to consider [the quick
guide](install-quick.html).

### Apache Derby

If Derby is selected, Gerrit will automatically set up the embedded
Derby database as backend so no set up or configuration is necessary.

Currently only support for embedded mode is added. There are two other
deployment options for Apache Derby that can be added later:

  - [Derby Network Server (standalone
    mode)](http://db.apache.org/derby/papers/DerbyTut/ns_intro.html#Network+Server+Options)

  - [Embedded Server (hybrid
    mode)](http://db.apache.org/derby/papers/DerbyTut/ns_intro.html#Embedded+Server)

### PostgreSQL

This option is more complicated than the H2 option but is recommended
for larger installations. It’s the database backend with the largest
userbase in the Gerrit community.

Create a user for the web application within PostgreSQL, assign it a
password, create a database to store the metadata, and grant the user
full rights on the newly created database:

``` 
  $ createuser --username=postgres -RDIElPS gerrit
  $ createdb --username=postgres -E UTF-8 -O gerrit reviewdb
```

Visit PostgreSQL’s
[documentation](http://www.postgresql.org/docs/9.1/interactive/index.html)
for further information regarding using PostgreSQL.

### MySQL

Requirements: MySQL version 5.1 or later.

This option is also more complicated than the H2 option. Just as with
PostgreSQL it’s also recommended for larger installations.

Create a user for the web application within the database, assign it a
password, create a database, and give the newly created user full rights
on it:

``` 
  mysql

  CREATE USER 'gerrit'@'localhost' IDENTIFIED BY 'secret';
  CREATE DATABASE reviewdb DEFAULT CHARACTER SET 'utf8';
  GRANT ALL ON reviewdb.* TO 'gerrit'@'localhost';
  FLUSH PRIVILEGES;
```

Visit MySQL’s [documentation](http://dev.mysql.com/doc/) for further
information regarding using MySQL.

### MariaDB

Requirements: MariaDB version 5.5 or later.

Refer to MySQL section above how to create MariaDB database.

Visit MariaDB’s [documentation](https://mariadb.com/kb/en/mariadb/) for
further information regarding using MariaDB.

### Oracle

PostgreSQL or H2 is the recommended database for Gerrit Code Review.
Oracle is supported for environments where running on an existing Oracle
installation simplifies administrative overheads, such as database
backups.

Create a user for the web application within sqlplus, assign it a
password, and grant the user full rights on the newly created
database:

``` 
  SQL> create user gerrit identified by secret_password default tablespace users;
  SQL> grant connect, resources to gerrit;
```

JDBC driver ojdbc6.jar must be obtained from your Oracle distribution.
Gerrit initialization process tries to copy it from a known location:

    /u01/app/oracle/product/11.2.0/xe/jdbc/lib/ojdbc6.jar

If this file can not be located at this place, then the alternative
location can be provided.

Instance name is the Oracle SID. Sample database section in
$site\_path/etc/gerrit.config:

    [database]
            type = oracle
            instance = xe
            hostname = localhost
            username = gerrit
            port = 1521

Sample database section in $site\_path/etc/secure.config:

    [database]
            password = secret_password

### SAP MaxDB

SAP MaxDB is a supported database for running Gerrit Code Review.
However it is recommended only for environments where you intend to run
Gerrit on an existing MaxDB installation to reduce administrative
overhead.

In the MaxDB studio or using the SQLCLI command line interface create a
user *gerrit* with the user class *RESOURCE* and a password \<secret
password\>. This will also create an associated schema on the database.

To run Gerrit on MaxDB, you need to obtain the MaxDB JDBC driver. It can
be found in your MaxDB installation at the following location:

  - on Windows 64bit at "C:\\Program
    Files\\sdb\\MaxDB\\runtime\\jar\\sapdbc.jar"

  - on Linux at "/opt/sdb/MaxDB/runtime/jar/sapdbc.jar"

It needs to be stored in the *lib* folder of the review site.

In the following sample database section it is assumed that the database
name is *reviewdb* and the database is installed on localhost:

In $site\_path/etc/gerrit.config:

    [database]
            type = maxdb
            database = reviewdb
            hostname = localhost
            username = gerrit

In $site\_path/etc/secure.config:

    [database]
            password = <secret password>

Visit SAP MaxDB’s [documentation](http://maxdb.sap.com/documentation/)
for further information regarding using SAP MaxDB.

### DB2

IBM DB2 is a supported database for running Gerrit Code Review. However
it is recommended only for environments where you intend to run Gerrit
on an existing DB2 installation to reduce administrative overhead.

Create a system wide user for the Gerrit application, and grant the user
full rights on the newly created database:

``` 
  db2 => create database gerrit
  db2 => connect to gerrit
  db2 => grant connect,accessctrl,dataaccess,dbadm,secadm on database to gerrit;
```

JDBC driver db2jcc4.jar and db2jcc\_license\_cu.jar must be obtained
from your DB2 distribution. Gerrit initialization process tries to copy
it from a known location:

    /opt/ibm/db2/V10.5/java/db2jcc4.jar
    /opt/ibm/db2/V10.5/java/db2jcc_license_cu.jar

If these files cannot be located at this place, then an alternative
location can be provided during init step execution.

Sample database section in $site\_path/etc/gerrit.config:

    [database]
            type = db2
            database = gerrit
            hostname = localhost
            username = gerrit
            port = 50001

Sample database section in $site\_path/etc/secure.config:

    [database]
            password = secret_password

### SAP HANA

SAP HANA is a supported database for running Gerrit Code Review. However
it is recommended only for environments where you intend to run Gerrit
on an existing HANA installation to reduce administrative overhead.

In the HANA studio or the SAP HANA Web-based Development Workbench
create a user *GERRIT2* with the role *RESTRICTED\_USER\_JDBC\_ACCESS*
and a password \<secret password\>. This will also create an associated
schema on the database. As this user would be required to change the
password upon first login you might want to to disable the password
lifetime check by executing *ALTER USER GERRIT2 DISABLE PASSWORD
LIFETIME*.

To run Gerrit on HANA, you need to obtain the HANA JDBC driver. It can
be found as described
[here](http://help.sap.com/saphelp_hanaplatform/helpdata/en/ff/15928cf5594d78b841fbbe649f04b4/frameset.htm).
It needs to be stored in the *lib* folder of the review site.

In the following sample database section it is assumed that HANA is
running on the host *hana.host* and listening on port *4242* where a
schema/user GERRIT2 was created:

In $site\_path/etc/gerrit.config:

    [database]
            type = hana
            hostname = hana.host
            port = 4242
            username = GERRIT2

In order to configure a specific database in a multi-database
environment (MDC) the database name has to be specified additionally:

In $site\_path/etc/gerrit.config:

    [database]
            type = hana
            hostname = hana.host
            database = tdb1
            port = 4242
            username = GERRIT2

In $site\_path/etc/secure.config:

    [database]
            password = <secret password>

Visit SAP HANA’s [documentation](http://help.sap.com/hana_appliance/)
for further information regarding using SAP HANA.

## Initialize the Site

Gerrit stores configuration files, the server’s SSH keys, and the
managed Git repositories under a local directory, typically referred to
as `'$site_path'`. If the embedded H2 database is being used, its data
files will also be stored under this directory.

You also have to decide where to store your server side git
repositories. This can either be a relative path under `'$site_path'` or
an absolute path anywhere on your server system. You have to choose a
place before commencing your init phase.

Initialize a new site directory by running the init command, passing the
path of the site directory to be created as an argument to the *-d*
option. Its recommended that Gerrit Code Review be given its own user
account on the host system:

``` 
  sudo adduser gerrit
  sudo su gerrit

  java -jar gerrit.war init -d /path/to/your/gerrit_application_directory
```

> **Note**
> 
> If you choose a location where your new user doesn’t have any
> privileges, you may have to manually create the directory first and
> then give ownership of that location to the `'gerrit'` user.

If run from an interactive terminal, the init command will prompt
through a series of configuration questions, including gathering
information about the database created above. If the terminal is not
interactive, running the init command will choose some reasonable
default selections, and will use the embedded H2 database. Once the init
phase is complete, you can review your settings in the file
`'$site_path/etc/gerrit.config'`.

When running the init command, additional JARs might be downloaded to
support optional selected functionality. If a download fails a URL will
be displayed and init will wait for the user to manually download the
JAR and store it in the target location.

When the init phase is complete, the daemon will be automatically
started in the background and your web browser will open to the site:

``` 
  Initialized /home/gerrit/review_site
  Executing /home/gerrit/review_site/bin/gerrit.sh start
  Starting Gerrit Code Review: OK
  Waiting for server to start ... OK
  Opening browser ...
```

When the browser opens, sign in to Gerrit through the web interface. The
first user to sign-in and register an account will be automatically
placed into the fully privileged Administrators group, permitting server
management over the web and over SSH. Subsequent users will be
automatically registered as unprivileged users.

## Installation Complete

Your base Gerrit server is now installed and running. You’re now ready
to either set up more projects or start working with the projects you’ve
already imported.

## Project Setup

See [Project Configuration](project-configuration.html) for further
details on how to register a new project with Gerrit. This step is
necessary if existing Git repositories were not imported during *init*.

## Start/Stop Daemon

To control the Gerrit Code Review daemon that is running in the
background, use the rc.d style start script created by *init*:

``` 
  review_site/bin/gerrit.sh start
  review_site/bin/gerrit.sh stop
  review_site/bin/gerrit.sh restart
```

(*Optional*) Configure the daemon to automatically start and stop with
the operating system.

Uncomment the following 3 lines in the `'$site_path/bin/gerrit.sh'`
script:

``` 
 chkconfig: 3 99 99
 description: Gerrit Code Review
 processname: gerrit
```

Then link the `gerrit.sh` script into `rc3.d`:

``` 
  sudo ln -snf `pwd`/review_site/bin/gerrit.sh /etc/init.d/gerrit
  sudo ln -snf /etc/init.d/gerrit /etc/rc3.d/S90gerrit
```

(*Optional*) To enable autocompletion of the gerrit.sh commands, install
autocompletion from the `/contrib/bash_completion` script. Refer to the
script’s header comments for installation instructions.

To install Gerrit into an existing servlet container instead of using
the embedded Jetty server, see [J2EE installation](install-j2ee.html).

## Installation on Windows

If new site is going to be initialized with Bouncy Castle cryptography,
ssh-keygen command must be available during the init phase. If you have
[Git for Windows](https://git-for-windows.github.io/) installed, start
Command Prompt and temporary add directory with ssh-keygen to the PATH
environment variable just before running init command:

    PATH=%PATH%;c:\Program Files\Git\usr\bin

Please note that the path in the above example must not be
double-quoted.

To run the daemon after site initialization execute:

    cd C:\MY\GERRIT\SITE
    java.exe -jar bin\gerrit.war daemon --console-log

To stop the daemon press Ctrl+C.

### Install the daemon as Windows Service

To install Gerrit as Windows Service use the [Apache Commons Daemon
Procrun](http://commons.apache.org/proper/commons-daemon/procrun.html).

Sample install
    command:

    prunsrv.exe //IS//Gerrit --DisplayName="Gerrit Code Review" --Startup=auto ^
          --Jvm="C:\Program Files\Java\jre1.8.0_65\bin\server\jvm.dll" ^
          --Classpath=C:\MY\GERRIT\SITE\bin\gerrit.war ^
          --LogPath=C:\MY\GERRIT\SITE\logs ^
          --StartPath=C:\MY\GERRIT\SITE ^
          --StartMode=jvm --StopMode=jvm ^
          --StartClass=com.google.gerrit.launcher.GerritLauncher --StartMethod=daemonStart ^
          --StopClass=com.google.gerrit.launcher.GerritLauncher --StopMethod=daemonStop ^
          ++DependsOn=postgresql-x64-9.4

## Site Customization

Gerrit Code Review supports some site-specific customization options.
For more information, see the related topics in this manual:

  - [Reverse Proxy](config-reverseproxy.html)

  - [Single Sign-On Systems](config-sso.html)

  - [Themes](config-themes.html)

  - [Gitweb Integration](config-gitweb.html)

  - [Other System Settings](config-gerrit.html)

  - [Automatic Site Initialization on
    Startup](config-auto-site-initialization.html)

## Anonymous Access

Exporting the Git repository directory
([gerrit.basePath](config-gerrit.html#gerrit.basePath)) over the
anonymous, unencrypted git:// protocol is more efficient than Gerrit’s
internal SSH daemon. See the `git-daemon` documentation for details on
how to configure this if anonymous access is desired.

  - [man
    git-daemon](http://www.kernel.org/pub/software/scm/git/docs/git-daemon.html)

## Plugins

Place Gerrit plugins in the review\_site/plugins directory to have them
loaded on Gerrit startup.

## External Documentation Links

  - [PostgreSQL Documentation](http://www.postgresql.org/docs/)

  - [MySQL
    Documentation](http://dev.mysql.com/doc/)

  - [git-daemon](http://www.kernel.org/pub/software/scm/git/docs/git-daemon.html)

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

