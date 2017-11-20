---
title: " Project not found: ..."
sidebar: errors_sidebar
permalink: error-project-not-found.html
---
With this error message Gerrit rejects to push a commit if the git
repository to which the push is done does not exist as a project in the
Gerrit server or if the pushing user has no read access for this
project.

The name of the project in Gerrit has the same name as the path of its
git repository (excluding the *.git* extension).

If you are facing this problem, do the following:

1.  Verify that the project name specified as git repository in the push
    command is typed correctly (case sensitive).

2.  Verify that you are pushing to the correct Gerrit server.

3.  Go in the Gerrit Web UI to *Projects* \> *List* and check that the
    project is listed. If the project is not listed the project either
    does not exist or you don’t have
    [*Read*](access-control.html#category_read) access for it. This
    means if you are certain that the project name is right you should
    contact the Gerrit Administrator or project owner to request access
    to the project.

This error message might be misleading if the project actually exists
but the push is failing because the pushing user has no read access for
the project. The reason that Gerrit in this case denies the existence of
the project is to prevent users from probing the Gerrit server to see if
a particular project exists.

## GERRIT

Part of [Gerrit Error Messages](error-messages.html)

## SEARCHBOX

