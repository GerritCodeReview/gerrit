---
title: " gerrit index project"
sidebar: cmd_sidebar
permalink: cmd-index-project.html
---
## NAME

gerrit index project - Index all the changes in one or more projects.

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit index project <PROJECT> [<PROJECT> …]

## DESCRIPTION

Index all the changes in one or more projects.

## ACCESS

Caller must have the *Maintain Server* capability.

## SCRIPTING

This command is intended to be used in scripts.

## OPTIONS

  - \<PROJECT\>  
    Required; name of the project to be indexed.

## EXAMPLES

Index all changes in projects MyProject and
NiceProject.

``` 
    $ ssh -p 29418 user@review.example.com gerrit index project MyProject NiceProject
```

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

