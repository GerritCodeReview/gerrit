---
title: " Gerrit Code Review - Named Queries"
sidebar: gerritdoc_sidebar
permalink: user-named-queries.html
---
## User Named Queries

It is possible to define named queries on a user level. To do this,
define the named queries in the `queries` file under the [user’s
ref](intro-user.html#user-refs) in the `All-Users` project. The user’s
queries file is a 2 column tab delimited file. The left column
represents the name of the query, and the right column represents the
query expression represented by the name.

Example queries file:

    # Name          Query
    #
    selfapproved    owner:self label:code-review+2,user=self
    blocked         label:code-review-2 OR label:verified-1
    # Note below how to reference your own named queries in other named queries
    ready           label:code-review+2 label:verified+1 -query:blocked status:open

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

