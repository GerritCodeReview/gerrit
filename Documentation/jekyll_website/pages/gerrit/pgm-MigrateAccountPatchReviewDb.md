---
title: " MigrateAccountPatchReviewDb"
sidebar: gerritdoc_sidebar
permalink: pgm-MigrateAccountPatchReviewDb.html
---
## NAME

MigrateAccountPatchReviewDb - Migrates AccountPatchReviewDb from one
database backend to another.

## SYNOPSIS

> 
> 
>     java -jar gerrit.war MigrateAccountPatchReviewDb
>       -d <SITE_PATH>
>       [--sourceUrl] [--chunkSize]

## DESCRIPTION

Migrates AccountPatchReviewDb from one database backend to another. The
AccountPatchReviewDb is a database used to store the user file reviewed
flags.

This command is only intended to be run if the configuration parameter
[accountPatchReviewDb.url](config-gerrit.html#accountPatchReviewDb.url)
is set or changed.

To migrate AccountPatchReviewDb:

  - Stop Gerrit

  - Configure new value for
    [accountPatchReviewDb.url](config-gerrit.html#accountPatchReviewDb.url)

  - Migrate data using this command

  - Start Gerrit

## OPTIONS

  - \-d; --site-path  
    Location of the `gerrit.config` file, and all other per-site
    configuration data, supporting libraries and log files.

  - \--sourceUrl  
    Url of source database. Only need to be specified if the source is
    not H2.

  - \--chunkSize  
    Chunk size of fetching from source and pushing to target on each
    time. Defaults to 100000.

## CONTEXT

This command can only be run on a server which has direct connectivity
to the database.

## EXAMPLES

To migrate from H2 to the database specified by
[accountPatchReviewDb.url](config-gerrit.html#accountPatchReviewDb.url)
in gerrit.config:

``` 
        $ java -jar gerrit.war MigrateAccountPatchReviewDb -d site_path
```

## SEE ALSO

  - Configuration parameter
    [accountPatchReviewDb.url](config-gerrit.html#accountPatchReviewDb.url)

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

