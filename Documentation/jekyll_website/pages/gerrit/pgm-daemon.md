---
title: " daemon"
sidebar: gerritdoc_sidebar
permalink: pgm-daemon.html
---
## NAME

daemon - Gerrit network server

## SYNOPSIS

> 
> 
>     java -jar gerrit.war daemon
>       -d <SITE_PATH>
>       [--enable-httpd | --disable-httpd]
>       [--enable-sshd | --disable-sshd]
>       [--console-log]
>       [--slave]
>       [--headless]
>       [--init]
>       [-s]

## DESCRIPTION

Runs the Gerrit network daemon on the local system, configured as per
the local copy of [gerrit.config](config-gerrit.html).

The path to gerrit.config is read from the metadata database, which
requires that all slaves (and master) reading from the same database
must place gerrit.config at the same location on the local filesystem.
However, any option within gerrit.config, including
[gerrit.basePath](config-gerrit.html#gerrit.basePath) may be set to
different values.

## OPTIONS

  - \-d; --site-path  
    Location of the gerrit.config file, and all other per-site
    configuration data, supporting libraries and log files.

  - \--enable-httpd; --disable-httpd  
    Enable (or disable) the internal HTTP daemon, answering web
    requests. Enabled by default when --slave is not used.

  - \--enable-sshd; --disable-sshd  
    Enable (or disable) the internal SSH daemon, answering SSH clients
    and remotely executed commands. Enabled by default.

  - \--slave  
    Run in slave mode, permitting only read operations by clients.
    Commands which modify state such as
    [receive-pack](cmd-receive-pack.html) (creates new changes or
    updates existing ones) or [review](cmd-review.html) (sets approve
    marks) are disabled.
    
    This option automatically implies *--enable-sshd*.

  - \--console-log  
    Send log messages to the console, instead of to the standard log
    file *$site\_path/logs/error\_log*.

  - \--headless  
    Don’t start the default Gerrit UI. May be useful when Gerrit is run
    with an alternative UI.

  - \--init  
    Run init before starting the daemon. This will create a new site or
    upgrade an existing site.

  - \--s  
    Start [Gerrit Inspector](dev-inspector.html) on the console, a
    built-in interactive inspection environment to assist debugging and
    troubleshooting of Gerrit code.
    
    This options requires *jython.jar* from the [Jython
    distribution](http://www.jython.org) to be present in
    *$site\_path/lib* directory.

## CONTEXT

This command can only be run on a server which has direct connectivity
to the metadata database, and local access to the managed Git
repositories.

## LOGGING

Error and warning messages from the server are automatically written to
the log file under *$site\_path/logs/error\_log*. This log file is
automatically rotated at 12:00 AM GMT each day, allowing an external log
cleaning service to clean up the prior logs.

## KNOWN ISSUES

Slave daemon caches can quickly become out of date when modifications
are made on the master. The following configuration is suggested in a
slave to reduce the maxAge for each cache entry, so that changes are
recognized in a reasonable period of time:

    [cache "accounts"]
      maxAge = 5 min
    [cache "diff"]
      maxAge = 5 min
    [cache "groups"]
      maxAge = 5 min
    [cache "projects"]
      maxAge = 5 min
    [cache "sshkeys"]
      maxAge = 5 min

and if LDAP support was enabled, also include:

    [cache "ldap_groups"]
      maxAge = 5 min
    [cache "ldap_usernames"]
      maxAge = 5 min

Automatic cache coherency between master and slave systems is planned to
be implemented in a future version.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

