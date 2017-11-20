---
title: " init"
sidebar: gerritdoc_sidebar
permalink: pgm-init.html
---
## NAME

init - Initialize a new Gerrit server installation or upgrade an
existing installation.

## SYNOPSIS

> 
> 
>     java -jar gerrit.war init
>       -d <SITE_PATH>
>       [--batch]
>       [--delete-caches]
>       [--no-auto-start]
>       [--skip-plugins]
>       [--list-plugins]
>       [--install-plugin=<PLUGIN_NAME>]
>       [--install-all-plugins]
>       [--secure-store-lib]
>       [--dev]
>       [--skip-all-downloads]
>       [--skip-download=<LIBRARY_NAME>]

## DESCRIPTION

Creates a new Gerrit server installation, interactively prompting for
some basic setup prior to writing default configuration files into a
newly created `$site_path`.

If run in an existing `$site_path`, init upgrades existing resources
(e.g. DB schema, plugins) as necessary.

## OPTIONS

  - \-b; --batch  
    Run in batch mode, skipping interactive prompts. For a fresh
    install, reasonable configuration defaults are chosen based on the
    whims of the Gerrit developers. On upgrades, the existing settings
    in `gerrit.config` are respected.
    
    If during a schema migration unused objects (e.g. tables, columns)
    are detected, they are **not** automatically dropped; a list of SQL
    statements to drop these objects is provided. To drop the unused
    objects these SQL statements must be executed manually.

  - \--delete-caches  
    Force deletion of all persistent cache files. Note that re-creation
    of these caches may be expensive.

  - \--no-auto-start  
    Don’t automatically start the daemon after initializing a newly
    created site path. This permits the administrator to inspect and
    modify the configuration before the daemon is started.

  - \-d; --site-path  
    Location of the `gerrit.config` file, and all other per-site
    configuration data, supporting libraries and log files.

  - \--skip-plugins  
    Entirely skip installation and initialization of plugins. This
    option is needed when initializing a gerrit site without an archive.
    That happens when running gerrit acceptance or integration tests in
    a debugger, using classes. Supplying this option leads to ignoring
    the `--install-plugin` and `--install-all-plugins` options, if
    supplied as well.

  - \--list-plugins  
    Print names of plugins that can be installed during init process.

  - \--install-all-plugins  
    Automatically install all plugins from gerrit.war without asking.
    This option also works in batch mode. This option cannot be supplied
    alongside `--install-plugin`.

  - \--secure-store-lib  
    Path to the jar providing the chosen
    [SecureStore](dev-plugins.html#secure-store) implementation class.
    This option is used in the same way as the `--new-secure-store-lib`
    option documented in
    [SwitchSecureStore](pgm-SwitchSecureStore.html).

  - \--install-plugin  
    Automatically install plugin with given name without asking. This
    option also works in batch mode. This option may be supplied more
    than once to install multiple plugins. This option cannot be
    supplied alongside `--install-all-plugins`.

  - \--dev  
    Install in developer mode. Default configuration settings are chosen
    to run the Gerrit server as a developer.

  - \--skip-all-downloads  
    Do not automatically download and install required libraries. The
    administrator must manually install the required libraries in the
    `lib/` folder.

  - \--skip-download  
    Do not automatically download and install the library with the given
    name. The administrator must manually install the required library
    in the `lib/` folder.

## CONTEXT

This command can only be run on a server which has direct connectivity
to the metadata database, and local access to the managed Git
repositories.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

