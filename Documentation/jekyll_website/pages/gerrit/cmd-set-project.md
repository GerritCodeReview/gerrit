---
title: " gerrit set-project"
sidebar: cmd_sidebar
permalink: cmd-set-project.html
---
## NAME

gerrit set-project - Change a project’s settings.

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit set-project
>       [--description <DESC> | -d <DESC>]
>       [--submit-type <TYPE> | -t <TYPE>]
>       [--contributor-agreements <true|false|inherit>]
>       [--signed-off-by <true|false|inherit>]
>       [--content-merge <true|false|inherit>]
>       [--change-id <true|false|inherit>]
>       [--project-state <STATE> | --ps <STATE>]
>       [--max-object-size-limit <N>]
>       <NAME>

## DESCRIPTION

Modifies a given project’s settings. This command can be useful to batch
change projects.

The command is argument-safe, that is, if no argument is given the
previous settings are kept intact.

## ACCESS

Caller must be an owner of the given project.

## SCRIPTING

This command is intended to be used in scripts.

## OPTIONS

  - \<NAME\>  
    Required; name of the project to edit. If name ends with `.git` the
    suffix will be automatically removed.

  - \--description; -d  
    New description of the project. If not specified, the old
    description is kept.
    
    Description values containing spaces should be quoted in single
    quotes ('). This most likely requires double quoting the value, for
    example `--description "'A description string'"`.

  - \--submit-type; -t  
    Action used by Gerrit to submit an approved change to its
    destination branch. Supported options are:
    
      - FAST\_FORWARD\_ONLY: produces a strictly linear history.
    
      - MERGE\_IF\_NECESSARY: create a merge commit when required.
    
      - REBASE\_IF\_NECESSARY: rebase the commit when required.
    
      - REBASE\_ALWAYS: always rebase the commit including dependencies.
    
      - MERGE\_ALWAYS: always create a merge commit.
    
      - CHERRY\_PICK: always cherry-pick the commit.
    
    For more details see [Submit
    Types](project-configuration.html#submit_type).

  - \--content-merge  
    If enabled, Gerrit will try to perform a 3-way merge of text file
    content when a file has been modified by both the destination branch
    and the change being submitted. This option only takes effect if
    submit type is not FAST\_FORWARD\_ONLY.

  - \--contributor-agreements  
    If enabled, authors must complete a contributor agreement on the
    site before pushing any commits or changes to this project.

  - \--signed-off-by  
    If enabled, each change must contain a Signed-off-by line from
    either the author or the uploader in the commit message.

  - \--change-id  
    Require a valid [Change-Id](user-changeid.html) footer in any commit
    uploaded for review. This does not apply to commits pushed directly
    to a branch or tag.

  - \--project-state; --ps  
    Set project’s visibility.
    
      - ACTIVE: project is regular and is the default value.
    
      - READ\_ONLY: users can see the project if read permission is
        granted, but all modification operations are disabled.
    
      - HIDDEN: the project is not visible for those who are not owners

  - \--max-object-size-limit  
    Define maximum Git object size for this project. Pushes containing
    an object larger than this limit will be rejected. This can be used
    to further limit the global
    [receive.maxObjectSizeLimit](config-gerrit.html#receive.maxObjectSizeLimit)
    and cannot be used to increase that globally set limit.
    
    Common unit suffixes of *k*, *m*, or *g* are supported.

## EXAMPLES

Change project `example` to be hidden, require change id, don’t use
content merge and use *merge if necessary* as merge
strategy:

``` 
    $ ssh -p 29418 review.example.com gerrit set-project example --submit-type MERGE_IF_NECESSARY\
    --change-id true --content-merge false --project-state HIDDEN
```

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

