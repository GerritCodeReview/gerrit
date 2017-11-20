---
title: " How Gerrit Works"
sidebar: gerritdoc_sidebar
permalink: intro-how-gerrit-works.html
---
To understand how Gerrit fits into and enhances the developer workflow,
consider a typical project. This project has a central source
repository, which serves as the authoritative copy of the project’s
contents.

![Central Source Repository](images/intro-quick-central-repo.png "fig:")

Gerrit takes the place of this central repository and adds an additional
concept: a *store of pending changes*.

![Gerrit in place of Central
Repository](images/intro-quick-central-gerrit.png "fig:")

With Gerrit, when a developer makes a change, it is sent to this store
of pending changes, where other developers can review, discuss and
approve the change. After enough reviewers grant their approval, the
change becomes an official part of the codebase.

In addition to this store of pending changes, Gerrit captures notes and
comments about each change. These features allow developers to review
changes at their convenience, or when conversations about a change can’t
happen face to face. They also help to create a record of the
conversation around a given change, which can provide a history of when
a change was made and why.

Like any repository hosting solution, Gerrit has a powerful [access
control model](access-control.html). This model allows you to fine-tune
access to your repository.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

