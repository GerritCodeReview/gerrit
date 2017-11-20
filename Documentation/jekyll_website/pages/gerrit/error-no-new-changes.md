---
title: " no new changes"
sidebar: errors_sidebar
permalink: error-no-new-changes.html
---
With this error message Gerrit rejects to push a commit if the pushed
commit was already successfully pushed to Gerrit in project scope. In
this case there is no new change and consequently there is nothing for
Gerrit to do.

If your push is failing with this error message, you normally don’t have
to do anything since the commit was already successfully pushed. Still
this error message may sometimes come as a surprise if you expected a
new commit to be pushed. In this case you should verify that:

1.  your changes were successfully committed locally (otherwise there is
    no new commit which can be pushed)

2.  you are pushing the correct commit (e.g. if you are pushing HEAD
    make sure you have locally checked out the correct branch)

If you are sure you are pushing the correct commit and you are still
getting the "no new changes" error unexpectedly you can take the commit
ID and search for the corresponding change in Gerrit. To do this simply
paste the commit ID in the Gerrit Web UI into the search field. Details
about how to search in Gerrit are explained [here](user-search.html).

Please note that each commit can really be pushed only once. This means:

1.  you cannot push a commit again even if the change for which the
    commit was pushed before was abandoned (but you may restore the
    abandoned change)

2.  you cannot reset a change to an old patch set by pushing the old
    commit for this change again

3.  if a commit was pushed to one branch you cannot push this commit to
    another branch in project scope.

4.  if a commit was pushed directly to a branch (without going through
    code review) you cannot push this commit once again for code review
    (please note that in this case searching by the commit ID in the
    Gerrit Web UI will not find any change)

If you need to re-push a commit you may rewrite this commit by
[amending](http://www.kernel.org/pub/software/scm/git/docs/git-commit.html)
it or doing an interactive [git
rebase](http://www.kernel.org/pub/software/scm/git/docs/git-rebase.html).
By rewriting the commit you actually create a new commit (with a new
commit ID in project scope) which can then be pushed to Gerrit. If the
old commit contains a Change-Id in the commit message you also need to
replace it with a new Change-Id (case 1. and 3. above), otherwise the
push will fail with another error message.

## Fast-forward merges

You will also encounter this error if you did a Fast-forward merge and
try to push the result. A workaround is to use the [Selecting Merge
Base](user-upload.html#base) feature or enable the [Use target branch
when determining new changes to
open](project-configuration.html#_use_target_branch_when_determining_new_changes_to_open)
configuration.

## GERRIT

Part of [Gerrit Error Messages](error-messages.html)

## SEARCHBOX

