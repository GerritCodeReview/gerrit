---
title: " gerrit version"
sidebar: cmd_sidebar
permalink: cmd-version.html
---
## NAME

gerrit version - Show the version of the currently executing Gerrit
server

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit version

## DESCRIPTION

Displays a one-line response with the string `gerrit version` followed
by the currently executing version of Gerrit.

The `git describe` command is used to generate the version string based
on the Git commit used to build Gerrit. For official releases of Gerrit,
the version string will be equal to the Git tag set in the Gerrit source
code, which in turn is equal to the name of the release (for example
2.4.2). When building Gerrit from another commit (one that doesn’t have
an official-looking tag pointing to it), the version string has the form
`<tagname>-<n>-g<sha1>`, where `<n>` is an integer indicating the number
of commits ahead of the `<tagname>` tag the commit is, and `<sha1>` is
the seven-character abbreviated SHA-1 of the commit. See the `git
describe` documentation for details on how `<tagname>` is chosen and how
`<n>` is computed.

## ACCESS

Any user who has SSH access to Gerrit.

## SCRIPTING

This command is intended to be used in scripts.

## EXAMPLES

``` 
        $ ssh -p 29418 review.example.com gerrit version
        gerrit version 2.4.2
```

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

