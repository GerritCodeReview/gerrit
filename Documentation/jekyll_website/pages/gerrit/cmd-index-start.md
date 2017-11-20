---
title: " gerrit index start"
sidebar: cmd_sidebar
permalink: cmd-index-start.html
---
## NAME

gerrit index start - Start the online indexer

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit index start <INDEX> [--force]

## DESCRIPTION

Gerrit supports online index schema upgrades. When starting Gerrit for
the first time after an upgrade that requires an index schema upgrade,
the online indexer will be started. If the schema upgrade is a success,
the new index will be activated and if it fails, a statement in the logs
will be printed with the number of successfully/failed indexed changes.

This command allows restarting the online indexer without having to
restart Gerrit. This command will not start the indexer if it is already
running or if the active index is the latest.

The [show-queue](cmd-show-queue.html) command provides online index
status.

## ACCESS

Caller must be a member of the privileged *Administrators* group.

## SCRIPTING

This command is intended to be used in scripts.

## OPTIONS

  - \<INDEX\>  
    Restart the online indexer on this secondary index. Currently
    supported values:
    
      - changes
    
      - accounts

  - \--force  
    Force an online re-index.

## EXAMPLES

Start the online indexer for the *changes* index:

``` 
  $ ssh -p 29418 review.example.com gerrit index start changes
```

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

