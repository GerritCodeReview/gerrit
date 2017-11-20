---
title: " missing Change-Id in commit message footer"
sidebar: errors_sidebar
permalink: error-missing-changeid.html
---
With this error message Gerrit rejects to push a commit to a project
which is configured to always require a Change-Id in the commit message
if the commit message of the pushed commit does not contain a Change-Id
in the footer (the last paragraph).

This error may happen for different reasons:

1.  missing Change-Id in the commit message

2.  Change-Id is contained in the commit message but not in the last
    paragraph

You can see the commit messages for existing commits in the history by
doing a [git
log](http://www.kernel.org/pub/software/scm/git/docs/git-log.html).

To avoid this error you should use the [commit
hook](cmd-hook-commit-msg.html) or EGit to automatically create and
insert a unique Change-Id into the commit message on every commit.

## Missing Change-Id in the commit message

If the commit message of a commit that you want to push does not contain
a Change-Id you have to update its commit message and insert a
Change-Id.

If you want to upload a new change to Gerrit make sure that you have
configured your environment so that a unique Change-Id is automatically
created and inserted on every commit as explained above. Now you can
rewrite the commits for which the Change-Ids are missing and the
Change-Ids will be automatically created and inserted into the commit
messages. This is explained
[here](error-push-fails-due-to-commit-message.html#commit_hook).

If you want to update an existing change in Gerrit by uploading a new
patch set you should copy its Change-Id from the Gerrit Web UI and
insert it into the commit message. How to update the commit message is
explained
[here](error-push-fails-due-to-commit-message.html).

## Change-Id is contained in the commit message but not in the last paragraph

To be picked up by Gerrit, a Change-Id must be in the last paragraph of
a commit message, for details, see [Change-Id
Lines](user-changeid.html).

If the Change-Id is contained in the commit message but not in its last
paragraph you have to update the commit message and move the Change-Id
into the last paragraph. How to update the commit message is explained
[here](error-push-fails-due-to-commit-message.html).

## GERRIT

Part of [Gerrit Error Messages](error-messages.html)

## SEARCHBOX

