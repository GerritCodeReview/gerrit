---
title: " kill"
sidebar: cmd_sidebar
permalink: cmd-kill.html
---
## NAME

kill - Cancel or abort a background task

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> kill <ID> …

## DESCRIPTION

Cancels a scheduled task from the queue. If the task has already been
started, requests for the task to cancel as soon as it reaches its next
cancellation point (which is usually blocking IO).

## ACCESS

Caller must be a member of the privileged *Administrators* group, or
have been granted [the *Kill Task* global
capability](access-control.html#capability_kill).

## SCRIPTING

Intended for interactive use only.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

