---
title: " Gerrit Code Review - JSON Data"
sidebar: gerritdoc_sidebar
permalink: json.html
---
Some commands produce JSON data streams intended for other applications
to consume. The structures are documented below. Note that any field may
be missing in the JSON messages, so consumers of this JSON stream should
deal with that appropriately.

## change

The Gerrit change being reviewed, or that was already reviewed.

  - project  
    Project path in Gerrit.

  - branch  
    Branch name within project.

  - topic  
    Topic name specified by the uploader for this change series.

  - id  
    Change identifier, as scraped out of the Change-Id field in the
    commit message, or as assigned by the server if it was missing.

  - number  
    Change number (deprecated).

  - subject  
    Description of change.

  - owner  
    Owner in [account attribute](#account).

  - url  
    Canonical URL to reach this change.

  - commitMessage  
    The full commit message for the change’s current patch set.

  - createdOn  
    Time in seconds since the UNIX epoch when this change was created.

  - lastUpdated  
    Time in seconds since the UNIX epoch when this change was last
    updated.

  - open  
    Boolean indicating if the change is still open for review.

  - status  
    Current state of this change.
    
      - NEW  
        Change is still being reviewed.
    
      - MERGED  
        Change has been merged to its branch.
    
      - ABANDONED  
        Change was abandoned by its owner or administrator.

  - comments  
    All inline/file comments for this change in [message
    attributes](#message).

  - trackingIds  
    Issue tracking system links in [trackingid attributes](#trackingid),
    scraped out of the commit message based on the server’s
    [trackingid](config-gerrit.html#trackingid) sections.

  - currentPatchSet  
    Current [patchSet attribute](#patchSet).

  - patchSets  
    All [patchSet attributes](#patchSet) for this change.

  - dependsOn  
    List of changes that this change depends on in [dependency
    attributes](#dependency).

  - neededBy  
    List of changes that depend on this change in [dependency
    attributes](#dependency).

  - submitRecords  
    The [submitRecord attribute](#submitRecord) contains information
    about whether this change has been or can be submitted.

  - allReviewers  
    List of all reviewers in [account attribute](#account) which are
    added to a change.

## trackingid

A link to an issue tracking system.

  - system  
    Name of the system. This comes straight from the gerrit.config file.

  - id  
    Id number as scraped out of the commit message.

## account

A user account.

  - name  
    User’s full name, if configured.

  - email  
    User’s preferred email address.

  - username  
    User’s username, if configured.

## patchSet

Refers to a specific patchset within a [change](#change).

  - number  
    The patchset number.

  - revision  
    Git commit for this patchset.

  - parents  
    List of parent revisions.

  - ref  
    Git reference pointing at the revision. This reference is available
    through the Gerrit Code Review server’s Git interface for the
    containing change.

  - uploader  
    Uploader of the patch set in [account attribute](#account).

  - author  
    Author of this patchset in [account attribute](#account).

  - createdOn  
    Time in seconds since the UNIX epoch when this patchset was created.

  - kind  
    Kind of change uploaded.
    
      - REWORK  
        Nontrivial content changes.
    
      - TRIVIAL\_REBASE  
        Conflict-free merge between the new parent and the prior patch
        set.
    
      - MERGE\_FIRST\_PARENT\_UPDATE  
        Conflict-free change of first (left) parent of a merge commit.
    
      - NO\_CODE\_CHANGE  
        No code changed; same tree and same parent tree.
    
      - NO\_CHANGE  
        No changes; same commit message, same tree and same parent tree.

  - approvals  
    The [approval attribute](#approval) granted.

  - comments  
    All comments for this patchset in [patchsetComment
    attributes](#patchsetcomment).

  - files  
    All changed files in this patchset in [file attributes](#file).

  - sizeInsertions  
    Size information of insertions of this patchset.

  - sizeDeletions  
    Size information of deletions of this patchset.

## approval

Records the code review approval granted to a patch set.

  - type  
    Internal name of the approval given.

  - description  
    Human readable category of the approval.

  - value  
    Value assigned by the approval, usually a numerical score.

  - oldValue  
    The previous approval score, only present if the value changed as a
    result of this event.

  - grantedOn  
    Time in seconds since the UNIX epoch when this approval was added or
    last updated.

  - by  
    Reviewer of the patch set in [account attribute](#account).

## refUpdate

Information about a ref that was updated.

  - oldRev  
    The old value of the ref, prior to the update.

  - newRev  
    The new value the ref was updated to.

  - refName  
    Full ref name within project.

  - project  
    Project path in Gerrit.

## submitRecord

Information about the submit status of a change.

  - status  
    Current submit status.
    
      - OK  
        The change is ready for submission or already submitted.
    
      - NOT\_READY  
        The change is missing a required label.
    
      - RULE\_ERROR  
        An internal server error occurred preventing computation.

  - labels  
    This describes the state of each code review [label
    attribute](#label), unless the status is RULE\_ERROR.

## label

Information about a code review label for a change.

  - label  
    The name of the label.

  - status  
    The status of the label.
    
      - OK  
        This label provides what is necessary for submission.
    
      - REJECT  
        This label prevents the change from being submitted.
    
      - NEED  
        The label is required for submission, but has not been
        satisfied.
    
      - MAY  
        The label may be set, but it’s neither necessary for submission
        nor does it block submission if set.
    
      - IMPOSSIBLE  
        The label is required for submission, but is impossible to
        complete. The likely cause is access has not been granted
        correctly by the project owner or site administrator.

  - by  
    The [account](#account) that applied the label.

## dependency

Information about a change or patchset dependency.

  - id  
    Change identifier.

  - number  
    Change number.

  - revision  
    Patchset revision.

  - ref  
    Ref name.

  - isCurrentPatchSet  
    If the revision is the current patchset of the change.

## message

Comment added on a change by a reviewer.

  - timestamp  
    Time in seconds since the UNIX epoch when this comment was added.

  - reviewer  
    The [account](#account) that added the comment.

  - message  
    The comment text.

## patchsetComment

Comment added on a patchset by a reviewer.

  - file  
    The name of the file on which the comment was added.

  - line  
    The line number at which the comment was added.

  - reviewer  
    The [account](#account) that added the comment.

  - message  
    The comment text.

## file

Information about a patch on a file.

  - file  
    The name of the file. If the file is renamed, the new name.

  - fileOld  
    The old name of the file, if the file is renamed.

  - type  
    The type of change.
    
      - ADDED  
        The file is being created/introduced by this patch.
    
      - MODIFIED  
        The file already exists, and has updated content.
    
      - DELETED  
        The file existed, but is being removed by this patch.
    
      - RENAMED  
        The file is renamed.
    
      - COPIED  
        The file is copied from another file.
    
      - REWRITE  
        Sufficient amount of content changed to claim the file was
        rewritten.

  - insertions  
    number of insertions of this patch.

  - deletions  
    number of deletions of this patch.

## SEE ALSO

  - [gerrit stream-events](cmd-stream-events.html)

  - [gerrit query](cmd-query.html)

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

