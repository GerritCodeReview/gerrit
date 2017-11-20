---
title: " reindex"
sidebar: gerritdoc_sidebar
permalink: pgm-reindex.html
---
## NAME

reindex - Rebuild the secondary index

## SYNOPSIS

> 
> 
>     java -jar gerrit.war reindex
>       [--threads]
>       [--changes-schema-version]
>       [--verbose]
>       [--list]
>       [--index]

## DESCRIPTION

Rebuilds the secondary index.

## OPTIONS

  - \--threads  
    Number of threads to use for indexing.

  - \--changes-schema-version  
    Schema version to reindex; default is most recent version.

  - \--verbose  
    Output debug information for each change.

  - \--list  
    List available index names.

  - \--index  
    Reindex only index with given name. This option can be supplied more
    than once to reindex multiple indices.

## CONTEXT

The secondary index must be enabled. See
[index.type](config-gerrit.html#index.type).

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

