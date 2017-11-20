[[createdb]]
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

