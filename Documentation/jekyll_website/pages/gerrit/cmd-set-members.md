---
title: " gerrit set-members"
sidebar: cmd_sidebar
permalink: cmd-set-members.html
---
## NAME

gerrit set-members - Set group members

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit set-members
>       [--add USER …]
>       [--remove USER …]
>       [--include GROUP …]
>       [--exclude GROUP …]
>       [--]
>       <GROUP> …

## DESCRIPTION

Set the group members for the specified groups.

## OPTIONS

  - \<GROUP\>  
    Required; name of the group for which the members should be set. The
    members for multiple groups can be set at once by specifying
    multiple groups.

  - \--add; -a  
    A user that should be added to the specified groups. Multiple users
    can be added at once by using this option multiple times.

  - \--remove; -r  
    Remove this user from the specified groups. Multiple users can be
    removed at once by using this option multiple times.

  - \--include; -i  
    A group that should be included to the specified groups. Multiple
    groups can be included at once by using this option multiple times.

  - \--exclude; -e  
    Exclude this group from the specified groups. Multiple groups can be
    excluded at once by using this option multiple times.

The `set-members` command is processing the options in the following
order: `--remove`, `--exclude`, `--add`, `--include`

## ACCESS

Any user who has SSH access to Gerrit.

## SCRIPTING

This command is intended to be used in scripts.

## EXAMPLES

Add alice and bob, but remove eve from the groups my-committers and
my-verifiers.

``` 
        $ ssh -p 29418 review.example.com gerrit set-members \
          -a alice@example.com -a bob@example.com \
          -r eve@example.com my-committers my-verifiers
```

Include the group my-friends into the group my-committers, but exclude
the included group my-testers from the group my-committers.

``` 
        $ ssh -p 29418 review.example.com gerrit set-members \
          -i my-friends -e my-testers my-committers
```

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

