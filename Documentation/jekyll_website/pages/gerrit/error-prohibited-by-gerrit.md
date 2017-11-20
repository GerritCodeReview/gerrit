---
title: " prohibited by Gerrit"
sidebar: errors_sidebar
permalink: error-prohibited-by-gerrit.html
---
This is a general error message that is returned by Gerrit if a push is
not allowed, e.g. because the pushing user has no sufficient privileges.

In particular this error occurs:

1.  if you push a commit for code review to a branch for which you don’t
    have upload permissions (access right
    [*Push*](access-control.html#category_push_review) on
    `+refs/for/refs/heads/*+`)

2.  if you bypass code review without
    [*Push*](access-control.html#category_push_direct) access right on
    `+refs/heads/*+`

3.  if you bypass code review pushing to a non-existing branch without
    [*Create Reference*](access-control.html#category_create) access
    right on `+refs/heads/*+`

4.  if you push an annotated tag without [*Create Annotated
    Tag*](access-control.html#category_create_annotated) access right on
    `+refs/tags/*+`

5.  if you push a signed tag without [*Create Signed
    Tag*](access-control.html#category_create_signed) access right on
    `+refs/tags/*+`

6.  if you push a lightweight tag without the access right [*Create
    Reference*](access-control.html#category_create) for the reference
    name `+refs/tags/*+`

7.  if you push a tag with somebody else as tagger and you don’t have
    the [*Forge
    Committer*](access-control.html#category_forge_committer) access
    right for the reference name `+refs/tags/*+`

8.  if you push to a project that is in state *Read Only*

For new users it often happens that they accidentally try to bypass code
review. The push then fails with the error message *prohibited by
Gerrit* because the project didn’t allow to bypass code review.
Bypassing the code review is done by pushing directly to
`+refs/heads/*+` (e.g. `refs/heads/master`) instead of pushing to
`+refs/for/*+` (e.g. `refs/for/master`). Details about how to push
commits for code review are explained
[here](user-upload.html#push_create).

## GERRIT

Part of [Gerrit Error Messages](error-messages.html)

## SEARCHBOX

