---
title: " gerrit stream-events"
sidebar: cmd_sidebar
permalink: cmd-stream-events.html
---
## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit stream-events

## DESCRIPTION

Provides a portal into the major events occurring on the server,
outputting activity data in real-time to the client. Events are filtered
by the caller’s access permissions, ensuring the caller only receives
events for changes they can view on the web, or in the project
repository.

Event output is in JSON, one event per line.

## ACCESS

Caller must be a member of the privileged *Administrators* group, or
have been granted [the *Stream Events* global
capability](access-control.html#capability_streamEvents).

## SCRIPTING

This command is intended to be used in scripts.

## OPTIONS

  - \--subscribe|-s  
    Type of the event to subscribe to. Multiple --subscribe options may
    be specified to subscribe to multiple events. When this option is
    provided, only subscribed events are emitted and all other events
    are ignored. When this option is omitted, all events are emitted.

## EXAMPLES

``` 
  $ ssh -p 29418 review.example.com gerrit stream-events
  {"type":"comment-added",change:{"project":"tools/gerrit", ...}, ...}
  {"type":"comment-added",change:{"project":"tools/gerrit", ...}, ...}
```

Only subscribe to specific event types:

``` 
  $ ssh -p 29418 review.example.com gerrit stream-events \
      -s patchset-created -s ref-replicated
```

## SCHEMA

The JSON messages consist of nested objects referencing the **change**,
**patchSet**, **account** involved, and other attributes as appropriate.

Note that any field may be missing in the JSON messages, so consumers of
this JSON stream should deal with that appropriately.

## EVENTS

### Assignee Changed

Sent when the assignee of a change has been modified.

  - type  
    "assignee-changed"

  - change  
    [change attribute](json.html#change)

  - changer  
    [account attribute](json.html#account)

  - oldAssignee  
    Assignee before it was changed.

  - eventCreatedOn  
    Time in seconds since the UNIX epoch when this event was created.

### Change Abandoned

Sent when a change has been abandoned.

  - type  
    "change-abandoned"

  - change  
    [change attribute](json.html#change)

  - patchSet  
    [patchSet attribute](json.html#patchSet)

  - abandoner  
    [account attribute](json.html#account)

  - reason  
    Reason for abandoning the change.

  - eventCreatedOn  
    Time in seconds since the UNIX epoch when this event was created.

### Change Merged

Sent when a change has been merged into the git repository.

  - type  
    "change-merged"

  - change  
    [change attribute](json.html#change)

  - patchSet  
    [patchSet attribute](json.html#patchSet)

  - submitter  
    [account attribute](json.html#account)

  - newRev  
    The resulting revision of the merge.

  - eventCreatedOn  
    Time in seconds since the UNIX epoch when this event was created.

### Change Restored

Sent when an abandoned change has been restored.

  - type  
    "change-restored"

  - change  
    [change attribute](json.html#change)

  - patchSet  
    [patchSet attribute](json.html#patchSet)

  - restorer  
    [account attribute](json.html#account)

  - reason  
    Reason for restoring the change.

  - eventCreatedOn  
    Time in seconds since the UNIX epoch when this event was created.

### Comment Added

Sent when a review comment has been posted on a change.

  - type  
    "comment-added"

  - change  
    [change attribute](json.html#change)

  - patchSet  
    [patchSet attribute](json.html#patchSet)

  - author  
    [account attribute](json.html#account)

  - approvals  
    All [approval attributes](json.html#approval) granted.

  - comment  
    Review comment cover message.

  - eventCreatedOn  
    Time in seconds since the UNIX epoch when this event was created.

### Dropped Output

Sent to notify a client that events have been dropped.

  - type  
    "dropped-output"

### Hashtags Changed

Sent when the hashtags have been added to or removed from a change.

  - type  
    "hashtags-changed"

  - change  
    [change attribute](json.html#change)

  - editor  
    [account attribute](json.html#account)

  - added  
    List of hashtags added to the change

  - removed  
    List of hashtags removed from the change

  - hashtags  
    List of hashtags on the change after tags were added or removed

  - eventCreatedOn  
    Time in seconds since the UNIX epoch when this event was created.

### Project Created

Sent when a new project has been created.

  - type  
    "project-created"

  - projectName  
    The created project name

  - projectHead  
    The created project head name

  - eventCreatedOn  
    Time in seconds since the UNIX epoch when this event was created.

### Patchset Created

Sent when a new change has been uploaded, or a new patch set has been
uploaded to an existing change.

  - type  
    "patchset-created"

  - change  
    [change attribute](json.html#change)

  - patchSet  
    [patchSet attribute](json.html#patchSet)

  - uploader  
    [account attribute](json.html#account)

  - eventCreatedOn  
    Time in seconds since the UNIX epoch when this event was created.

### Ref Updated

Sent when a reference is updated in a git repository.

  - type  
    "ref-updated"

  - submitter  
    [account attribute](json.html#account)

  - refUpdate  
    [refUpdate attribute](json.html#refUpdate)

  - eventCreatedOn  
    Time in seconds since the UNIX epoch when this event was created.

### Reviewer Added

Sent when a reviewer is added to a change.

  - type  
    "reviewer-added"

  - change  
    [change attribute](json.html#change)

  - patchSet  
    [patchSet attribute](json.html#patchSet)

  - reviewer  
    [account attribute](json.html#account)

  - eventCreatedOn  
    Time in seconds since the UNIX epoch when this event was created.

### Reviewer Deleted

Sent when a reviewer (with a vote) is removed from a change.

  - type  
    "reviewer-deleted"

  - change  
    [change attribute](json.html#change)

  - patchSet  
    [patchSet attribute](json.html#patchSet)

  - reviewer  
    reviewer that was removed as [account attribute](json.html#account)

  - remover  
    user that removed the reviewer as [account
    attribute](json.html#account)

  - approvals  
    All [approval attributes](json.html#approval) removed.

  - comment  
    Review comment cover message.

  - eventCreatedOn  
    Time in seconds since the UNIX epoch when this event was created.

### Topic Changed

Sent when the topic of a change has been changed.

  - type  
    "topic-changed"

  - change  
    [change attribute](json.html#change)

  - changer  
    [account attribute](json.html#account)

  - oldTopic  
    Topic name before it was changed.

  - eventCreatedOn  
    Time in seconds since the UNIX epoch when this event was created.

### Vote Deleted

Sent when a vote was removed from a change.

  - type  
    "vote-deleted"

  - change  
    [change attribute](json.html#change)

  - patchSet  
    [patchSet attribute](json.html#patchSet)

  - reviewer  
    user whose vote was removed as [account
    attribute](json.html#account)

  - remover  
    user who removed the vote as [account attribute](json.html#account)

  - approvals  
    all votes as [approval attributes](json.html#approval)

  - comment  
    Review comment cover message.

## SEE ALSO

  - [JSON Data Formats](json.html)

  - [Access Controls](access-control.html)

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

