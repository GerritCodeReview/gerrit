---
title: " Gerrit Code Review - Stars"
sidebar: gerritdoc_sidebar
permalink: dev-stars.html
---
## Description

Changes can be starred with labels that behave like private hashtags.
Any label can be applied to a change, but these labels are only visible
to the user for which the labels have been set.

Stars allow users to categorize changes by self-defined criteria and
then build [dashboards](user-dashboards.html) for them by making use of
the [star query operators](#query-stars).

## Star API

The [star REST API](rest-api-accounts.html#star-endpoints) supports:

  - [get star labels from a change](rest-api-accounts.html#get-stars)

  - [update star labels on a change](rest-api-accounts.html#set-stars)

  - [list changes that are starred by any
    label](rest-api-accounts.html#get-starred-changes)

Star labels are also included in
[ChangeInfo](rest-api-changes.html#change-info) entities that are
returned by the [changes REST API](rest-api-changes.html).

There are [additional REST
endpoints](rest-api-accounts.html#default-star-endpoints) for the
[default star](#default-star).

Only the [default star](#default-star) is shown in the WebUI and can be
updated from there. Other stars do not show up in the WebUI.

## Default Star

If the default star is set by a user, this user is automatically
notified by email whenever updates are made to that change.

The default star is the star that is shown in the WebUI and which can be
updated from there.

The default star is represented by the special star label *star*.

## Ignore Star

If the ignore star is set by a user, this user gets no email
notifications for updates of that change, even if this user is a
reviewer of the change or the change is matched by a project watch of
the user.

Since changes can only be ignored once they are created, users that
watch a project will always get the email notifications for the change
creation. Only then the change can be ignored.

Users that are added as reviewer or assignee to a change that they have
ignored will be notified about this, so that they know about the review
request. They can then decide to remove the ignore star.

The ignore star is represented by the special star label *ignore*.

## Reviewed Star

If the "reviewed/\<patchset\_id\>"-star is set by a user, and
\<patchset\_id\> matches the current patch set, the change is always
reported as "reviewed" in the ChangeInfo.

This allows users to "de-highlight" changes in a dashboard until a new
patchset has been uploaded.

## Unreviewed Star

If the "unreviewed/\<patchset\_id\>"-star is set by a user, and
\<patchset\_id\> matches the current patch set, the change is always
reported as "unreviewed" in the ChangeInfo.

This allows users to "highlight" changes in a dashboard.

## Query Stars

There are several query operators to find changes with stars:

  - [star:\<LABEL\>](user-search.html#star): Matches any change that was
    starred by the current user with the label `<LABEL>`.

  - [has:stars](user-search.html#has-stars): Matches any change that was
    starred by the current user with any label.

  - [is:starred](user-search.html#is-starred) /
    [has:star](user-search.html#has-star): Matches any change that was
    starred by the current user with the [default star](#default-star).

## Syntax

Star labels cannot contain whitespace characters. All other characters
are allowed.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

