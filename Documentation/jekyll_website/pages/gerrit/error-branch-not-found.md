---
title: " branch ... not found"
sidebar: errors_sidebar
permalink: error-branch-not-found.html
---
With this error message Gerrit rejects to push a commit for code review
if the specified target branch does not exist.

To push a change for code review the commit has to be pushed to the
project’s magical `refs/for/'branch'` ref (for details have a look at
[Create Changes](user-upload.html#push_create)). If you specify a
non-existing branch in the `refs/for/'branch'` ref the push fails with
the error message *branch … not found*.

To fix this problem verify

  - that the branch name in the push specification is typed correctly
    (case sensitive) and

  - that the branch really exists for this project (in the Gerrit Web UI
    go to *Projects* \> *List* and browse your project, then click on
    *Branches* to see all existing branches).

If it was your intention to create a new branch you can either

  - bypass code review on push as explained
    [here](user-upload.html#bypass_review) or

  - create the new branch in the Gerrit Web UI before pushing (go to
    *Projects* \> *List* and browse your project, in the *Branches* tab
    you can then create a new branch).

Please note that you need to be granted the [*Create
reference*](access-control.html#category_create) access to create new
branches.

## GERRIT

Part of [Gerrit Error Messages](error-messages.html)

## SEARCHBOX

