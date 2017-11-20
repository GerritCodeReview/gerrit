---
title: " SwitchSecureStore"
sidebar: gerritdoc_sidebar
permalink: pgm-SwitchSecureStore.html
---
## NAME

SwitchSecureStore - Changes the currently used SecureStore
implementation

## SYNOPSIS

> 
> 
>     java -jar gerrit.war SwitchSecureStore
>       [--new-secure-store-lib]

## DESCRIPTION

Changes the SecureStore implementation used by Gerrit. It migrates all
data stored in the old implementation, removes the old implementation
jar file from `$site_path/lib` and puts the new one there. As a final
step the
[gerrit.secureStoreClass](config-gerrit.html#gerrit.secureStoreClass)
property of `gerrit.config` will be updated.

All dependencies not provided by Gerrit should be put the in
`$site_path/lib` directory manually, before running the
`SwitchSecureStore` program.

After this operation there is no automatic way back the to standard
Gerrit no-op secure store implementation, however there is a manual
procedure: \* stop Gerrit, \* remove SecureStore jar file from
`$site_path/lib`, \* put plain text passwords into
`$site_path/etc/secure.conf` file, \* start Gerrit.

## OPTIONS

  - \--new-secure-store-lib  
    Path to jar file with new SecureStore implementation. Jar
    dependencies must be put in `$site_path/lib` directory.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

