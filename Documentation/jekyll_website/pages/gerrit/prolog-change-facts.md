---
title: " Prolog Facts for Gerrit Changes"
sidebar: gerritdoc_sidebar
permalink: prolog-change-facts.html
---
Prior to invoking the `submit_rule(X)` query for a change, Gerrit
initializes the Prolog engine with a set of facts (current data) about
this change. The following table provides an overview of the provided
facts.

> **Important**
> 
> All the terms listed below are defined in the `gerrit` package. To use
> any of them we must use a qualified name like
> `gerrit:change_branch(X)`.

<table>
<caption>Prolog facts about the current change</caption>
<colgroup>
<col width="33%" />
<col width="33%" />
<col width="33%" />
</colgroup>
<thead>
<tr class="header">
<th>Fact</th>
<th>Example</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>change_branch/1</code></p></td>
<td><p><code>change_branch('refs/heads/master').</code></p></td>
<td><p>Destination Branch for the change as string atom</p></td>
</tr>
<tr class="even">
<td><p><code>change_owner/1</code></p></td>
<td><p><code>change_owner(user(1000000)).</code></p></td>
<td><p>Owner of the change as <code>user(ID)</code> term. ID is the numeric account ID</p></td>
</tr>
<tr class="odd">
<td><p><code>change_project/1</code></p></td>
<td><p><code>change_project('full/project/name').</code></p></td>
<td><p>Name of the project as string atom</p></td>
</tr>
<tr class="even">
<td><p><code>change_topic/1</code></p></td>
<td><p><code>change_topic('plugins').</code></p></td>
<td><p>Topic name as string atom</p></td>
</tr>
<tr class="odd">
<td><p><code>commit_author/1</code></p></td>
<td><p><code>commit_author(user(100000)).</code></p></td>
<td><p>Author of the commit as <code>user(ID)</code> term. ID is the numeric account ID</p></td>
</tr>
<tr class="even">
<td><p><code>commit_author/3</code></p></td>
<td><p><code>commit_author(user(100000), 'John Doe', 'john.doe@example.com').</code></p></td>
<td><p>ID, full name and the email of the commit author. The full name and the email are string atoms</p></td>
</tr>
<tr class="odd">
<td><p><code>commit_committer/1</code></p></td>
<td><p><code>commit_committer()</code></p></td>
<td><p>Committer of the commit as <code>user(ID)</code> term. ID is the numeric account ID</p></td>
</tr>
<tr class="even">
<td><p><code>commit_committer/3</code></p></td>
<td><p><code>commit_committer()</code></p></td>
<td><p>ID, full name and the email of the commit committer. The full name and the email are string atoms</p></td>
</tr>
<tr class="odd">
<td><p><code>commit_label/2</code></p></td>
<td><p><code>commit_label(label('Code-Review', 2), user(1000000)).</code></p></td>
<td><p>Set of votes on the last patch-set</p></td>
</tr>
<tr class="even">
<td><p><code>commit_label(label('Verified', -1), user(1000001)).</code></p></td>
</tr>
<tr class="odd">
<td><p><code>commit_message/1</code></p></td>
<td><p><code>commit_message('Fix bug X').</code></p></td>
<td><p>Commit message as string atom</p></td>
</tr>
<tr class="even">
<td><p><code>commit_stats/3</code></p></td>
<td><p><code>commit_stats(5,20,50).</code></p></td>
<td><p>Number of files modified, number of insertions and the number of deletions.</p></td>
</tr>
<tr class="odd">
<td><p><code>current_user/1</code></p></td>
<td><p><code>current_user(user(1000000)).</code></p></td>
<td><p>Current user as one of the four given possibilities</p></td>
</tr>
<tr class="even">
<td><p><code>current_user(user(anonymous)).</code></p></td>
</tr>
<tr class="odd">
<td><p><code>current_user(user(peer_daemon)).</code></p></td>
</tr>
<tr class="even">
<td><p><code>current_user(user(replication)).</code></p></td>
</tr>
<tr class="odd">
<td><p><code>pure_revert/1</code></p></td>
<td><p><code>pure_revert(1).</code></p></td>
<td><p><a href="rest-api-changes.html#get-pure-revert">Pure revert</a> as integer atom (1 if the change is a pure revert, 0 otherwise)</p></td>
</tr>
<tr class="even">
<td><p><code>uploader/1</code></p></td>
<td><p><code>uploader(user(1000000)).</code></p></td>
<td><p>Uploader as <code>user(ID)</code> term. ID is the numeric account ID</p></td>
</tr>
<tr class="odd">
<td><p><code>unresolved_comments_count/1</code></p></td>
<td><p><code>unresolved_comments_count(0).</code></p></td>
<td><p>The number of unresolved comments as an integer atom</p></td>
</tr>
</tbody>
</table>

In addition Gerrit provides a set of built-in helper predicates that can
be used when implementing the `submit_rule` predicate. The most common
ones are listed in the following table.

<table>
<caption>Built-in Prolog helper predicates</caption>
<colgroup>
<col width="33%" />
<col width="33%" />
<col width="33%" />
</colgroup>
<thead>
<tr class="header">
<th>Predicate</th>
<th>Example usage</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>commit_delta/1</code></p></td>
<td><p><code>commit_delta('\\.java$').</code></p></td>
<td><p>True if any file name from the last patch set matches the given regex.</p></td>
</tr>
<tr class="even">
<td><p><code>commit_delta/3</code></p></td>
<td><p><code>commit_delta('\\.java$', T, P)</code></p></td>
<td><p>Returns the change type (via <code>T</code>) and path (via <code>P</code>), if the change type is <code>rename</code>, it also returns the old path. If the change type is <code>rename</code>, it returns a delete for old path and an add for new path. If the change type is <code>copy</code>, an add is returned along with new path.</p>
<p>Possible values for the change type are the following symbols: <code>add</code>, <code>modify</code>, <code>delete</code>, <code>rename</code>, <code>copy</code></p></td>
</tr>
<tr class="odd">
<td><p><code>commit_delta/4</code></p></td>
<td><p><code>commit_delta('\\.java$', T, P, O)</code></p></td>
<td><p>Like <code>commit_delta/3</code> plus the old path (via <code>O</code>) if applicable.</p></td>
</tr>
<tr class="even">
<td><p><code>commit_edits/2</code></p></td>
<td><p><code>commit_edits('/pom.xml$', 'dependency')</code></p></td>
<td><p>True if any of the files matched by the file name regex (first parameter) have edited lines that match the regex in the second parameter. This example will be true if there is a modification of a <code>pom.xml</code> file such that an edited line contains or contained the string <code>'dependency'</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>commit_message_matches/1</code></p></td>
<td><p><code>commit_message_matches('^Bug fix')</code></p></td>
<td><p>True if the commit message matches the given regex.</p></td>
</tr>
</tbody>
</table>

> **Note**
> 
> For a complete list of built-in helpers read the `gerrit_common.pl`
> and all Java classes whose name matches `PRED_*.java` from Gerrit’s
> source code.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

