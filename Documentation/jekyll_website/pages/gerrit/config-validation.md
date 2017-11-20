---
title: " Gerrit Code Review - Plugin-based Validation"
sidebar: gerritdoc_sidebar
permalink: config-validation.html
---
Gerrit provides interfaces to allow [plugins](dev-plugins.html) to
perform validation on certain operations.

## New commit validation

Plugins implementing the `CommitValidationListener` interface can
perform additional validation checks against new commits.

If the commit fails the validation, the plugin can either provide a
message that will be sent back to the git client, or throw an exception
which will cause the commit to be rejected.

Validation applies to both commits uploaded via `git push`, and new
commits generated via Gerrit’s Web UI features such as the rebase,
revert and cherry-pick buttons.

Out of the box, Gerrit includes a plugin that checks the length of the
subject and body lines of commit messages on uploaded commits.

## User ref operations validation

Plugins implementing the `RefOperationValidationListener` interface can
perform additional validation checks against user ref operations
(resulting from either push or corresponding Gerrit REST/SSH endpoints
call e.g. create branch etc.). Namely including ref creation, deletion
and update (also non-fast-forward) before they are applied to the git
repository.

The plugin can throw an exception which will cause the operation to
fail, and prevent the ref update from being applied.

## Pre-merge validation

Plugins implementing the `MergeValidationListener` interface can perform
additional validation checks against commits before they are merged to
the git repository.

If the commit fails the validation, the plugin can throw an exception
which will cause the merge to fail.

## On submit validation

Plugins implementing the `OnSubmitValidationListener` interface can
perform additional validation checks against ref operations resulting
from execution of submit operation before they are applied to any git
repositories (there could be more than one in case of topic submits).

Plugin can throw an exception which will cause submit operation to be
aborted.

## Pre-upload validation

Plugins implementing the `UploadValidationListener` interface can
perform additional validation checks before any upload operations
(clone, fetch, pull). The validation is executed right before Gerrit
begins to send a pack back to the git client.

If upload fails the validation, the plugin can throw an exception which
will cause the upload to fail and the exception’s message text will be
reported to the git client.

## New project validation

Plugins implementing the `ProjectCreationValidationListener` interface
can perform additional validation on project creation based on the input
arguments.

E.g. a plugin could use this to enforce a certain name scheme for
project names.

## New group validation

Plugins implementing the `GroupCreationValidationListener` interface can
perform additional validation on group creation based on the input
arguments.

E.g. a plugin could use this to enforce a certain name scheme for group
names.

## Assignee validation

Plugins implementing the `AssigneeValidationListener` interface can
perform validation of assignees before they are assigned to a change.

## Hashtag validation

Plugins implementing the `HashtagValidationListener` interface can
perform validation of hashtags before they are added to or removed from
changes.

## Outgoing e-mail validation

This interface provides a low-level e-mail filtering API for plugins.
Plugins implementing the `OutgoingEmailValidationListener` interface can
perform filtering of outgoing e-mails just before they are sent.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

