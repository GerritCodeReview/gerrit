---
title: " rulec"
sidebar: gerritdoc_sidebar
permalink: pgm-rulec.html
---
## NAME

rulec - Compile project-specific Prolog rules to JARs

## SYNOPSIS

> 
> 
>     java -jar gerrit.war rulec
>       -d <SITE_PATH>
>       [--quiet]
>       [--all | <PROJECT>…]

## DESCRIPTION

Looks for a Prolog rule file named `rules.pl` on the repository’s
`refs/meta/config` branch. If rules.pl exists, creates a JAR file named
`rules-'SHA1'.jar` in `'$site_path'/cache/rules`.

## OPTIONS

  - \-d; --site-path  
    Location of the gerrit.config file, and all other per-site
    configuration data, supporting libraries and log files.

  - \--all  
    Compile rules for all projects.

  - \--quiet  
    Suppress non-error output messages.

\<PROJECT\>: Compile rules for the specified project.

## CONTEXT

This command can only be run on a server which has direct connectivity
to the metadata database, and local access to the managed Git
repositories.

Caching needs to be enabled. See
[cache.directory](config-gerrit.html#cache.directory).

## EXAMPLES

To compile a rule JAR file for test/project:

``` 
        $ java -jar gerrit.war rulec -d site_path test/project
```

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

