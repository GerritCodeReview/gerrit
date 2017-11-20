---
title: " Upload denied for project ..."
sidebar: errors_sidebar
permalink: error-upload-denied.html
---
With this error message Gerrit rejects to push a commit if the pushing
user has no upload permissions for the project to which the push was
done.

There are two possibilities how to continue in this situation:

1.  contact one of the project owners and request upload permissions for
    the project (access right
    [*Push*](access-control.html#category_push))

2.  export your commit as a patch using the [git
    format-patch](http://www.kernel.org/pub/software/scm/git/docs/git-format-patch.html)
    command and provide the patch file to one of the project owners

## GERRIT

Part of [Gerrit Error Messages](error-messages.html)

## SEARCHBOX

