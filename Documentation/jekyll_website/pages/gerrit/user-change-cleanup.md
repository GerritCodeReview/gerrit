---
title: " Gerrit Code Review - Change Cleanup"
sidebar: gerritdoc_sidebar
permalink: user-change-cleanup.html
---
Gerrit administrators may configure [change
cleanups](config-gerrit.html#changeCleanup) that are executed
periodically.

## Auto-Abandon

This cleanup job automatically abandons open changes that have been
inactive for a defined time.

Abandoning old inactive changes has the following advantages:

  - it signals change authors that changes are considered outdated

  - it keeps dashboards clean

  - it reduces the load on the server (for open changes the mergeability
    flag is recomputed whenever a change is merged)

If a change is still wanted it can be restored by clicking on the
`Restore` button.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

