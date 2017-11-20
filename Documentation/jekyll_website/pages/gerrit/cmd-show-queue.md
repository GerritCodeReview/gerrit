---
title: " gerrit show-queue"
sidebar: cmd_sidebar
permalink: cmd-show-queue.html
---
## NAME

gerrit show-queue - Display the background work queues, including
replication and indexing

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit show-queue
>     ssh -p <port> <host> ps

## DESCRIPTION

Presents a table of the pending activity the Gerrit daemon is currently
performing, or will perform in the near future. Gerrit contains an
internal scheduler, similar to cron, that it uses to queue and dispatch
both short and long term activity.

Tasks that are completed or canceled exit the queue very quickly once
they enter this state, but it can be possible to observe tasks in these
states.

## ACCESS

End-users may see a task in the queue only if they can also see the
project the task is associated with. Tasks operating on other projects,
or that do not have a specific project are hidden.

Members of the group *Administrators*, or any group that has been
granted [the *View Queue*
capability](access-control.html#capability_viewQueue) can see all queue
entries.

## SCRIPTING

Intended for interactive use only.

## OPTIONS

  - \--wide; -w  
    Do not format the output to the terminal width (default of 80
    columns).

  - \--by-queue; -q  
    Group tasks by queue and print queue info.

## DISPLAY

  - Task  
    Unique task identifier on this server. May be passed into
    [kill](cmd-kill.html) to cancel or terminate the task. Task
    identifiers have a period of 2^32-1, and start from a random value.

  - State  
    If running, blank.
    
    If the task has completed, but has not yet been reaped, *done*. If
    the task has been killed, but has not yet halted or been removed
    from the queue, *killed*.
    
    If the task is ready to execute but is waiting for an idle thread in
    its associated thread pool, *waiting*.
    
    Otherwise the time (local to the server) that this task will begin
    execution.

  - Command  
    Short text description of the task that will be performed at the
    given time.

## EXAMPLES

The following queue contains two tasks scheduled to replicate the
`tools/gerrit.git` project to two different remote systems, `dst1` and
`dst2`:

``` 
        $ ssh -p 29418 review.example.com gerrit show-queue
        Task     State                 Command
        ------------------------------------------------------------------------------
        7aae09b2 14:31:15.435          mirror dst1:/home/git/tools/gerrit.git
        9ad09d27 14:31:25.434          mirror dst2:/var/cache/tools/gerrit.git
        ------------------------------------------------------------------------------
          2 tasks
```

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

