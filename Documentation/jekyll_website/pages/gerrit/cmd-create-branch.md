---
title: " gerrit create-branch"
sidebar: cmd_sidebar
permalink: cmd-create-branch.html
---
## NAME

gerrit create-branch - Create a new branch

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit create-branch
>       <PROJECT>
>       <NAME>
>       <REVISION>

## DESCRIPTION

Creates a new branch for a project.

## ACCESS

Caller should have [Create
Reference](access-control.html#category_create) permission on the
project.

Administrators do not automatically have permission to create branches.
It must be granted via the Create Reference permission.

## SCRIPTING

This command is intended to be used in scripts.

## OPTIONS

  - \<PROJECT\>  
    Required; name of the project.

  - \<NAME\>  
    Required; name of the branch to be created.

  - \<REVISION\>  
    Required; base revision of the new branch.

## EXAMPLES

Create a new branch called *newbranch* from the *master* branch of the
project
*myproject*.

``` 
    $ ssh -p 29418 review.example.com gerrit create-branch myproject newbranch master
```

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

