---
title: " Quickstart for Installing Gerrit on Linux"
sidebar: gerritdoc_sidebar
permalink: linux-quickstart.html
---
This quickstart shows you how to install Gerrit on a Linux machine.

> **Note**
> 
> The installation steps provided in this quickstart are for
> demonstration purposes only. They are not intended for use in a
> production environment.
> 
> For a more detailed installation guide, see [Standalone Daemon
> Installation Guide](install.html).

## Before you begin

To complete this quickstart, you need:

1.  A Unix-based server such as any of the Linux flavors or BSD.

2.  Java SE Runtime Environment version 1.8 or later.

## Download Gerrit

From the Linux machine on which you want to install Gerrit:

1.  Open a terminal window.

2.  Download the Gerrit archive. See [Gerrit Code Review -
    Releases](https://gerrit-releases.storage.googleapis.com/index.html)
    for a list of available archives.

The steps in this quickstart used Gerrrit 2.14.2, which you can download
using a command such as:

    wget https://www.gerritcodereview.com/download/gerrit-2.14.2.war

> **Note**
> 
> If you want to build and install Gerrit from the source files, see
> [Developer Setup](dev-readme.html).

## Install and initialize Gerrit

From the command line, type the following:

    java -jar gerrit*.war init --batch --dev -d ~/gerrit_testsite

The preceding command uses two parameters:

  - `--batch`. This parameter assigns default values to a variety of
    Gerrit configuration options. To learn more about these
    configuration options, see [Configuration](config-gerrit.html).

  - `--dev`. This parameter configures the server to use the
    authentication option, `DEVELOPMENT_BECOME_ANY_ACCOUNT`. This
    authentication type makes it easy for you to switch between
    different users to explore how Gerrit works. To learn more about
    setting up Gerrit for development, see [Developer
    Setup](dev-readme.html).

This command displays a number of messages in the terminal window. The
following is an example of these messages:

    Generating SSH host key ... rsa(simple)... done
    Initialized /home/gerrit/gerrit_testsite
    Executing /home/gerrit/gerrit_testsite/bin/gerrit.sh start
    Starting Gerrit Code Review: OK

The last message you should see is `Starting Gerrit Code Review: OK`.
This message informs you that the Gerrit service is now running.

## Update the listen URL

Another recommended task is to change the URL that Gerrit listens to
from `*` to `localhost`. This change helps prevent outside connections
from contacting the
    instance.

    git config --file ~/gerrit_testsite/etc/gerrit.config httpd.listenUrl 'http://localhost:8080'

## Restart the Gerrit service

You must restart the Gerrit service for your authentication type and
listen URL changes to take effect.

    ~/gerrit_testsite/bin/gerrit.sh restart

## Viewing Gerrit

At this point, you have a basic installation of Gerrit. You can view
this installation by opening a browser and entering the following URL:

    http://localhost:8080

## Next steps

Through this quickstart, you now have a simple version of Gerrit running
on your Linux machine. You can use this installation to explore the UI
and become familiar with some of Gerrit’s features. For a more detailed
installation guide, see [Standalone Daemon Installation
Guide](install.html).

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

