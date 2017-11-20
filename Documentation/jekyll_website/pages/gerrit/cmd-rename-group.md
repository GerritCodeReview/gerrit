---
title: " gerrit rename-group"
sidebar: cmd_sidebar
permalink: cmd-rename-group.html
---
## NAME

gerrit rename-group - Rename an account group.

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit rename-group
>       <GROUP>
>       <NEWNAME>

## DESCRIPTION

Renames an account group.

## ACCESS

Caller must be a member of the group owning the group to be renamed or
be a member of the privileged *Administrators* group.

## SCRIPTING

This command is intended to be used in scripts.

## OPTIONS

  - \<GROUP\>  
    Required; name of the group to be renamed.

  - \<NEWNAME\>  
    Required; new name of the group.

## EXAMPLES

Rename the group "MyGroup" to
"MyCommitters".

``` 
        $ ssh -p 29418 user@review.example.com gerrit rename-group MyGroup MyCommitters
```

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

