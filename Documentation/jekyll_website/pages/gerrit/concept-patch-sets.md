---
title: " Patch Sets"
sidebar: gerritdoc_sidebar
permalink: concept-patch-sets.html
---
As described in [Changes](concept-changes.html), a change represents a
single commit under review. Each change is assigned a
[Change-Id](concept-changes.html#change-id).

It is very common to amend a commit during the code review process.
Gerrit uses the Change-Id to associate each iteration of the commit with
the same change. These iterations of a commit are referred to as *patch
sets*. When a change is approved, only the latest version of a commit is
submitted to the repository.

> **Note**
> 
> It is also possible to copy a Change-Id to a completely new commit.
> This is useful in situations where you want to keep the discussion
> around a change, but also need to completely modify your approach.

## File List

When you open a change in Gerrit, a list of affected files appears in
the file list, located in the middle of the Review screen. This table
displays the following information for each file:

  - A checkbox, indicating the file has been reviewed

  - The type of modification

  - The path and name of the file

  - The number of added lines and or deleted lines

## File modifications

Each file in a patch set has a letter next to it, indicating the type of
modification for that file. The following table lists the types of
modifications.

<table>
<caption>Types of file modifications</caption>
<colgroup>
<col width="33%" />
<col width="33%" />
<col width="33%" />
</colgroup>
<tbody>
<tr class="odd">
<td><p>Letter</p></td>
<td><p>Modification Type</p></td>
<td><p>Definition</p></td>
</tr>
<tr class="even">
<td><p>M</p></td>
<td><p>Modification</p></td>
<td><p>The file existed before this change and is modified.</p></td>
</tr>
<tr class="odd">
<td><p>A</p></td>
<td><p>Added</p></td>
<td><p>The file is newly added.</p></td>
</tr>
<tr class="even">
<td><p>D</p></td>
<td><p>Deleted</p></td>
<td><p>The file is deleted.</p></td>
</tr>
<tr class="odd">
<td><p>R</p></td>
<td><p>Renamed</p></td>
<td><p>The file is renamed.</p></td>
</tr>
<tr class="even">
<td><p>C</p></td>
<td><p>Copied</p></td>
<td><p>The file is new and is copied from an existing file.</p></td>
</tr>
</tbody>
</table>

If the status is **R** (Renamed) or **C** (Copied), the file list also
displays the original name of the file below the patch set file.

## Views

By default, Gerrit displays the latest patch set for a given change. You
can view previous versions of a patch set by selecting from the **Patch
Set** drop-down list.

## Diffs

Clicking a file in the file list opens the Diff screen. By default, this
screen displays a diff between the latest patch set’s version of a file
and the current version of that file in the repository. You can also
open a diff within the Review screen by clicking the blue triangle
located in the same row as the file. To show the diffs of all files in
the Review screen, click the **Show Diffs** link, located at the top of
the file list.

You can diff between other patch sets by selecting a patch set number
from the **Diff Against** drop-down list.

## Description

Each change in Gerrit must have a change description. This change
description comes from the commit message and becomes part of the
history of the project.

In addition to the change description, you can add a description for a
specific patch set. This description is intended to help guide reviewers
as a change evolves, such as "Added more unit tests." Unlike the change
description, a patch set description does not become a part of the
project’s history.

To add a patch set description, click **Add a patch set description**,
located in the file list.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

