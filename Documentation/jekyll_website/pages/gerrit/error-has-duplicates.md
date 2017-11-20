---
title: " ... has duplicates"
sidebar: errors_sidebar
permalink: error-has-duplicates.html
---
With this error message Gerrit rejects to push a commit if its commit
message contains a Change-Id for which multiple changes can be found in
the project.

This error means that there is an inconsistency in Gerrit since for one
project there are multiple changes that have the same Change-Id. Every
change is expected to have an unique Change-Id.

Since this error should never occur in practice, you should inform your
Gerrit administrator if you hit this problem and/or [open a Gerrit
issue](https://bugs.chromium.org/p/gerrit/issues/list).

In any case to not be blocked with your work, you can simply create a
new Change-Id for your commit and then push it as new change to Gerrit.
How to exchange the Change-Id in the commit message of your commit is
explained [here](error-push-fails-due-to-commit-message.html).

## GERRIT

Part of [Gerrit Error Messages](error-messages.html)

## SEARCHBOX

