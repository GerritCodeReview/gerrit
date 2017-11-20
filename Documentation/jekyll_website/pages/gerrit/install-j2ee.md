---
title: " Gerrit Code Review - J2EE Installation"
sidebar: gerritdoc_sidebar
permalink: install-j2ee.html
---
## Description

Gerrit binary distributions include a standalone Jetty servlet
container, but are packaged as a standard WAR file to permit easy
deployment to other existing container installations if using the
standalone daemon is not desired.

Gerrit Code Review can be installed into any J2EE servlet container,
including popular open source containers such as Jetty or Tomcat, or any
commercial server which supports the J2EE servlet specification.

## Installation

  - Complete the [database setup](install.html#createdb) and [site
    initialization](install.html#init) tasks described in the standard
    installation documentation.

  - Stop the embedded daemon that was automatically started by *init*:
    
    ``` 
      review_site/bin/gerrit.sh stop
    ```

  - Configure JNDI DataSource *jdbc/ReviewDb*.
    
    This DataSource must point to the database you created above. Don’t
    forget to ensure your JNDI configuration can load the necessary JDBC
    drivers. You may wish to ensure connection pooling is configured and
    enabled within the DataSource.

  - Deploy the *gerrit.war* file to your application server.
    
    The deployment process differs between servers, but typically this
    can be accomplished by copying *gerrit.war* into the *webapps/*
    subdirectory of the container’s installation.

  - (*Optional*) Install Bouncy Castle Crypto API
    
    If you enabled Bouncy Castle Crypto during *init*, copy the JAR from
    `'$site_path'/lib` into your servlet container’s extensions
    directory so it’s available to Gerrit Code Review.

  - (*Optional*) [Configure Automatic Site Initialization on
    Startup](config-auto-site-initialization.html)

## Jetty 7.x

These directions will configure Gerrit as the default web application,
allowing URLs like `http://example.com/4543` to jump directly to change
4543.

Download and unzip a release version of Jetty. From here on we call the
unpacked directory `$JETTY_HOME`.

  - [Jetty Downloads](http://www.eclipse.org/jetty/downloads.php)

If this is a fresh installation of Jetty, move into the installation
directory and do some cleanup to remove the sample webapps:

``` 
  cd $JETTY_HOME
  rm -rf contexts/* webapps/*
```

Copy Gerrit Code Review into the deployment:

``` 
  cp ~/gerrit.war webapps/gerrit.war
  java -jar webapps/gerrit.war cat extra/jetty7/gerrit.xml >contexts/gerrit.xml
```

Install the required additional libraries by copying them into the
`'$JETTY_HOME'/lib/ext` directory:

``` 
  cp ../review_db/lib/* lib/ext/
  java -jar webapps/gerrit.war cat lib/commons-dbcp-1.2.2.jar >lib/ext/commons-dbcp-1.2.2.jar
  java -jar webapps/gerrit.war cat lib/commons-pool-1.5.4.jar >lib/ext/commons-pool-1.5.4.jar
  java -jar webapps/gerrit.war cat lib/h2-1.2.128.jar >lib/ext/h2-1.2.128.jar
  java -jar webapps/gerrit.war cat lib/postgresql-8.4-701.jdbc4.jar >lib/ext/postgresql-8.4-701.jdbc4.jar
```

Edit `'$JETTY_HOME'/contexts/gerrit.xml` to correctly configure the
database and outgoing SMTP connections, especially the user and password
fields.

If OpenID authentication (or certain enterprise single-sign-on
solutions) is being used, you may need to increase the header buffer
size parameter, due to very long header lines being used by the OpenID
authentication handshake process. Add the following to
`'$JETTY_HOME'/etc/jetty.xml` under
`org.eclipse.jetty.server.nio.SelectChannelConnector`:

``` 
  <Set name="headerBufferSize">16384</Set>
```

To start automatically when the system boots, create a start script and
modify it for your
configuration:

``` 
  java -jar webapps/gerrit.war --cat extra/jetty7/gerrit-jetty.sh >/etc/init.d/gerrit-jetty
  vi /etc/init.d/gerrit-jetty
```

> **Tip**
> 
> Under Jetty, restarting the web application (e.g. after modifying
> `system_config`) is as simple as touching the context config file:
> `'$JETTY_HOME'/contexts/gerrit.xml`

## Tomcat 7.x

If a reverse proxy is used in front of Tomcat then see the
[configuration instructions for encoding
slashes](config-reverseproxy.html). Otherwise Tomcat must be configured
to encode slashes, by adding
`-Dorg.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH=true` to the
`CATALINA_OPTS` environment variable.

Excerpt from the
[documentation](https://tomcat.apache.org/tomcat-7.0-doc/config/systemprops.html):

    Property org.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH:
    If this is true '%2F' and '%5C' will be permitted as path delimiters.
    If not specified, the default value of false will be used.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

