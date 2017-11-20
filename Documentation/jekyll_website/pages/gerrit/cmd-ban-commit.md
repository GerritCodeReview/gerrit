---
title: " gerrit ban-commit"
sidebar: cmd_sidebar
permalink: cmd-ban-commit.html
---
## NAME

gerrit ban-commit - Bans a commit from a project’s repository.

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit ban-commit
>       [--reason <REASON>]
>       <PROJECT>
>       <COMMIT> …

## DESCRIPTION

Marks a commit as banned for the specified repository. If a commit is
banned Gerrit rejects every push that includes this commit with
[contains banned commit …](error-contains-banned-commit.html).

> **Note**
> 
> This command just marks the commit as banned, but it does not remove
> the commit from the history of any central branch. This needs to be
> done manually.

## ACCESS

Caller must be owner of the project or be a member of the privileged
*Administrators* group.

## SCRIPTING

This command is intended to be used in scripts.

## OPTIONS

  - \<PROJECT\>  
    Required; name of the project for which the commit should be banned.

  - \<COMMIT\>  
    Required; commit(s) that should be banned.

  - \--reason  
    Reason for banning the commit.

## EXAMPLES

Ban commit `421919d015c062fd28901fe144a78a555d0b5984` from project
`myproject`:

``` 
        $ ssh -p 29418 review.example.com gerrit ban-commit myproject \
        421919d015c062fd28901fe144a78a555d0b5984
```

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

