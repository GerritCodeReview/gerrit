---
title: " Gerrit Code Review - Command Line Tools"
sidebar: cmd_sidebar
permalink: cmd-index.html
---
## Client

Client commands and hooks can be downloaded via scp, wget or curl from
Gerrit’s daemon, and then executed on the client system.

To download a client command or hook, use scp or an http
client:

``` 
  $ scp -p -P 29418 john.doe@review.example.com:bin/gerrit-cherry-pick ~/bin/
  $ scp -p -P 29418 john.doe@review.example.com:hooks/commit-msg .git/hooks/

  $ curl -Lo ~/bin/gerrit-cherry-pick http://review.example.com/tools/bin/gerrit-cherry-pick
  $ curl -Lo .git/hooks/commit-msg http://review.example.com/tools/hooks/commit-msg
```

For more details on how to determine the correct SSH port number, see
[Testing Your SSH Connection](user-upload.html#test_ssh).

### Commands

  - [gerrit-cherry-pick](cmd-cherry-pick.html)  
    Download and cherry-pick one or more changes (commits).

### Hooks

Client hooks can be installed into a local Git repository, improving the
developer experience when working with a Gerrit Code Review server.

  - [commit-msg](cmd-hook-commit-msg.html)  
    Automatically generate \`Change-Id: \` tags in commit messages.

## Server

Aside from the standard Git server side actions, Gerrit supports several
other commands over its internal SSH daemon. As Gerrit does not provide
an interactive shell, the commands must be triggered from an ssh client,
for example:

``` 
  $ ssh -p 29418 review.example.com gerrit ls-projects
```

For more details on how to determine the correct SSH port number, see
[Testing Your SSH Connection](user-upload.html#test_ssh).

### User Commands

  - [gerrit apropos](cmd-apropos.html)  
    Search Gerrit documentation index.

  - [gerrit ban-commit](cmd-ban-commit.html)  
    Bans a commit from a project’s repository.

  - [gerrit create-branch](cmd-create-branch.html)  
    Create a new project branch.

  - [gerrit ls-groups](cmd-ls-groups.html)  
    List groups visible to the caller.

  - [gerrit ls-members](cmd-ls-members.html)  
    List the membership of a group visible to the caller.

  - [gerrit ls-projects](cmd-ls-projects.html)  
    List projects visible to the caller.

  - [gerrit query](cmd-query.html)  
    Query the change database.

  - *gerrit receive-pack*  
    *Deprecated alias for `git receive-pack`.*

  - [gerrit rename-group](cmd-rename-group.html)  
    Rename an account group.

  - [gerrit review](cmd-review.html)  
    Verify, approve and/or submit a patch set from the command line.

  - [gerrit set-head](cmd-set-head.html)  
    Change the HEAD reference of a project.

  - [gerrit set-project](cmd-set-project.html)  
    Change a project’s settings.

  - [gerrit set-reviewers](cmd-set-reviewers.html)  
    Add or remove reviewers on a change.

  - [gerrit stream-events](cmd-stream-events.html)  
    Monitor events occurring in real time.

  - [gerrit version](cmd-version.html)  
    Show the currently executing version of Gerrit.

  - [git receive-pack](cmd-receive-pack.html)  
    Standard Git server side command for client side `git push`.
    
    Also implements the magic associated with uploading commits for
    review. See [Creating Changes](user-upload.html#push_create).

  - git upload-pack  
    Standard Git server side command for client side `git fetch`.

### Administrator Commands

  - [gerrit close-connection](cmd-close-connection.html)  
    Close the specified SSH connection.

  - [gerrit create-account](cmd-create-account.html)  
    Create a new user account.

  - [gerrit create-group](cmd-create-group.html)  
    Create a new account group.

  - [gerrit create-project](cmd-create-project.html)  
    Create a new project and associated Git repository.

  - [gerrit flush-caches](cmd-flush-caches.html)  
    Flush some/all server caches from memory.

  - [gerrit gc](cmd-gc.html)  
    Run the Git garbage collection.

  - [gerrit gsql](cmd-gsql.html)  
    Administrative interface to active database.

  - [gerrit index activate](cmd-index-activate.html)  
    Activate the latest index version available.

  - [gerrit index start](cmd-index-start.html)  
    Start the online indexer.

  - [gerrit index changes](cmd-index-changes.html)  
    Index one or more changes.

  - [gerrit index project](cmd-index-project.html)  
    Index all the changes in one or more projects.

  - [gerrit logging ls-level](cmd-logging-ls-level.html)  
    List loggers and their logging level.

  - [gerrit logging set-level](cmd-logging-set-level.html)  
    Set the logging level of loggers.

  - [gerrit ls-user-refs](cmd-ls-user-refs.html)  
    Lists refs visible for a specified user.

  - [gerrit plugin add](cmd-plugin-install.html)  
    Alias for *gerrit plugin install*.

  - [gerrit plugin enable](cmd-plugin-enable.html)  
    Enable plugins.

  - [gerrit plugin install](cmd-plugin-install.html)  
    Install/Add a plugin.

  - [gerrit plugin ls](cmd-plugin-ls.html)  
    List the installed plugins.

  - [gerrit plugin reload](cmd-plugin-reload.html)  
    Reload/Restart plugins.

  - [gerrit plugin remove](cmd-plugin-remove.html)  
    Disable plugins.

  - [gerrit plugin rm](cmd-plugin-remove.html)  
    Alias for *gerrit plugin remove*.

  - [gerrit set-account](cmd-set-account.html)  
    Change an account’s settings.

  - [gerrit set-members](cmd-set-members.html)  
    Set group members.

  - [gerrit set-project-parent](cmd-set-project-parent.html)  
    Change the project permissions are inherited from.

  - [gerrit show-caches](cmd-show-caches.html)  
    Display current cache statistics.

  - [gerrit show-connections](cmd-show-connections.html)  
    Display active client SSH connections.

  - [gerrit show-queue](cmd-show-queue.html)  
    Display the background work queues, including replication.

  - [gerrit test-submit rule](cmd-test-submit-rule.html)  
    Test prolog submit rules.

  - [gerrit test-submit type](cmd-test-submit-type.html)  
    Test prolog submit type.

  - [kill](cmd-kill.html)  
    Kills a scheduled or running task.

  - [ps](cmd-show-queue.html)  
    Alias for *gerrit show-queue*.

  - [suexec](cmd-suexec.html)  
    Execute a command as any registered user account.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

