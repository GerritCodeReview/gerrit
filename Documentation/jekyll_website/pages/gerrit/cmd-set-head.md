---
title: " gerrit set-head"
sidebar: cmd_sidebar
permalink: cmd-set-head.html
---
## NAME

gerrit set-head - Change a project’s HEAD.

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit set-head <NAME>
>       --new-head <REF>

## DESCRIPTION

Modifies a given project’s HEAD reference.

The command is argument-safe, that is, if no argument is given the
previous settings are kept intact.

## ACCESS

Caller must be an owner of the given project.

## SCRIPTING

This command is intended to be used in scripts.

## OPTIONS

  - \<NAME\>  
    Required; name of the project to change the HEAD. If name ends with
    `.git` the suffix will be automatically removed.

  - \--new-head  
    Required; name of the ref that should be set as new HEAD. The
    *refs/heads/* prefix can be omitted.

## EXAMPLES

Change HEAD of project `example` to `stable-2.11`
branch:

``` 
    $ ssh -p 29418 review.example.com gerrit set-head example --new-head stable-2.11
```

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

