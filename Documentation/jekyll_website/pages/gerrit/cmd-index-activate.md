---
title: " gerrit index activate"
sidebar: cmd_sidebar
permalink: cmd-index-activate.html
---
## NAME

gerrit index activate - Activate the latest index version available

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit index activate <INDEX>

## DESCRIPTION

Gerrit supports online index schema upgrades. When starting Gerrit for
the first time after an upgrade that requires an index schema upgrade,
the online indexer will be started. If the schema upgrade is a success,
the new index will be activated and if it fails, a statement in the logs
will be printed with the number of successfully/failed indexed changes.

This command allows to activate the latest index even if there were some
failures.

## ACCESS

Caller must be a member of the privileged *Administrators* group.

## SCRIPTING

This command is intended to be used in scripts.

## OPTIONS

  - \<INDEX\>  
    The index to activate. Currently supported values:
    
      - changes
    
      - accounts

## EXAMPLES

Activate the latest change index:

``` 
  $ ssh -p 29418 review.example.com gerrit activate changes
```

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

