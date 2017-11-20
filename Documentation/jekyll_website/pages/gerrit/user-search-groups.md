---
title: " Gerrit Code Review - Searching Groups"
sidebar: gerritdoc_sidebar
permalink: user-search-groups.html
---
Group queries only match internal groups. External groups and system
groups are not included in the query result.

## Basic Group Search

Similar to many popular search engines on the web, just enter some text
and let Gerrit figure out the meaning:

<table>
<colgroup>
<col width="50%" />
<col width="50%" />
</colgroup>
<thead>
<tr class="header">
<th>Description</th>
<th>Examples</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p>Name</p></td>
<td><p>Foo-Verifiers</p></td>
</tr>
<tr class="even">
<td><p>UUID</p></td>
<td><p>6a1e70e1a88782771a91808c8af9bbb7a9871389</p></td>
</tr>
<tr class="odd">
<td><p>Description</p></td>
<td><p>deprecated</p></td>
</tr>
</tbody>
</table>

## Search Operators

Operators act as restrictions on the search. As more operators are added
to the same query string, they further restrict the returned results.
Search can also be performed by typing only a text with no operator,
which will match against a variety of fields.

  - description:'DESCRIPTION'  
    Matches groups that have a description that contains *DESCRIPTION*
    (case-insensitive).

  - inname:'NAMEPART'  
    Matches groups that have a name part that starts with *NAMEPART*
    (case-insensitive).

  - is:visibletoall  
    Matches groups that are in the groups options marked as visible to
    all registered users.

  - name:'NAME'  
    Matches groups that have the name *NAME* (case-insensitive).

  - owner:'OWNER'  
    Matches groups that are owned by the group whose name best matches
    *OWNER* or that has the UUID *OWNER*.

  - uuid:'UUID'  
    Matches groups that have the UUID *UUID*.

  - member:'MEMBER'  
    Matches groups that have the account represented by *MEMBER* as a
    member.

  - subgroup:'SUBGROUP'  
    Matches groups that have a subgroup whose name best matches
    *SUBGROUP* or whose UUID is *SUBGROUP*.

## Magical Operators

  - is:visible  
    Magical internal flag to prove the current user has access to read
    the group. This flag is always added to any query.

  - limit:'CNT'  
    Limit the returned results to no more than *CNT* records. This is
    automatically set to the page size configured in the current user’s
    preferences. Including it in a web query may lead to unpredictable
    results with regards to pagination.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

