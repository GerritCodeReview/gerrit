---
title: " gerrit gc"
sidebar: cmd_sidebar
permalink: cmd-gc.html
---
## NAME

gerrit gc - Run the Git garbage collection

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit gc
>       [--all]
>       [--show-progress]
>       [--aggressive]
>       <NAME> …

## DESCRIPTION

Runs the Git garbage collection for the specified projects.

A Gerrit system administrator can define the default parameters that
should be used for running the garbage collection in the user global Git
configuration file of the system user that runs the Gerrit server.

Since the user global Git configuration file is overlaid with the Git
configuration on repository level it is possible to specify repository
specific parameters for the garbage collection in the Git repository
configuration of every project.

## ACCESS

Caller must be a member of the privileged *Administrators* group, or
have been granted the [Run Garbage
Collection](access-control.html#capability_runGC) global capability.

## SCRIPTING

This command is intended to be used in scripts.

## OPTIONS

  - \<NAME\>  
    Name of the projects for which the Git garbage collection should be
    run.

  - \--all  
    If specified the Git garbage collection is run for all projects
    sequentially.

  - \--show-progress  
    If specified progress information is shown.

  - \--aggressive  
    If an aggressive garbage collection should be done.

## EXAMPLES

Run the Git garbage collection for the projects *myProject* and
*yourProject*:

``` 
        $ ssh -p 29418 review.example.com gerrit gc myProject yourProject
        collecting garbage for "myProject":
        ...
        done.

        collecting garbage for "yourProject":
        ...
        done.
```

Run the Git garbage collection for all projects:

``` 
        $ ssh -p 29418 review.example.com gerrit gc --all
```

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

