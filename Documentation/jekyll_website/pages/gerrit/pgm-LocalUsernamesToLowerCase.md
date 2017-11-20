---
title: " LocalUsernamesToLowerCase"
sidebar: gerritdoc_sidebar
permalink: pgm-LocalUsernamesToLowerCase.html
---
## NAME

LocalUsernamesToLowerCase - Convert the local username of every account
to lower case

## SYNOPSIS

> 
> 
>     java -jar gerrit.war LocalUsernamesToLowerCase
>       -d <SITE_PATH>

## DESCRIPTION

Converts the local username for every account to lower case. The local
username is the username that is used to login into the Gerrit Web UI.

This task is only intended to be run if the configuration parameter
[ldap.localUsernameToLowerCase](config-gerrit.html#ldap.localUsernameToLowerCase)
was set to true to achieve case insensitive LDAP login to the Gerrit Web
UI.

Please be aware that the conversion of the local usernames to lower case
can’t be undone.

The program will produce errors if there are accounts that have the same
local username, but with different case. In this case the local username
for these accounts is not converted to lower case.

After all usernames have been migrated, the [reindex](pgm-reindex.html)
program is automatically invoked to reindex all accounts.

This task cannot run in the background concurrently to the server; it
must be run by itself.

## OPTIONS

  - \-d; --site-path  
    Location of the gerrit.config file, and all other per-site
    configuration data, supporting libraries and log files.

## CONTEXT

This command can only be run on a server which has direct connectivity
to the metadata database.

## EXAMPLES

To convert the local username of every account to lower case:

``` 
        $ java -jar gerrit.war LocalUsernamesToLowerCase -d site_path
```

## SEE ALSO

  - Configuration parameter
    [ldap.localUsernameToLowerCase](config-gerrit.html#ldap.localUsernameToLowerCase)

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

