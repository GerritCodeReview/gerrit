---
title: " gerrit logging set-level"
sidebar: cmd_sidebar
permalink: cmd-logging-set-level.html
---
## NAME

gerrit logging set-level - set the logging level

gerrit logging set - set the logging level

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit logging set-level | set
>       <LEVEL>
>       <NAME>

## DESCRIPTION

Set the logging level of specified loggers.

## Options

  - \<LEVEL\>  
    Required; logging level for which the loggers should be set. *reset*
    can be used to revert all loggers back to their level at deployment
    time.

  - \<NAME\>  
    Set the level of the loggers which contain the input argument in
    their name. If this argument is not provided, all loggers will have
    their level changed. Note that this argument has no effect if
    *reset* is passed in LEVEL.

## ACCESS

Caller must have the ADMINISTRATE\_SERVER capability.

## Examples

Change the logging level of the loggers in the package com.google to
DEBUG.

``` 
    $ssh -p 29418 review.example.com gerrit logging set-level \
     debug com.google.
```

Reset the logging level of every logger to what they were at deployment
time.

``` 
    $ssh -p 29418 review.example.com gerrit logging set-level \
     reset
```

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

