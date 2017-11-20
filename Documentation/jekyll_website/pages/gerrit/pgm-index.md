---
title: " Gerrit Code Review - Server Side Administrative Tools"
sidebar: gerritdoc_sidebar
permalink: pgm-index.html
---
Server side tools can be started by executing the WAR file through the
Java command line. For example:

    $ java -jar gerrit.war <tool> [<options>]

Tool should be one of the following names:

## Tools

  - [init](pgm-init.html)  
    Initialize a new Gerrit server installation.

  - [daemon](pgm-daemon.html)  
    Gerrit HTTP, SSH network server.

  - [gsql](pgm-gsql.html)  
    Administrative interface to idle database.

  - [prolog-shell](pgm-prolog-shell.html)  
    Simple interactive Prolog interpreter.

  - [reindex](pgm-reindex.html)  
    Rebuild the secondary index.

  - [SwitchSecureStore](pgm-SwitchSecureStore.html)  
    Change used SecureStore implementation.

  - [rulec](pgm-rulec.html)  
    Compile project-specific Prolog rules to JARs.

  - version  
    Display the release version of Gerrit Code Review.

  - [passwd](pgm-passwd.html)  
    Set or reset password in secure.config.

### Transition Utilities

  - [LocalUsernamesToLowerCase](pgm-LocalUsernamesToLowerCase.html)  
    Convert the local username of every account to lower
    case.

  - [MigrateAccountPatchReviewDb](pgm-MigrateAccountPatchReviewDb.html)  
    Migrates AccountPatchReviewDb from one database backend to another.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

