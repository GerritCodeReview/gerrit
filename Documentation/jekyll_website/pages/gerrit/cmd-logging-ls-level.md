---
title: " gerrit logging ls-level"
sidebar: cmd_sidebar
permalink: cmd-logging-ls-level.html
---
## NAME

gerrit logging ls-level - view the logging level

gerrit logging ls - view the logging level

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit logging ls-level | ls
>       <NAME>

## DESCRIPTION

View the logging level of specified loggers.

## Options

  - \<NAME\>  
    Display the loggers which contain the input argument in their name.
    If this argument is not provided, all loggers will be printed.

## ACCESS

Caller must have the ADMINISTRATE\_SERVER capability.

## Examples

View the logging level of the loggers in the package com.google:

``` 
    $ssh -p 29418 review.example.com gerrit logging ls-level \
     com.google.
```

View the logging level of every logger

``` 
    $ssh -p 29418 review.example.com gerrit logging ls-level
```

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

