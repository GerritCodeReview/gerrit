---
title: " Gerrit Code Review - IntelliJ Setup"
sidebar: gerritdoc_sidebar
permalink: dev-intellij.html
---
## Prerequisites

You need an installation of IntelliJ of version 2016.2.

In addition, Java 8 must be specified on your path or via `JAVA_HOME` so
that building with Bazel via the Bazel plugin is possible.

> **Tip**
> 
> If the synchronization of the project with the BUILD files using the
> Bazel plugin fails and IntelliJ reports the error **Could not get
> Bazel roots**, this indicates that the Bazel plugin couldn’t find Java
> 8.

Bazel must be installed as described by [Building with Bazel -
Installation](#dev-bazel#installation).

## Installation of the Bazel plugin

1.  Go to **File → Settings → Plugins**.

2.  Click on **Browse Repositories**.

3.  Search for the plugin `IntelliJ with Bazel`.

4.  Install it.

5.  Restart IntelliJ.

## Creation of IntelliJ project

1.  Go to **File → Import Bazel Project**.

2.  For **Use existing bazel workspace → Workspace**, select the
    directory containing the Gerrit source code.

3.  Choose **Import from workspace** and select the `.bazelproject` file
    which is located in the top directory of the Gerrit source code.

4.  Adjust the path of the project data directory and the name of the
    project if desired.

> **Tip**
> 
> The project data directory can be separate from the source code. One
> advantage of this is that project files don’t need to be excluded from
> version control.

Unfortunately, the created project seems to have a broken output path.
To fix it, please complete the following steps:

1.  Go to **File → Project Structure → Project Settings → Modules**.

2.  Switch to the tab **Paths**.

3.  Click on **Inherit project compile output path**.

4.  Click on **Use module compile output path**.

## Recommended settings

### Code style

#### google-java-format plugin

Install the `google-java-format` plugin by following these steps:

1.  Go to **File → Settings → Plugins**.

2.  Click on **Browse Repositories**.

3.  Search for the plugin `google-java-format`.

4.  Install it.

5.  Restart IntelliJ.

Every time you start IntelliJ, make sure to use **Code → Reformat with
google-java-format** on an arbitrary line of code. This replaces the
default CodeStyleManager with a custom one. Thus, uses of **Reformat
Code** either via **Code → Reformat Code**, keyboard shortcuts, or the
commit dialog will use the custom style defined by the
`google-java-format` plugin.

#### Code style settings

The `google-java-format` plugin is the preferred way to format the code.
As it only kicks in on demand, it’s also recommended to have code style
settings which help to create properly formatted code as-you-go. Those
settings can’t completely mimic the format enforced by the
`google-java-format` plugin but try to be as close as possible. So
before submitting code, please make sure to run **Reformat Code**.

1.  Download
    [intellij-java-google-style.xml](https://raw.githubusercontent.com/google/styleguide/gh-pages/intellij-java-google-style.xml).

2.  Go to **File → Settings → Editor → Code Style**.

3.  Click on **Manage**.

4.  Click on **Import**.

5.  Choose `IntelliJ IDEA Code Style XML`.

6.  Select the previously downloaded file
    `intellij-java-google-style.xml`.

7.  Make sure that `Google Style` is chosen as **Scheme**.

In addition, the EditorConfig settings (which ensure a consistent style
between Eclipse, IntelliJ, and other editors) should be applied on top
of that. Those settings are in the file `.editorconfig` of the Gerrit
source code. IntelliJ will automatically pick up those settings if the
EditorConfig plugin is enabled and configured correctly as can be
verified by:

1.  Go to **File → Settings → Plugins**.

2.  Ensure that the EditorConfig plugin is enabled.

3.  Go to **File → Settings → Editor → Code Style**.

4.  Ensure that **Enable EditorConfig support** is checked.

> **Note**
> 
> If IntelliJ notifies you later on that the EditorConfig settings
> override the code style settings, simply confirm that.

### Copyright

Copy the folder `$(gerrit_source_code)/tools/intellij/copyright` (not
just the contents) to `$(project_data_directory)/.idea`. If it already
exists, replace it.

### File header

By default, IntelliJ adds a file header containing the name of the
author and the current date to new files. To disable that, follow these
steps:

1.  Go to **File → Settings → Editor → File and Code Templates**.

2.  Select the tab **Includes**.

3.  Select **File Header**.

4.  Remove the template code in the right editor.

### Commit message

To simplify the creation of commit messages which are compliant with the
[Commit Message](#dev-contributing#commit-message) format, do the
following:

1.  Go to **File → Settings → Version Control**.

2.  Check **Commit message right margin (columns)**.

3.  Make sure that 72 is specified as value.

4.  Check **Wrap when typing reaches right margin**.

In addition, you should follow the instructions of [this
section](#dev-contributing#git_commit_settings) (if you haven’t done so
already):

  - Install the Git hook for the `Change-Id` line.

  - Set up the HTTP access.

Setting up the HTTP access will allow you to commit changes via IntelliJ
without specifying your credentials. The Git hook won’t be noticeable
during a commit as it’s executed after the commit dialog of IntelliJ was
closed.

## Run configurations

Run configurations can be accessed on the toolbar. To edit them or add
new ones, choose **Edit Configurations** on the drop-down list of the
run configurations or go to **Run → Edit Configurations**.

### Pre-configured run configurations

In order to be able to use the pre-configured run configurations, the
following steps are necessary:

1.  Make sure that the folder `runConfigurations` exists within
    `$(project_data_directory)/.idea`. If it doesn’t exist, create it.

2.  Specify the IntelliJ path variable `GERRIT_TESTSITE`. (This
    configuration is shared among all IntelliJ projects.)
    
    1.  Go to **Settings → Appearance & Behavior → Path Variables**.
    
    2.  Click on the **+** to add a new path variable.
    
    3.  Specify `GERRIT_TESTSITE` as name and the path to your local
        test site as value.

The copied run configurations will be added automatically to the
available run configurations of the IntelliJ project.

#### Gerrit Daemon

> **Warning**
> 
> At the moment running this configuration results in a
> `java.io.FileNotFoundException`. To debug a local Gerrit server with
> IntelliJ, use the instructions of [Running the
> Daemon](#dev-readme#run_daemon) in combination with [Debugging a
> remote Gerrit server](#remote-debug).

Copy `$(gerrit_source_code)/tools/intellij/gerrit_daemon.xml` to
`$(project_data_directory)/.idea/runConfigurations/`.

This run configuration starts the Gerrit daemon similarly as [Running
the Daemon](#dev-readme#run_daemon).

> **Note**
> 
> The [Site Initialization](#dev-readme#init) has to be completed before
> this run configuration works properly.

### Unit tests

To create run configurations for unit tests, run or debug them via a
right-click on a method, class, file, or package. The created run
configuration is a temporary one and can be saved to make it permanent.

Normally, this approach generates JUnit run configurations. When the
Bazel plugin manages a project, it intercepts the creation and creates a
Bazel test run configuration instead, which can be used just like the
standard ones.

> **Tip**
> 
> If you would like to execute a test in NoteDb mode, add
> `--test_env=GERRIT_NOTEDB=READ_WRITE` to the **Bazel flags** of your
> run configuration.

### Debugging a remote Gerrit server

If a remote Gerrit server is running and has opened a debug port, you
can attach IntelliJ via a `Remote debug configuration`.

1.  Go to **Run → Edit Configurations**.

2.  Click on the **+** to add a new configuration.

3.  Choose **Remote**.

4.  Adjust **Configuration → Settings → Host** and **Port**.

5.  Start this configuration in `Debug` mode.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

