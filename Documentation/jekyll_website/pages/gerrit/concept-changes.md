---
title: " Changes"
sidebar: gerritdoc_sidebar
permalink: concept-changes.html
---
A change represents a single commit under review. Each change is
identified by a [section\_title](#change-id).

Multiple git commits can share the same Change-Id, allowing you to
update a change as you receive feedback through the code review process.
In Gerrit, commits that share the same Change-Id are referred to as
*patch sets*. When a change is approved, only the latest version of a
commit is submitted to the repository.

You can view a specific change using Gerrit’s Review screen. This screen
provides the following information for each change:

  - Current and previous patch sets

  - [???](#Change%20properties), such as owner, project, and target
    branch

  - [Comments](CONCEPT-comments.html)

  - Votes on [Review Labels](config-labels.html)

  - The [section\_title](#change-id)

## Change properties

When you open a change in Gerrit, the Review screen displays a number of
properties about that change.

<table>
<caption>Change Properties</caption>
<colgroup>
<col width="50%" />
<col width="50%" />
</colgroup>
<tbody>
<tr class="odd">
<td><p>Property</p></td>
<td><p>Description</p></td>
</tr>
<tr class="even">
<td><p>Updated</p></td>
<td><p>The date on which the change was last updated.</p></td>
</tr>
<tr class="odd">
<td><p>Owner</p></td>
<td><p>The contributor who created the change.</p></td>
</tr>
<tr class="even">
<td><p>Assignee</p></td>
<td><p>The contributor responsible for the change. Often used when a change has mulitple reviewers to identify the individual responsible for final approval.</p></td>
</tr>
<tr class="odd">
<td><p>Reviewers</p></td>
<td><p>A list of one or more contributors responsible for reviewing the change.</p></td>
</tr>
<tr class="even">
<td><p>CC</p></td>
<td><p>A list of one or more contributors who are kept informed about the change, but are not required to review it.</p></td>
</tr>
<tr class="odd">
<td><p>Project</p></td>
<td><p>The name of the Gerrit project.</p></td>
</tr>
<tr class="even">
<td><p>Branch</p></td>
<td><p>The branch on which the change was made.</p></td>
</tr>
<tr class="odd">
<td><p>Topic</p></td>
<td><p>An optional topic.</p></td>
</tr>
<tr class="even">
<td><p>Strategy</p></td>
<td><p>The <a href="#submit-strategy">???</a> for the change.</p></td>
</tr>
<tr class="odd">
<td><p>Code Review</p></td>
<td><p>Displays the Code Review status for the change.</p></td>
</tr>
</tbody>
</table>

In addition, Gerrit displays the status of any additional labels, such
as the Verified label, that have been configured for the server. See
[Review Labels](config-labels.html) for more information.

## Change Message

Next to the list of change properties is the change message. This
message contains user-supplied information regarding what the change
does. To modify the change message, click the **Edit** link.

By default, the change message contains the Change-Id. This ID contains
a permanent link to a search for that Change-Id in Gerrit.

## Related Changes

In some cases, a change may be dependent on another change. These
changes are listed next to the change message. These related changes are
grouped together in several categories, including:

  - Relation Chain. These changes are related by parent-child
    relationships, regardless of [???](#topics).

  - Merge Conflicts. These are changes in which there is a merge
    conflict with the current change.

  - Submitted Together. These are changes that share the same
    [???](#topics).

An arrow indicates the change you are currently viewing.

## Topics

Changes can be grouped by topics. Topics make it easier to find related
changes by using the topic search operator. Changes with the same topic
also appear in the **Relation Chain** section of the Review screen.

Grouping changes by topics can be helpful when you have several changes
that, when combined, implement a feature.

Assigning a topic to a change can be done in the change screen or
through a `git
push` command.

## Submit strategies

Each project in Gerrit can employ a specific submit strategy. This
strategy is listed in the change properties section of the Review
screen.

The following table lists the supported submit strategies.

<table>
<caption>Submit Strategies</caption>
<colgroup>
<col width="50%" />
<col width="50%" />
</colgroup>
<tbody>
<tr class="odd">
<td><p>Strategy</p></td>
<td><p>Description</p></td>
</tr>
<tr class="even">
<td><p>Fast Forward Only</p></td>
<td><p>No merge commits are produced. All merges must be handled on the client, before submitting the change.</p>
<p>To submit a change, the change must be a strict superset of the destination branch.</p></td>
</tr>
<tr class="odd">
<td><p>Merge If Necessary</p></td>
<td><p>The default submit strategy. If the change being submitted is a strict superset of the destination branch, then the branch is fast-forwarded to the change. If not, a merge commit is automatically created at submit time. This is identical to the <code>git merge --ff</code> command.</p></td>
</tr>
<tr class="even">
<td><p>Always Merge</p></td>
<td><p>Always produce a merge commit, even if the change is a strict superset of the destination branch. This is identical to the <code>git merge --no-ff</code> command. It is often used when users of the project want to be able to read the history of submits by running the <code>git log --first-parent</code> command.</p></td>
</tr>
<tr class="odd">
<td><p>Cherry Pick</p></td>
<td><p>Always cherry pick the patch set, ignoring the parent lineage and instead creating a new commit on top of the current branch.</p>
<p>When cherry picking a change, Gerrit automatically appends a short summary of the change’s approvals and a link back to the change. The committer header is also set to the submitter, while the author header retains the original patch set author.</p>
<p>NOTE: Gerrit ignores dependencies between changes when using this submit type unless <code>change.submitWholeTopic</code> is enabled and depending changes share the same topic. This means submitters must remember to submit changes in the right order when using this submit type.</p></td>
</tr>
<tr class="even">
<td><p>Rebase if Necessary</p></td>
<td><p>If the change being submitted is a strict superset of the destination branch, the branch is fast-forwarded to the change. If not, the change is automatically rebased and the branch is fast-forwarded to the change.</p></td>
</tr>
<tr class="odd">
<td><p>Rebase Always</p></td>
<td><p>Similar to Rebase If Necessary, but creates a new patch set even if fast forward is possible. This strategy is also similar to Cherry Pick; however, Rebase Always does not ignore dependencies.</p></td>
</tr>
</tbody>
</table>

Any project owner can use the Project screen to modify the method Gerrit
uses to submit a change.

## Change-Id

Gerrit uses a Change-Id to identify which patch sets belong to the same
review. For example, you make a change to a project. A reviewer supplies
some feedback, which you address in a second commit. By assigning the
same Change-Id to both commits, Gerrit can attach those commits to the
same change.

Change-Ids are appended to the end of a commit message, and resemble the
following:

    commit 29a6bb1a059aef021ac39d342499191278518d1d
    Author: A. U. Thor <author@example.com>
    Date: Thu Aug 20 12:46:50 2009 -0700
    
        Improve foo widget by attaching a bar.
    
        We want a bar, because it improves the foo by providing more
        wizbangery to the dowhatimeanery.
    
        Bug: #42
        Change-Id: Ic8aaa0728a43936cd4c6e1ed590e01ba8f0fbf5b
        Signed-off-by: A. U. Thor <author@example.com>
        CC: R. E. Viewer <reviewer@example.com>

Gerrit requires that the Change-Id is in the footer (last paragraph) of
a commit message. It can be combined with a Signed-off-by, CC, or other
lines. For instance, the previous example has a Change-Id, along with a
Signed-off-by and CC line.

Notice that the Change-Id is similar to the commit id. To avoid
confusing the two, a Change-Id typically begins with an `I`.

While there are several ways you can add a Change-Id, the standard
method uses git’s [commit-msg hook](cmd-hook-commit-msg.html) to
automatically add the Change-Id to each new commit.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

