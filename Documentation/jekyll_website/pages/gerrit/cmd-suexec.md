---
title: " suexec"
sidebar: cmd_sidebar
permalink: cmd-suexec.html
---
## NAME

suexec - Execute a command as any registered user account

## SYNOPSIS

> 
> 
>     ssh -p <port>
>       -i SITE_PATH/etc/ssh_host_rsa_key
>       "Gerrit Code Review@localhost"
>       suexec
>       --as <EMAIL>
>       [--from HOST:PORT]
>       [--]
>       [COMMAND]

## DESCRIPTION

The suexec command permits executing any other command as any other
registered user account.

suexec can only be invoked by the magic user `Gerrit Code Review`, or
any user granted granted the [Run
As](access-control.html#capability_runAs) capability. The run as
capability is permitted to be used only if
[auth.enableRunAs](config-gerrit.html) is true.

## OPTIONS

  - \--as  
    Email address of the user you want to impersonate.

  - \--from  
    Hostname and port of the machine you want to impersonate the command
    coming from.

  - COMMAND  
    Gerrit command you want to run.

## ACCESS

Caller must be the magic user Gerrit Code Review using the SSH daemon’s
host key, or a key on this daemon’s peer host key ring, or a user
granted the Run As capability.

## SCRIPTING

This command is intended to be used in scripts.

## EXAMPLES

Approve the change with commit c0ff33 as "Verified +1" as user
<bob@example.com>

``` 
  $ sudo -u gerrit ssh -p 29418 \
    -i site_path/etc/ssh_host_rsa_key \
    "Gerrit Code Review@localhost" \
    suexec \
    --as bob@example.com \
    -- \
    gerrit approve --verified +1 c0ff33
```

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

