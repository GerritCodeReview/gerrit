---
title: " passwd"
sidebar: gerritdoc_sidebar
permalink: pgm-passwd.html
---
## NAME

passwd - Set or reset password in secure.config.

## SYNOPSIS

> 
> 
>     java -jar gerrit.war passwd
>       -d <SITE_PATH>
>       <SECTION.KEY>
>       [PASSWORD]

## DESCRIPTION

Set or reset password in an existing Gerrit server installation,
interactively prompting for a new password or using the one provided in
the command line argument.

## OPTIONS

  - \-d; --site-path  
    Location of the `gerrit.config` file, and all other per-site
    configuration data, supporting libraries and log files.

## ARGUMENTS

  - SECTION.KEY  
    Section and key in the `secure.config` file for setting or editing
    the password value.

  - PASSWORD  
    New password to set in `secure.config` associated to the section and
    key. When specified as argument, automatically implies batch mode
    and the command would not ask anything interactively.

## CONTEXT

This utility is typically useful when a secure store is configured to
encrypt password values and thus editing the file manually is not an
option.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

