---
title: " gerrit flush-caches"
sidebar: cmd_sidebar
permalink: cmd-flush-caches.html
---
## NAME

gerrit flush-caches - Flush some/all server caches from memory

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit flush-caches --all
>     ssh -p <port> <host> gerrit flush-caches --list
>     ssh -p <port> <host> gerrit flush-caches --cache <NAME> …

## DESCRIPTION

Clear an in-memory cache, forcing Gerrit to reconsult the ground truth
when it needs the information again.

Flushing a cache may be necessary if an administrator modifies database
records directly in the database, rather than going through the Gerrit
web interface.

If no options are supplied, defaults to `--all`.

## ACCESS

The caller must be a member of a group that is granted one of the
following capabilities:

  - [Flush Caches](access-control.html#capability_flushCaches) (any
    cache except "web\_sessions")

  - [Maintain Server](access-control.html#capability_maintainServer)
    (any cache including "web\_sessions")

  - [Administrate
    Server](access-control.html#capability_administrateServer) (any
    cache including "web\_sessions")

## SCRIPTING

This command is intended to be used in scripts.

## OPTIONS

  - \--all  
    Flush all known caches. This is like applying a big hammer, it will
    force everything out, potentially more than was necessary for the
    change made. This option automatically skips flushing potentially
    dangerous caches such as "web\_sessions". To flush one of these
    caches, the caller must specifically name them on the command line,
    e.g. pass `--cache web_sessions`.

  - \--list  
    Show a list of the caches.

  - \--cache \<NAME\>  
    Flush only the cache called \<NAME\>. May be supplied more than once
    to flush multiple caches in a single command execution.

## EXAMPLES

List caches available for flushing:

``` 
        $ ssh -p 29418 review.example.com gerrit flush-caches --list
        accounts
        diff
        groups
        ldap_groups
        openid
        projects
        sshkeys
        web_sessions
```

Flush all caches known to the server, forcing them to recompute:

``` 
        $ ssh -p 29418 review.example.com gerrit flush-caches --all
```

or

``` 
        $ ssh -p 29418 review.example.com gerrit flush-caches
```

Flush only the "sshkeys" cache, after manually editing an SSH key for a
user:

``` 
        $ ssh -p 29418 review.example.com gerrit flush-caches --cache sshkeys
```

Flush "web\_sessions", forcing all users to sign-in
again:

``` 
        $ ssh -p 29418 review.example.com gerrit flush-caches --cache web_sessions
```

## SEE ALSO

  - [gerrit show-caches](cmd-show-caches.html)

  - [Cache Configuration](config-gerrit.html#cache)

  - [Standard Caches](config-gerrit.html#cache_names)

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

