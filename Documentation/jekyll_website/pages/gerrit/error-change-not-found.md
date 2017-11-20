---
title: " change ... not found"
sidebar: errors_sidebar
permalink: error-change-not-found.html
---
With this error message Gerrit rejects to push a commit to a change that
cannot be found.

This error message means that the user explicitly pushed a commit to a
non-existing change by specifying it as target ref. This way of adding a
new patch set to a change is deprecated as explained
[here](user-upload.html#manual_replacement_mapping). It is recommended
to only rely on Change-Ids for [replacing
changes](user-upload.html#push_replace).

## GERRIT

Part of [Gerrit Error Messages](error-messages.html)

## SEARCHBOX

