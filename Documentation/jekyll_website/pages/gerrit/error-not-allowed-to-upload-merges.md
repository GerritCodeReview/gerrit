---
title: " you are not allowed to upload merges"
sidebar: errors_sidebar
permalink: error-not-allowed-to-upload-merges.html
---
With this error message Gerrit rejects to push a merge commit if the
pushing user has no permission to upload merge commits for the project
to which the push is done.

If you need to upload merge commits, you can contact one of the project
owners and request permission to upload merge commits (access right
[*Push Merge Commit*](access-control.html#category_push_merge)) for this
project.

If one of your changes could not be merged in Gerrit due to conflicts
and you created the merge commit to resolve the conflicts, you might
want to revert the merge and instead of this do a
[rebase](http://www.kernel.org/pub/software/scm/git/docs/git-rebase.html).

## GERRIT

Part of [Gerrit Error Messages](error-messages.html)

## SEARCHBOX

