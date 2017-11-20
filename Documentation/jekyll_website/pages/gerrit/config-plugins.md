---
title: " Plugins"
sidebar: gerritdoc_sidebar
permalink: config-plugins.html
---
The Gerrit server functionality can be extended by installing plugins.

## Plugin Installation

Plugin installation is as easy as dropping the plugin jar into the
`$site_path/plugins/` folder. It may take [a few
minutes](config-gerrit.html#plugins.checkFrequency) until the server
picks up new and updated plugins.

Plugins can also be installed via
[REST](rest-api-plugins.html#install-plugin) and
[SSH](cmd-plugin-install.html).

## Plugin Development

How to develop plugins is described in the [Plugin Development
Guide](dev-plugins.html).

If you want to share your plugin under the [Apache License
2.0](licenses.html#Apache2_0) you can host your plugin development on
the [gerrit-review](https://gerrit-review.googlesource.com) Gerrit
Server. You can request the creation of a new Project by email to the
[Gerrit mailing
list](https://groups.google.com/forum/#!forum/repo-discuss). You would
be assigned as project owner of the new plugin project so that you can
submit changes on your own. It is the responsibility of the project
owner to maintain the plugin, e.g. to make sure that it works with new
Gerrit versions and to create stable branches for old releases.

## Core Plugins

Core plugins are packaged within the Gerrit war file and can easily be
installed during the [Gerrit initialization](pgm-init.html).

The core plugins are developed and maintained by the Gerrit maintainers
and the Gerrit community.

### commit-message-length-validator

This plugin checks the length of a commit’s commit message subject and
message body, and reports warnings or errors to the git client if the
lengths are
exceeded.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/commit-message-length-validator)
|
[Documentation](https://gerrit.googlesource.com/plugins/commit-message-length-validator/+doc/master/src/main/resources/Documentation/about.md)
|
[Configuration](https://gerrit.googlesource.com/plugins/commit-message-length-validator/+doc/master/src/main/resources/Documentation/config.md)

### cookbook-plugin

Sample plugin to demonstrate features of Gerrit’s plugin
API.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/cookbook-plugin)
|
[Documentation](https://gerrit.googlesource.com/plugins/cookbook-plugin/+doc/master/src/main/resources/Documentation/about.md)

### download-commands

This plugin defines commands for downloading changes in different
download schemes (for downloading via different network
protocols).

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/download-commands)
|
[Documentation](https://gerrit.googlesource.com/plugins/download-commands/+doc/master/src/main/resources/Documentation/about.md)
|
[Configuration](https://gerrit.googlesource.com/plugins/download-commands/+doc/master/src/main/resources/Documentation/config.md)

### hooks

This plugin runs server-side hooks on
events.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/hooks)
|
[Documentation](https://gerrit.googlesource.com/plugins/hooks/+doc/master/src/main/resources/Documentation/about.md)
|
[Configuration](https://gerrit.googlesource.com/plugins/hooks/+doc/master/src/main/resources/Documentation/config.md)

### replication

This plugin can automatically push any changes Gerrit Code Review makes
to its managed Git repositories to another system. Usually this would be
configured to provide mirroring of changes, for warm-standby backups, or
a load-balanced public mirror
farm.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/replication)
|
[Documentation](https://gerrit.googlesource.com/plugins/replication/+doc/master/src/main/resources/Documentation/about.md)
|
[Configuration](https://gerrit.googlesource.com/plugins/replication/+doc/master/src/main/resources/Documentation/config.md)

### reviewnotes

Stores review information for Gerrit changes in the `refs/notes/review`
branch.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/reviewnotes)
|
[Documentation](https://gerrit.googlesource.com/plugins/reviewnotes/+doc/master/src/main/resources/Documentation/about.md)

### review-strategy

This plugin allows users to configure different review
strategies.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/review-strategy)
|
[Documentation](https://gerrit.googlesource.com/plugins/review-strategy/+/master/src/main/resources/Documentation/about.md)

### singleusergroup

This plugin provides a group per user. This is useful to assign access
rights directly to a single user, since in Gerrit access rights can only
be assigned to groups.

## Other Plugins

Besides core plugins there are many other Gerrit plugins available.
These plugins are developed and maintained by different parties. The
Gerrit Project doesn’t guarantee proper functionality of any of these
plugins.

The Gerrit Project doesn’t provide binaries for these plugins, but there
is one public service that offers the download of pre-built plugin jars:

  - [CI Server from GerritForge](https://gerrit-ci.gerritforge.com)

The following list gives an overview of available plugins, but the list
may not be complete. You may discover more plugins on
[gerrit-review](https://gerrit-review.googlesource.com/#/admin/projects/?filter=plugins%252F).

### admin-console

Plugin to provide administrator-only functionality, intended to simplify
common administrative tasks. Currently providing user-level information.
Also providing access control information by project or
project/account.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/admin-console)
|
[Documentation](https://gerrit.googlesource.com/plugins/admin-console/+doc/master/src/main/resources/Documentation/about.md)

### avatars-external

This plugin allows to use an external url to load the avatar images
from.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/avatars-external)
|
[Documentation](https://gerrit.googlesource.com/plugins/avatars-external/+doc/master/src/main/resources/Documentation/about.md)
|
[Configuration](https://gerrit.googlesource.com/plugins/avatars-external/+doc/master/src/main/resources/Documentation/config.md)

### avatars-gravatar

Plugin to display user icons from
Gravatar.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/avatars-gravatar)

### branch-network

This plugin allows the rendering of Git repository branch network in a
graphical HTML5 Canvas. It is mainly intended to be used as a "project
link" in a gitweb configuration or by other Gerrit GWT UI plugins to be
plugged elsewhere in
Gerrit.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/branch-network)
|
[Documentation](https://gerrit.googlesource.com/plugins/branch-network/+doc/master/src/main/resources/Documentation/about.md)
|
[Configuration](https://gerrit.googlesource.com/plugins/branch-network/+doc/master/src/main/resources/Documentation/config.md)

### changemessage

This plugin allows to display a static info message on the change
screen.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/changemessage)
| [Plugin
Documentation](https://gerrit.googlesource.com/plugins/changemessage/+doc/master/src/main/resources/Documentation/about.md)
|
[Configuration](https://gerrit.googlesource.com/plugins/changemessage/+doc/master/src/main/resources/Documentation/config.md)

### delete-project

Provides the ability to delete a
project.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/delete-project)
|
[Documentation](https://gerrit.googlesource.com/plugins/delete-project/+doc/master/src/main/resources/Documentation/about.md)

### egit

This plugin provides extensions for easier usage with EGit.

The plugin adds a download command for EGit that allows to copy only the
change ref into the clipboard. The change ref is needed for downloading
a Gerrit change from within
EGit.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/egit)
|
[Documentation](https://gerrit.googlesource.com/plugins/egit/+doc/master/src/main/resources/Documentation/about.md)

### emoticons

This plugin allows users to see emoticons in comments as
images.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/emoticons)
|
[Documentation](https://gerrit.googlesource.com/plugins/emoticons/+doc/master/src/main/resources/Documentation/about.md)
|
[Configuration](https://gerrit.googlesource.com/plugins/emoticons/+doc/master/src/main/resources/Documentation/config.md)

### gitblit

GitBlit code-viewer plugin with SSO and Security Access
Control.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/gitblit)

### github

Plugin to integrate with GitHub: replication, pull-request to
Change-Sets

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/github)

### gitiles

Plugin running Gitiles alongside a Gerrit
server.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/gitiles)

### imagare

The imagare plugin allows Gerrit users to upload and share
images.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/imagare)
|
[Documentation](https://gerrit.googlesource.com/plugins/imagare/+doc/master/src/main/resources/Documentation/about.md)
|
[Configuration](https://gerrit.googlesource.com/plugins/imagare/+doc/master/src/main/resources/Documentation/config.md)

### importer

The importer plugin allows to import projects from one Gerrit server
into another Gerrit server.

Projects can be imported while both source and target Gerrit server are
online. There is no downtime required.

The git repository and all changes of the project, including approvals
and review comments, are imported. Historic timestamps are preserved.

Project imports can be resumed. This means a project team can continue
to work in the source system while the import to the target system is
done. By resuming the import the project in the target system can be
updated with the missing delta.

The importer plugin can also be used to copy a project within one Gerrit
server, and in combination with the [delete-project](#delete-project)
plugin it can be used to rename a
project.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/importer)
|
[Documentation](https://gerrit.googlesource.com/plugins/importer/+doc/master/src/main/resources/Documentation/about.md)

### Issue Tracker System Plugins

Plugins to integrate with issue tracker systems (ITS), that (based on
events in Gerrit) allows to take actions in the ITS. For example, they
can add comments to bugs, or change status of bugs.

All its-plugins have a common base implementation which is stored in the
`its-base` project. `its-base` is not a plugin, but just a framework for
the ITS plugins which is packaged within each ITS plugin.

[its-base
Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/its-base)
| [its-base
Documentation](https://gerrit.googlesource.com/plugins/its-base/+doc/master/src/main/resources/Documentation/about.md)
| [its-base
Configuration](https://gerrit.googlesource.com/plugins/its-base/+doc/master/src/main/resources/Documentation/config.md)

#### its-bugzilla

Plugin to integrate with
Bugzilla.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/its-bugzilla)
|
[Documentation](https://gerrit.googlesource.com/plugins/its-bugzilla/+doc/master/src/main/resources/Documentation/about.md)

#### its-jira

Plugin to integrate with
Jira.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/its-jira)
|
[Configuration](https://gerrit.googlesource.com/plugins/its-jira/+doc/master/src/main/resources/Documentation/config.md)

#### its-rtc

Plugin to integrate with IBM Rational Team Concert
(RTC).

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/its-rtc)
|
[Configuration](https://gerrit.googlesource.com/plugins/its-rtc/+doc/master/src/main/resources/Documentation/config.md)

#### its-storyboard

Plugin to integrate with Storyboard task tracking
system.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/its-storyboard)
|
[Documentation](https://gerrit.googlesource.com/plugins/its-storyboard/+doc/master/src/main/resources/Documentation/about.md)

### javamelody

This plugin allows to monitor the Gerrit server.

This plugin integrates JavaMelody in Gerrit in order to retrieve live
instrumentation data from
Gerrit.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/javamelody)
|
[Documentation](https://gerrit.googlesource.com/plugins/javamelody/+doc/master/src/main/resources/Documentation/about.md)
|
[Configuration](https://gerrit.googlesource.com/plugins/javamelody/+doc/master/src/main/resources/Documentation/config.md)

### labelui

The labelui plugin adds a user preference that allows users to choose a
table control to render the labels/approvals on the change screen
(similar to how labels/approvals were rendered on the old change
screen).

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/labelui)
|
[Documentation](https://gerrit.googlesource.com/plugins/labelui/+doc/master/src/main/resources/Documentation/about.md)

### menuextender

The menuextender plugin allows Gerrit administrators to configure
additional menu entries from the
WebUI.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/menuextender)
|
[Documentation](https://gerrit.googlesource.com/plugins/menuextender/+doc/master/src/main/resources/Documentation/about.md)
|
[Configuration](https://gerrit.googlesource.com/plugins/menuextender/+doc/master/src/main/resources/Documentation/config.md)

### metrics-reporter-elasticsearch

This plugin reports Gerrit metrics to
Elasticsearch.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/metrics-reporter-elasticsearch).

### metrics-reporter-graphite

This plugin reports Gerrit metrics to
Graphite.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/metrics-reporter-graphite).

### metrics-reporter-jmx

This plugin reports Gerrit metrics to
JMX.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/metrics-reporter-jmx).

### motd

This plugin can output messages to clients when pulling/fetching/cloning
code from Gerrit Code Review. If the client (and transport mechanism)
can support sending the message to the client, it will be displayed to
the user (usually prefixed by “remote: ”), but will be silently
discarded
otherwise.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/motd)
|
[Documentation](https://gerrit.googlesource.com/plugins/motd/+doc/master/src/main/resources/Documentation/about.md)
|
[Configuration](https://gerrit.googlesource.com/plugins/motd/+doc/master/src/main/resources/Documentation/config.md)

### OAuth authentication provider

This plugin enables Gerrit to use OAuth2 protocol for authentication.
Two different OAuth providers are supported:

  - GitHub

  - Google

[Project](https://github.com/davido/gerrit-oauth-provider) |
[Configuration](https://github.com/davido/gerrit-oauth-provider/wiki/Getting-Started)

### owners

This plugin provides a Prolog predicate `add_owner_approval/3` that
appends `label('Owner-Approval', need(_))` to a provided
list.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/owners)
|
[Documentation](https://gerrit.googlesource.com/plugins/owners/+/refs/heads/master/README.md)

### project-download-commands

This plugin adds support for project specific download commands.

Project specific download commands that are defined on a parent project
are inherited by the child projects. Child projects can overwrite the
inherited download command or remove it by assigning no value to
it.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/project-download-commands)
|
[Documentation](https://gerrit.googlesource.com/plugins/project-download-commands/+doc/master/src/main/resources/Documentation/about.md)
|
[Configuration](https://gerrit.googlesource.com/plugins/project-download-commands/+doc/master/src/main/resources/Documentation/config.md)

### quota

This plugin allows to enforce quotas in Gerrit.

To protect a Gerrit installation it makes sense to limit the resources
that a project or group can consume. To do this a Gerrit administrator
can use this plugin to define quotas on project
namespaces.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/quota)
|
[Documentation](https://gerrit.googlesource.com/plugins/quota/+doc/master/src/main/resources/Documentation/about.md)
[Configuration](https://gerrit.googlesource.com/plugins/quota/+doc/master/src/main/resources/Documentation/config.md)

### ref-protection

A plugin that protects against commits being lost.

Backups of deleted or non-fast-forward updated refs are created under
the `refs/backups/`
namespace.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/ref-protection)
|
[Documentation](https://gerrit.googlesource.com/plugins/ref-protection/+/refs/heads/stable-2.11/src/main/resources/Documentation/about.md)

### reparent

A plugin that provides project reparenting as a self-service for project
owners.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/reparent)
|
[Documentation](https://gerrit.googlesource.com/plugins/reparent/+doc/master/src/main/resources/Documentation/about.md)
|
[Configuration](https://gerrit.googlesource.com/plugins/reparent/+doc/master/src/main/resources/Documentation/config.md)

### reviewers

A plugin that allows adding default reviewers to a
change.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/reviewers)
|
[Documentation](https://gerrit.googlesource.com/plugins/reviewers/+doc/master/src/main/resources/Documentation/about.md)
|
[Configuration](https://gerrit.googlesource.com/plugins/reviewers/+doc/master/src/main/resources/Documentation/config.md)

### reviewers-by-blame

A plugin that allows automatically adding reviewers to a change from the
git blame computation on the changed files. It will add the users that
authored most of the lines touched by the change, since these users
should be familiar with the code and can mostly review the
change.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/reviewers-by-blame)
|
[Documentation](https://gerrit.googlesource.com/plugins/reviewers-by-blame/+doc/master/src/main/resources/Documentation/about.md)
|
[Configuration](https://gerrit.googlesource.com/plugins/reviewers-by-blame/+doc/master/src/main/resources/Documentation/config.md)

### scripting/groovy-provider

This plugin provides a Groovy runtime environment for Gerrit plugins in
Groovy.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/scripting/groovy-provider)
|
[Documentation](https://gerrit.googlesource.com/plugins/scripting/groovy-provider/+doc/master/src/main/resources/Documentation/about.md)

### scripting/scala-provider

This plugin provides a Scala runtime environment for Gerrit plugins in
Scala.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/scripting/scala-provider)
|
[Documentation](https://gerrit.googlesource.com/plugins/scripting/scala-provider/+doc/master/src/main/resources/Documentation/about.md)

### scripts

Repository containing a collection of Gerrit scripting plugins that are
intended to provide simple and useful extensions.

Groovy and Scala scripts require the installation of the corresponding
scripting/\*-provider plugin in order to be loaded into
Gerrit.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/scripts)
[Documentation](https://gerrit.googlesource.com/plugins/scripts/+doc/master/README.md)

### server-config

This plugin enables access (download and upload) to the server config
files. It may be used to change Gerrit config files (like
`etc/gerrit.config`) in cases where direct access to the file system
where Gerrit’s config files are stored is difficult or impossible to
get.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/server-config)

### serviceuser

This plugin allows to create service users in Gerrit.

A service user is a user that is used by another service to communicate
with Gerrit. E.g. a service user is needed to run the Gerrit Trigger
Plugin in Jenkins. A service user is not able to login into the Gerrit
WebUI and it cannot push commits or
tags.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/serviceuser)
|
[Documentation](https://gerrit.googlesource.com/plugins/serviceuser/+doc/master/src/main/resources/Documentation/about.md)
|
[Configuration](https://gerrit.googlesource.com/plugins/serviceuser/+doc/master/src/main/resources/Documentation/config.md)

### uploadvalidator

This plugin allows to configure upload validations per project.

Project owners can configure blocked file extensions, required footers
and a maximum allowed path length. Pushes of commits that violate these
settings are rejected by
Gerrit.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/uploadvalidator)
|
[Documentation](https://gerrit.googlesource.com/plugins/uploadvalidator/+doc/master/src/main/resources/Documentation/about.md)
|
[Configuration](https://gerrit.googlesource.com/plugins/uploadvalidator/+doc/master/src/main/resources/Documentation/config.md)

### verify-status

This plugin adds a separate channel for Gerrit to store test metadata
and view them on the Gerrit UI. The metadata can be stored in the Gerrit
database or in a completely separate
datastore.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/verify-status)
|
[Documentation](https://gerrit.googlesource.com/plugins/verify-status/+doc/master/src/main/resources/Documentation/about.md)
|
[Configuration](https://gerrit.googlesource.com/plugins/verify-status/+doc/master/src/main/resources/Documentation/database.md)

### websession-flatfile

This plugin replaces the built-in Gerrit H2 based websession cache with
a flatfile based implementation. This implementation is shareable among
multiple Gerrit servers, making it useful for multi-master Gerrit
installations.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/websession-flatfile)
|
[Documentation](https://gerrit.googlesource.com/plugins/websession-flatfile/+doc/master/src/main/resources/Documentation/about.md)
|
[Configuration](https://gerrit.googlesource.com/plugins/websession-flatfile/+doc/master/src/main/resources/Documentation/config.md)

### x-docs

This plugin serves project documentation as HTML
pages.

[Project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/x-docs)
|
[Documentation](https://gerrit.googlesource.com/plugins/x-docs/+doc/master/src/main/resources/Documentation/about.md)
|
[Configuration](https://gerrit.googlesource.com/plugins/x-docs/+doc/master/src/main/resources/Documentation/config.md)

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

