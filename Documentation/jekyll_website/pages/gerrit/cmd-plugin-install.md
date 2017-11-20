---
title: " plugin install"
sidebar: cmd_sidebar
permalink: cmd-plugin-install.html
---
## NAME

plugin install - Install/Add a plugin.

plugin add - Install/Add a plugin.

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit plugin install | add
>       [--name <NAME> | -n <NAME>]
>       - | <URL> | <PATH>

## DESCRIPTION

Install/Add a plugin. The plugin will be copied into the site path’s
`plugins` directory.

## ACCESS

  - Caller must be a member of the privileged *Administrators*
    group.

  - [plugins.allowRemoteAdmin](config-gerrit.html#plugins.allowRemoteAdmin)
    must be enabled in `$site_path/etc/gerrit.config`.

## SCRIPTING

This command is intended to be used in scripts.

## OPTIONS

  - \-  
    Plugin jar or js as piped input.

  - \<URL\>  
    URL from where the plugin should be downloaded. This can be an HTTP
    or FTP site.

  - \<PATH\>  
    Absolute file path to the plugin jar or js.

  - \--name; -n  
    The name under which the plugin should be installed. Note: if the
    plugin provides its own name in the MANIFEST file, then the plugin
    name from the MANIFEST file has precedence over this option.

## EXAMPLES

Install a plugin from an absolute file path on the server’s host:

``` 
        ssh -p 29418 localhost gerrit plugin install -n name.jar \
          $(pwd)/my-plugin.jar
```

Install a WebUI plugin from an absolute file path on the server’s host:

``` 
  ssh -p 29418 localhost gerrit plugin install -n name.js \
    $(pwd)/my-webui-plugin.js
```

Install a plugin from an HTTP site:

``` 
        ssh -p 29418 localhost gerrit plugin install -n name.jar \
          http://build-server/output/our-plugin
```

Install a plugin from piped input:

``` 
        ssh -p 29418 localhost gerrit plugin install -n name.jar \
          - <target/name-0.1.jar
```

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

