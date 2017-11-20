---
title: " plugin reload"
sidebar: cmd_sidebar
permalink: cmd-plugin-reload.html
---
## NAME

plugin reload - Reload/Restart plugins.

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit plugin reload
>       <NAME> …

## DESCRIPTION

Reload/Restart plugins.

Whether a plugin is reloaded or restarted is defined by the plugin’s
[reload method](dev-plugins.html#reload_method).

E.g. a plugin needs to be reloaded if its configuration is modified to
make the new configuration data become active.

## ACCESS

  - Caller must be a member of the privileged *Administrators*
    group.

  - [plugins.allowRemoteAdmin](config-gerrit.html#plugins.allowRemoteAdmin)
    must be enabled in `$site_path/etc/gerrit.config`.

## SCRIPTING

This command is intended to be used in scripts.

## OPTIONS

  - \<NAME\>  
    Name of the plugin that should be reloaded. Multiple names of
    plugins that should be reloaded may be specified.

## EXAMPLES

Reload a plugin:

``` 
        ssh -p 29418 localhost gerrit plugin reload my-plugin
```

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

