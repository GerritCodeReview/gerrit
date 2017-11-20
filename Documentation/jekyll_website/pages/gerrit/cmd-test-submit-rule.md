---
title: " gerrit test-submit rule"
sidebar: cmd_sidebar
permalink: cmd-test-submit-rule.html
---
## NAME

gerrit test-submit rule - Test prolog submit rules with a chosen
changeset.

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit test-submit rule
>       [-s]
>       [--no-filters]
>       CHANGE

## DESCRIPTION

Provides a way to test prolog [submit rules](prolog-cookbook.html).

## OPTIONS

  - \-s  
    Reads a rules.pl file from stdin instead of rules.pl in
    refs/meta/config.

  - \--no-filters  
    Don’t run the submit\_filter/2 from the parent projects of the
    specified change.

## ACCESS

Can be used by anyone that has permission to read the specified
changeset.

## EXAMPLES

Test submit\_rule from stdin and return the results as
JSON.

``` 
 cat rules.pl | ssh -p 29418 review.example.com gerrit test-submit rule -s I78f2c6673db24e4e92ed32f604c960dc952437d9
 [
   {
     "status": "NOT_READY",
     "reject": {
       "Any-Label-Name": {}
     }
   }
 ]
```

Test the active submit\_rule from the refs/meta/config branch, ignoring
filters in the project
parents.

``` 
 $ ssh -p 29418 review.example.com gerrit test-submit rule I78f2c6673db24e4e92ed32f604c960dc952437d9 --no-filters
 [
   {
     "status": "NOT_READY",
     "need": {
       "Code-Review": {}
       "Verified": {}
     }
   }
 ]
```

## SCRIPTING

Can be used either interactively for testing new prolog submit rules, or
from a script to check the submit status of a change.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

