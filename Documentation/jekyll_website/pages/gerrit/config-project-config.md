---
title: " Gerrit Code Review - Project Configuration File Format"
sidebar: gerritdoc_sidebar
permalink: config-project-config.html
---
This page explains the storage format of Gerrit’s project configuration
and access control models.

The web UI access control panel is a front end for human-readable
configuration files under the `refs/meta/config` namespace in the
affected project. Direct manipulation of these files is mainly relevant
in an automation scenario of the access controls.

## The `refs/meta/config` namespace

The namespace contains three different files that play different roles
in the permission model. With read permission to that reference, it is
possible to fetch the `refs/meta/config` reference to a local
repository. A nice side effect is that you can also upload changes to
project permissions and review them just like with regular code changes.
The preview changes option is also provided on the UI. Please note that
you will have to configure push rights for the `refs/meta/config` name
space if you’d like to use the possibility to automate permission
updates.

## Property inheritance

If a property is set to INHERIT, then the value from the parent project
is used. If the property is not set in any parent project, the default
value is FALSE.

## The file `project.config`

The `project.config` file contains the link between groups and their
permitted actions on reference patterns in this project and any projects
that inherit its permissions.

The format in this file corresponds to the Git config file format, so if
you want to automate your permissions it is a good idea to use the `git
config` command when writing to the file. This way you know you don’t
accidentally break the format of the file.

Here follows a `git config` command
    example:

    $ git config -f project.config project.description "Rights inherited by all other projects"

Below you will find an example of the `project.config` file format:

    [project]
           description = Rights inherited by all other projects
    [access "refs/*"]
           read = group Administrators
    [access "refs/heads/*"]
            label-Your-Label-Here = -1..+1 group Administrators
    [capability]
           administrateServer = group Administrators
    [receive]
           requireContributorAgreement = false
    [label "Your-Label-Here"]
            function = MaxWithBlock
            value = -1 Your -1 Description
            value =  0 Your No score Description
            value = +1 Your +1 Description

As you can see, there are several sections.

The [`project` section](#project-section) appears once per project.

The [`access` section](#access-section) appears once per reference
pattern, such as `+refs/*+` or `+refs/heads/*+`. Only one access section
per pattern is allowed.

The [`receive` section](#receive-section) appears once per project.

The [`submit` section](#submit-section) appears once per project.

The [`capability`](#capability-section) section only appears once, and
only in the `All-Projects` repository. It controls core features that
are configured on a global level.

The [`label`](#label-section) section can appear multiple times. You can
also redefine the text and behavior of the built in label types
`Code-Review` and `Verified`.

Optionally a `commentlink` section can be added to define
project-specific comment links. The `commentlink` section has the same
format as the [`commentlink` section in
gerrit.config](config-gerrit.html#commentlink) which is used to define
global comment links.

### Project section

The project section includes configuration of project settings.

These are the keys:

  - Description

### Receive section

The receive section includes configuration of project-specific receive
settings:

  - receive.requireContributorAgreement  
    Controls whether or not a user must complete a contributor agreement
    before they can upload changes. Default is `INHERIT`. If
    `All-Project` enables this option then the dependent project must
    set it to false if users are not required to sign a contributor
    agreement prior to submitting changes for that specific project. To
    use that feature the global option in `gerrit.config` must be
    enabled:
    [auth.contributorAgreements](config-gerrit.html#auth.contributorAgreements).

  - receive.requireSignedOffBy  
    Sign-off can be a requirement for some projects (for example Linux
    kernel uses it). Sign-off is a line at the end of the commit message
    which certifies who is the author of the commit. Its main purpose is
    to improve tracking of who did what, especially with patches.
    Default is `INHERIT`, which means that this property is inherited
    from the parent project.

  - receive.requireChangeId  
    Controls whether or not the Change-Id must be included in the commit
    message in the last paragraph. Default is `INHERIT`, which means
    that this property is inherited from the parent project.

  - receive.maxObjectSizeLimit  
    Maximum allowed Git object size that receive-pack will accept. If an
    object is larger than the given size the pack-parsing will abort and
    the push operation will fail. If set to zero then there is no limit.
    
    Project owners can use this setting to prevent developers from
    pushing objects which are too large to Gerrit. This setting can also
    be set it `gerrit.config` globally
    [receive.maxObjectSizeLimit](config-gerrit.html#receive.maxObjectSizeLimit).
    
    The project specific setting in `project.config` is only honored
    when it further reduces the global limit.
    
    Default is zero.
    
    Common unit suffixes of k, m, or g are supported.

  - receive.checkReceivedObjects  
    Controls whether or not the JGit functionality for checking received
    objects is enabled.
    
    By default Gerrit checks the validity of git objects. Setting this
    variable to false should not be used unless a project with history
    containing invalid objects needs to be pushed into a Gerrit
    repository.
    
    This functionality is provided as some other git implementations
    have allowed bad history to be written into git repositories. If
    these repositories need pushing up to Gerrit then the JGit checks
    need to be disabled.
    
    The default value for this is true, false disables the checks.

  - receive.enableSignedPush  
    Controls whether server-side signed push validation is enabled on
    the project. Only has an effect if signed push validation is enabled
    on the server; see the [global
    configuration](config-gerrit.html#receive.enableSignedPush) for
    details.
    
    Default is `INHERIT`, which means that this property is inherited
    from the parent project.

  - receive.requireSignedPush  
    Controls whether server-side signed push validation is required on
    the project. Only has an effect if signed push validation is enabled
    on the server, and link:\#receive.enableSignedPush is set on the
    project. See the [global
    configuration](config-gerrit.html#receive.enableSignedPush) for
    details.
    
    Default is `INHERIT`, which means that this property is inherited
    from the parent project.

  - receive.rejectImplicitMerges  
    Controls whether a check for implicit merges will be performed when
    changes are pushed for review. An implicit merge is a case where
    merging an open change would implicitly merge another branch into
    the target branch. Typically, this happens when a change is done on
    master and, by mistake, pushed to a stable branch for review. When
    submitting such change, master would be implicitly merged into
    stable without anyone noticing that. When this option is set to
    *true* Gerrit will reject the push if an implicit merge is detected.
    
    This check is only done for non-merge commits, merge commits are not
    subject of the implicit merge check.
    
    Default is `INHERIT`, which means that this property is inherited
    from the parent project.

### Change section

The change section includes configuration for project-specific change
settings:

  - change.privateByDefault  
    Controls whether all new changes in the project are set as private
    by default.
    
    Note that a new change will be public if the `is_private` field in
    [ChangeInput](rest-api-changes.html#change-input) is set to `false`
    explicitly when calling the
    [CreateChange](rest-api-changes.html#create-change) REST API or the
    `remove-private` [PushOption](user-upload.html#private) is used
    during the Git push.
    
    Default is `INHERIT`, which means that this property is inherited
    from the parent project.

### Submit section

The submit section includes configuration of project-specific submit
settings:

  - *mergeContent*: Defines whether to automatically merge changes.
    Valid values are *true*, *false*, or *INHERIT*. Default is
    *INHERIT*.

  - *action*: defines the [submit
    type](project-configuration.html#submit_type). Valid values are
    *fast forward only*, *merge if necessary*, *rebase if necessary*,
    *merge always* and *cherry pick*. The default is *merge if
    necessary*.

  - *matchAuthorToCommitterDate*: Defines whether to the author date
    will be changed to match the submitter date upon submit, so that git
    log shows when the change was submitted instead of when the author
    last committed. Valid values are *true*, *false*, or *INHERIT*. The
    default is *INHERIT*. This option only takes effect in submit
    strategies which already modify the commit, i.e. Cherry Pick, Rebase
    Always, and (perhaps) Rebase If Necessary.

Merge strategy

### Access section

Each `access` section includes a reference and access rights connected
to groups. Each group listed must exist in the [`groups`
file](#file-groups).

Please refer to the [Access
Categories](access-control.html#access_categories) documentation for a
full list of available access rights.

### MIME Types section

The `mimetype` section may be configured to force the web code reviewer
to return certain MIME types by file path. MIME types may be used to
activate syntax highlighting.

    [mimetype "text/x-c"]
      path = *.pkt
    [mimetype "text/x-java"]
      path = api/current.txt

### Capability section

The `capability` section only appears once, and only in the
`All-Projects` repository. It controls Gerrit administration
capabilities that are configured on a global level.

Please refer to the [Global
Capabilities](access-control.html#global_capabilities) documentation for
a full list of available capabilities.

### Label section

Please refer to [Custom Labels](config-labels.html#label_custom)
documentation.

### branchOrder section

Defines a branch ordering which is used for backporting of changes.
Backporting will be offered for a change (in the Gerrit UI) for all more
stable branches where the change can merge cleanly.

  - branchOrder.branch  
    A branch name, typically multiple values will be defined. The order
    of branch names in this section defines the branch order. The
    topmost is considered to be the least stable branch (typically the
    master branch) and the last one the most stable (typically the last
    maintained release branch).

Example:

    [branchOrder]
      branch = master
      branch = stable-2.9
      branch = stable-2.8
      branch = stable-2.7

The `branchOrder` section is inheritable. This is useful when multiple
or all projects follow the same branch rules. A `branchOrder` section in
a child project completely overrides any `branchOrder` section from a
parent i.e. there is no merging of `branchOrder` sections. A present but
empty `branchOrder` section removes all inherited branch order.

Branches not listed in this section will not be included in the
mergeability check. If the `branchOrder` section is not defined then the
mergeability of a change into other branches will not be done.

### reviewer section

Defines config options to adjust a project’s reviewer workflow such as
enabling reviewers and CCs by email.

  - reviewer.enableByEmail  
    A boolean indicating if reviewers and CCs that do not currently have
    a Gerrit account can be added to a change by providing their email
    address.

This setting only takes affect for changes that are readable by
anonymous users.

Default is `INHERIT`, which means that this property is inherited from
the parent project. If the property is not set in any parent project,
the default value is `FALSE`.

## The file `groups`

Each group in this list is linked with its UUID so that renaming of
groups is possible without having to rewrite every `groups` file in
every repository where it’s used.

This is what the default groups file for `All-Projects.git` looks like:

    # UUID                                         Group Name
    #
    3d6da7dc4e99e6f6e5b5196e21b6f504fc530bba       Administrators
    global:Anonymous-Users                         Anonymous Users
    global:Change-Owner                            Change Owner
    global:Project-Owners                          Project Owners
    global:Registered-Users                        Registered Users

This file can’t be written to by the `git config` command.

In order to reference a group in `project.config`, it must be listed in
the `groups` file. When editing permissions through the web UI this file
is maintained automatically, but when pushing updates to
`refs/meta/config` this must be dealt with by hand. Gerrit will refuse
`project.config` files that refer to groups not listed in `groups`.

The UUID of a group can be found on the General tab of the group’s page
in the web UI or via the `-v` option to [the `ls-groups` SSH
command](cmd-ls-groups.html).

## The file `rules.pl`

The `rules.pl` files allows you to replace or amend the default Prolog
rules that control e.g. what conditions need to be fulfilled for a
change to be submittable. This file content should be interpretable by
the *Prolog Cafe* interpreter.

You can read more about the `rules.pl` file and the prolog rules on [the
Prolog cookbook page](prolog-cookbook.html).

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

