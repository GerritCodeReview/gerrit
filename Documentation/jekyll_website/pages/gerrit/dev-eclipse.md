---
title: " Gerrit Code Review - Eclipse Setup"
sidebar: gerritdoc_sidebar
permalink: dev-eclipse.html
---
This document is about configuring Gerrit Code Review into an Eclipse
workspace for development and debugging with GWT.

Java 6 or later SDK is also required to run GWT’s compiler and runtime
debugging environment.

## Project Setup

In your Eclipse installation’s
[`eclipse.ini`](https://wiki.eclipse.org/Eclipse.ini) file, add the
following line in the `vmargs` section:

``` 
  -DmaxCompiledUnitsAtOnce=10000
```

Without this setting, annotation processing does not work reliably and
the build is likely to fail with errors
like:

``` 
  Could not write generated class ... javax.annotation.processing.FilerException: Source file already created
```

and

``` 
  AutoAnnotation_Commands_named cannot be resolved to a type
```

First, generate the Eclipse project by running the
`tools/eclipse/project.py` script. Then, in Eclipse, choose *Import
existing project* and select the `gerrit` project from the current
working directory.

Expand the `gerrit` project, right-click on the `eclipse-out` folder,
select *Properties*, and then under *Attributes* check *Derived*.

Note that if you make any changes in the project configuration that get
saved to the `.project` file, for example adding Resource Filters on a
folder, they will be overwritten the next time you run
`tools/eclipse/project.py`.

## Code Formatter Settings

To format source code, Gerrit uses the
[`google-java-format`](https://github.com/google/google-java-format)
tool (version 1.3), which automatically formats code to follow the style
guide. See [Code Style](dev-contributing.html#style) for the instruction
how to set up command line tool that uses this formatter. The Eclipse
plugin is provided that allows to format with the same formatter from
within the Eclipse IDE. See [Eclipse
plugin](https://github.com/google/google-java-format#eclipse) for
details how to install it. It’s important to use the same plugin version
as the `google-java-format` script.

## Site Initialization

Build once on the command line with [Bazel](dev-bazel.html#build) and
then follow [Site Initialization](dev-readme.html#init) in the Developer
Setup guide to configure a local site for testing.

## Testing

### Running the Daemon

Duplicate the existing launch configuration:

  - In Eclipse select Run → Debug Configurations …

  - Java Application → `gerrit_daemon`

  - Right click, Duplicate

  - Modify the name to be unique.

  - Switch to Arguments tab.

  - Edit the `-d` program argument flag to match the path used during
    *init*. The template launch configuration resolves to
    `../gerrit_testsite` since that is what the documentation
    recommends.

  - Switch to Common tab.

  - Change Save as to be Local file.

  - Close the Debug Configurations dialog and save the changes when
    prompted.

### Running GWT Debug Mode

The `gerrit_gwt_debug` launch configuration uses GWT’s [Super Dev
Mode](http://www.gwtproject.org/articles/superdevmode.html).

  - Make a local copy of the `gerrit_gwt_debug` configuration, using the
    process described for `gerrit_daemon` above.

  - Launch the local copy of `gerrit_gwt_debug` from the Eclipse debug
    menu.

  - If debugging GWT for the first time:
    
      - Open the [codeserver URL](http://localhost:9876/) and add the
        `Dev Mode On` and `Dev Mode Off` bookmarklet to your bookmark
        bar.
    
      - Activate the source maps feature in your browser. Refer to the
        [Chrome](https://developer.chrome.com/devtools/docs/javascript-debugging#source-maps)
        and
        [Firefox](https://developer.mozilla.org/en-US/docs/Tools/Debugger#Use_a_source_map)
        developer documentation.

  - Load the [Gerrit page](http://localhost:8080).

  - Open the source tab in developer tools.

  - Click the `Dev Mode On` bookmark to incrementally recompile changed
    files.

  - Select the `gerrit_ui` module to compile (the `Compile` button can
    also be used as a bookmarklet).

  - In the developer tools source tab, open a file and set a breakpoint.

  - Navigate to the UI and confirm that the breakpoint is hit.

  - To end the debugging session, click the `Dev Mode Off` bookmark.

<!-- end list -->

  - Hitting `F5` in the browser only reloads the last compile output,
    without recompiling.

  - To reflect your changes in the debug session, click `Dev Mode On`
    then `Compile`.

### Running GWT Debug Mode for Gerrit plugins

A Gerrit plugin can expose GWT module and its implementation can be
inspected in the SDM debug session.

`codeserver` needs two additional inputs to expose the plugin module in
the SDM debug session: the module name and the source folder location.
For example the module name and source folder of `cookbook-plugin`
should be added in the local copy of the `gerrit_gwt_debug`
configuration:

``` 
  com.googlesource.gerrit.plugins.cookbook.HelloForm \
  -src ${resource_loc:/gerrit}/plugins/cookbook-plugin/src/main/java \
  -- --console-log [...]
```

After doing that, both the Gerrit core and plugin GWT modules can be
activated during SDM (debug session)\[<http://imgur.com/HFXZ5No>\].

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

