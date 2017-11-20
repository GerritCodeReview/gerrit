---
title: " invalid Change-Id line format in commit message footer"
sidebar: errors_sidebar
permalink: error-invalid-changeid-line.html
---
With this error message Gerrit rejects to push a commit if its commit
message footer contains an invalid Change-Id line.

You can see the commit messages for existing commits in the history by
doing a [git
log](http://www.kernel.org/pub/software/scm/git/docs/git-log.html).

If it was the intention to rework a change and to push a new patch set,
find the change in the Gerrit Web UI, copy its Change-Id line and use it
to correct the invalid Change-Id line in the commit message of the
commit for which the push is failing. How to do this is explained
[here](error-push-fails-due-to-commit-message.html#commit_hook).

If it was the intention to create a new change in Gerrit simply remove
the invalid Change-Id line from the commit message of the commit for
which the push is failing. How to do this is explained
[here](error-push-fails-due-to-commit-message.html#commit_hook). In case
you have configured the [commit hook](cmd-hook-commit-msg.html) a new
valid Change-Id will be automatically generated and inserted.

## SEE ALSO

  - [Change-Id Lines](user-changeid.html)

## GERRIT

Part of [Gerrit Error Messages](error-messages.html)

## SEARCHBOX

