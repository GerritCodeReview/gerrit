---
title: " multiple Change-Id lines in commit message footer"
sidebar: errors_sidebar
permalink: error-multiple-changeid-lines.html
---
With this error message Gerrit rejects to push a commit if the commit
message footer of the pushed commit contains several Change-Id lines.

You can see the commit messages for existing commits in the history by
doing a [git
log](http://www.kernel.org/pub/software/scm/git/docs/git-log.html).

If it was the intention to rework a change and to push a new patch set,
find the change in the Gerrit Web UI, copy its Change-Id line and use
the copied Change-Id line instead of the existing Change-Id lines in the
commit message of the commit for which the push is failing. How to do
this is explained
[here](error-push-fails-due-to-commit-message.html#commit_hook).

If it was the intention to create a new change in Gerrit simply remove
all Change-Id lines from the commit message of the commit for which the
push is failing. How to do this is explained
[here](error-push-fails-due-to-commit-message.html#commit_hook). In case
you have configured the [commit hook](cmd-hook-commit-msg.html) a new
Change-Id will be automatically generated and inserted.

## SEE ALSO

  - [Change-Id Lines](user-changeid.html)

## GERRIT

Part of [Gerrit Error Messages](error-messages.html)

## SEARCHBOX

