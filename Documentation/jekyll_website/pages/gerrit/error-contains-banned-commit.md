---
title: " contains banned commit ..."
sidebar: errors_sidebar
permalink: error-contains-banned-commit.html
---
With this error message Gerrit rejects to push a commit that is banned
or that would merge in an ancestor that is banned.

If a commit was identified as a bad commit (e.g. because it contains
coding that violates intellectual property) and because of this it was
removed from the central git repository it can be marked as banned.
Gerrit will then prevent that this commit ever enters the repository
again by rejecting every push of such a commit with the error message
"contains banned commit …".

If you have commits that you want to push that are based on a banned
commit you may want to
[cherry-pick](http://www.kernel.org/pub/software/scm/git/docs/git-cherry-pick.html)
them onto a clean base and push them again.

## GERRIT

Part of [Gerrit Error Messages](error-messages.html)

## SEARCHBOX

