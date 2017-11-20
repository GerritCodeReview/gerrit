---
title: " commit already exists"
sidebar: errors_sidebar
permalink: error-commit-already-exists.html
---
With "commit already exists (as current patchset)" or "commit already
exists (in the change)" error message Gerrit rejects to push a commit to
an existing change via `refs/changes/n` if the commit was already
successfully pushed to the change.

With "commit already exists (in the project)" error message Gerrit
rejects to push a commit to an existing change via `refs/changes/n` if
the commit was already successfully pushed to a change in project scope.

In any above case there is no new commit and consequently there is
nothing for Gerrit to do.

For further information about how to resolve this error, please refer to
[no new changes](error-no-new-changes.html).

## GERRIT

Part of [Gerrit Error Messages](error-messages.html)

## SEARCHBOX

