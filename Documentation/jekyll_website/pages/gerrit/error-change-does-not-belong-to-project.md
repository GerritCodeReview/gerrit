---
title: " change ... does not belong to project ..."
sidebar: errors_sidebar
permalink: error-change-does-not-belong-to-project.html
---
With this error message Gerrit rejects to push a commit to a change that
belongs to another project.

This error message means that the user explicitly pushed a commit to a
change that belongs to another project by specifying it as target ref.
This way of adding a new patch set to a change is deprecated as
explained [here](user-upload.html#manual_replacement_mapping). It is
recommended to only rely on Change-Ids for [replacing
changes](user-upload.html#push_replace).

## GERRIT

Part of [Gerrit Error Messages](error-messages.html)

## SEARCHBOX

