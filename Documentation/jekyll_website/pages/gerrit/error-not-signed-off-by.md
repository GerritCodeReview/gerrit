---
title: " not Signed-off-by author/committer/uploader in commit message footer"
sidebar: errors_sidebar
permalink: error-not-signed-off-by.html
---
Projects in Gerrit can be configured to require a
[Signed-off-by](user-signedoffby.html#Signed-off-by) in the footer of
the commit message to enforce that every change is signed by the author,
committer or uploader. If for a project a Signed-off-by is required and
the commit message footer does not contain it, Gerrit rejects to push
the commit with this error message.

This policy can be bypassed by having the access right [*Forge
Committer*](access-control.html#category_forge_committer).

This error may happen for different reasons if you do not have the
access right to forge the committer identity:

1.  missing Signed-off-by in the commit message footer

2.  Signed-off-by is contained in the commit message footer but it’s
    neither from the author, committer nor uploader

3.  Signed-off-by from the author, committer or uploader is contained in
    the commit message but not in the footer (last paragraph)

To be able to push your commits you have to update the commit messages
as explained [here](error-push-fails-due-to-commit-message.html) so that
they contain a Signed-off-by from the author, committer or uploader in
the last paragraph. However it is important that you only add a
Signed-off-by if you understand the semantics of the
[Signed-off-by](user-signedoffby.html#Signed-off-by) and the commit
applies to the rules that are connected with this footer.

## GERRIT

Part of [Gerrit Error Messages](error-messages.html)

## SEARCHBOX

