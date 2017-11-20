---
title: " gerrit create-group"
sidebar: cmd_sidebar
permalink: cmd-create-group.html
---
## NAME

gerrit create-group - Create a new account group.

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit create-group
>       [--owner <GROUP> | -o <GROUP>]
>       [--description <DESC> | -d <DESC>]
>       [--member <USERNAME>]
>       [--group <GROUP>]
>       [--visible-to-all]
>       <GROUP>

## DESCRIPTION

Creates a new account group. The group creating user (the user that
fired the create-group command) is not automatically added to the
created group. In case the creating user wants to be a member of the
group he/she must list itself in the --member option. This is slightly
different from Gerrit’s Web UI where the creating user automatically
becomes a member of the newly created group.

## ACCESS

Caller must be a member of the privileged *Administrators* group, or
have been granted [the *Create Group* global
capability](access-control.html#capability_createGroup).

## SCRIPTING

This command is intended to be used in scripts.

## OPTIONS

  - \<GROUP\>  
    Required; name of the new group.

  - \--owner, -o  
    Name of the owning group. If not specified the group will be
    self-owning.

  - \--description, -d  
    Description of group.
    
    Description values containing spaces should be quoted in single
    quotes ('). This most likely requires double quoting the value, for
    example `--description "'A description string'"`.

  - \--member  
    User name to become initial member of the group. Multiple --member
    options may be specified to add more initial members.
    
    Trying to add a user that doesn’t have an account in Gerrit fails,
    unless LDAP is used for authentication. If LDAP is used for
    authentication and the user is not found, Gerrit tries to
    authenticate the user against the LDAP backend. If the
    authentication is successful a user account is automatically
    created, so that the user can be added to the group.

  - \--group  
    Group name to include in the group. Multiple --group options may be
    specified to include more initial groups.

  - \--visible-to-all  
    If specified, the group members will be visible to all users.

## EXAMPLES

Create a new account group called `gerritdev` with two initial members
`developer1` and `developer2`. The group should be owned by
itself:

``` 
        $ ssh -p 29418 user@review.example.com gerrit create-group --member developer1 --member developer2 gerritdev
```

Create a new account group called `Foo` owned by the `Foo-admin` group.
Put `developer1` as the initial member and include group
description:

``` 
        $ ssh -p 29418 user@review.example.com gerrit create-group --owner Foo-admin --member developer1 --description "'Foo description'" Foo
```

Note that it is necessary to quote the description twice. The local
shell needs double quotes around the value to ensure the single quotes
are passed through SSH as-is to the remote Gerrit server, which uses the
single quotes to delimit the value.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

