---
title: " gerrit close-connection"
sidebar: cmd_sidebar
permalink: cmd-close-connection.html
---
## NAME

gerrit close-connection - Close the specified SSH connection

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit close-connection <SESSION_ID>
>        [--wait]

## DESCRIPTION

Close an SSH connection.

The connection closing is done asynchronously by default. Use `--wait`
option to wait for connection to close.

An error message will be displayed if no connection with the specified
session ID is found.

## ACCESS

Caller must be a member of the privileged *Administrators* group.

## SCRIPTING

Intended for interactive use only.

## OPTIONS

`--wait` : Wait for connection to close before exiting.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

