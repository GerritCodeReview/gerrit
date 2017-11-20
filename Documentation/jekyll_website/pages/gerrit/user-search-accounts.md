---
title: " Gerrit Code Review - Searching Accounts"
sidebar: gerritdoc_sidebar
permalink: user-search-accounts.html
---
## Basic Account Search

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
<td><p>John</p></td>
</tr>
<tr class="even">
<td><p>Email address</p></td>
<td><p><a href="mailto:jdoe@example.com">jdoe@example.com</a></p></td>
</tr>
<tr class="odd">
<td><p>Username</p></td>
<td><p>jdoe</p></td>
</tr>
<tr class="even">
<td><p>Account-Id</p></td>
<td><p>1000096</p></td>
</tr>
<tr class="odd">
<td><p>Own account</p></td>
<td><p>self</p></td>
</tr>
</tbody>
</table>

## Search Operators

Operators act as restrictions on the search. As more operators are added
to the same query string, they further restrict the returned results.
Search can also be performed by typing only a text with no operator,
which will match against a variety of fields.

  - email:'EMAIL'  
    Matches accounts that have the email address *EMAIL* or an email
    address that starts with *EMAIL*.

  - is:active  
    Matches accounts that are active.

  - is:inactive  
    Matches accounts that are inactive.

  - name:'NAME'  
    Matches accounts that have any name part *NAME*. The name parts
    consist of any part of the full name and the email addresses.

  - username:'USERNAME'  
    Matches accounts that have the username *USERNAME*.

## Magical Operators

  - is:visible  
    Magical internal flag to prove the current user has access to read
    the account. This flag is always added to any query.

  - is:active  
    Matches accounts that are active. If neither [is:active](#is-active)
    nor [is:inactive](#is-inactive) is contained in a query, `is:active`
    is automatically added so that by default only active accounts are
    matched.

  - limit:'CNT'  
    Limit the returned results to no more than *CNT* records. This is
    automatically set to the page size configured in the current user’s
    preferences. Including it in a web query may lead to unpredictable
    results with regards to pagination.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

