---
title: " gerrit index changes"
sidebar: cmd_sidebar
permalink: cmd-index-changes.html
---
## NAME

gerrit index changes - Index one or more changes.

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit index changes <CHANGE> [<CHANGE> …]

## DESCRIPTION

Indexes one or more changes.

Changes can be specified in the [same
format](rest-api-changes.html#change-id) supported by the REST API.

## ACCESS

Caller must have the *Maintain Server* capability, or be the owner of
the change to be indexed.

## SCRIPTING

This command is intended to be used in scripts.

## OPTIONS

  - \--CHANGE  
    Required; changes to be indexed.

## EXAMPLES

Index changes with legacy ID numbers 1 and 2.

``` 
    $ ssh -p 29418 user@review.example.com gerrit index changes 1 2
```

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

