---
title: " too many commits"
sidebar: errors_sidebar
permalink: error-too-many-commits.html
---
This error occurs when a push directly to a branch [bypassing
review](user-upload.html#bypass_review) contains more commits than the
server is able to validate in a single batch.

The recommended way to avoid this message is to use the
[`skip-validation` push option](user-upload.html#skip_validation).
Depending on the number of commits, it may also be feasible to split the
push into smaller batches.

The actual limit is controlled by a [server config
option](config-gerrit.html#receive.maxBatchCommits).

## GERRIT

Part of [Gerrit Error Messages](error-messages.html)

## SEARCHBOX

