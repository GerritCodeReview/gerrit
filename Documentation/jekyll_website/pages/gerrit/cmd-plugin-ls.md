---
title: " plugin ls"
sidebar: cmd_sidebar
permalink: cmd-plugin-ls.html
---
## NAME

plugin ls - List the installed plugins.

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit plugin ls
>       [--all | -a]
>       [--format {text | json | json_compact}]

## DESCRIPTION

List the installed plugins and show their version and status.

## ACCESS

  - The caller must be a member of a group that is granted the [View
    Plugins](access-control.html#capability_viewPlugins) capability or
    the [Administrate
    Server](access-control.html#capability_administrateServer)
    capability.

  - [plugins.allowRemoteAdmin](config-gerrit.html#plugins.allowRemoteAdmin)
    must be enabled in `$site_path/etc/gerrit.config`.

## SCRIPTING

This command is intended to be used in scripts.

## OPTIONS

  - \--all; -a  
    List all plugins, including disabled plugins.

  - \--format  
    What output format to display the results in.
    
      - `text`  
        Simple text based format.
    
      - `json`  
        Map of JSON objects describing each project.
    
      - `json_compact`  
        Minimized JSON output.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

