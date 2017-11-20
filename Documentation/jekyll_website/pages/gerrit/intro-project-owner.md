---
title: " Project Owner Guide"
sidebar: gerritdoc_sidebar
permalink: intro-project-owner.html
---
This is a Gerrit guide that is dedicated to project owners. It explains
the many possibilities that Gerrit provides to customize the workflows
for a project.

## What is a project owner?

Being project owner means that you own a project in Gerrit. Technically
this is expressed by having the
[Owner](access-control.html#category_owner) access right on `+refs/*+`
on that project. As project owner you have the permission to edit the
access control list and the project settings of the project. It also
means that you should get familiar with these settings so that you can
adapt them to the needs of your project.

Being project owner means being responsible for the administration of a
project. This requires having a deeper knowledge of Gerrit than the
average user. Normally per team there should be 2 to 3 persons, who have
a certain level of Git/Gerrit knowledge, assigned as project owners. It
normally doesn’t make sense that everyone in a team is project owner.
For normal team members it is sufficient to be committer or contributor.

## Access Rights

As a project owner you can edit the access control list of your project.
This allows you to grant permissions on the project to different groups.

Gerrit comes with a rich set of permissions which allow a very
fine-grained control over who can do what on a project. Access control
is one of the most powerful Gerrit features but it is also a rather
complex topic. This guide will only highlight the most important aspects
of access control, but the [Access Control](access-control.html) chapter
explains all the details.

### Editing Access Rights

To see the access rights of your project

  - go to the Gerrit Web UI

  - click on the `Projects` \> `List` menu entry

  - find your project in the project list and click on it

  - click on the `Access` menu entry

By clicking on the `Edit` button the access rights become editable and
you may save any changes by clicking on the `Save Changes` button.
Optionally you can provide a `Commit Message` to explain the reasons for
changing the access rights.

The access rights are stored in the project’s Git repository in a
special branch called `refs/meta/config`. On this branch there is a
`project.config` file which contains the access rights. More information
about this storage format can be found in the [Project Configuration
File Format](config-project-config.html) chapter. What is important to
know is that by looking at the history of the `project.config` file on
the `refs/meta/config` branch you can always see how the access rights
were changed and by whom. If a good commit message is provided you can
also see from the history why the access rights were modified.

If a Git browser such as gitweb is configured for the Gerrit server you
can find a link to the history of the `project.config` file in the Web
UI. Otherwise you may inspect the history locally. If you have cloned
the repository you can do this by executing the following commands:

``` 
  $ git fetch ssh://localhost:29418/project refs/meta/config
  $ git checkout FETCH_HEAD
  $ git log project.config
```

Non project owners may still edit the access rights and propose the
modifications to the project owners by clicking on the `Save for
Review` button. This creates a new change with the access rights
modifications that can be approved by a project owner. The project
owners are automatically added as reviewer on this change so that they
get informed about it by email.

### Inheritance

Normally when a new project is created in Gerrit it already has some
access rights which are inherited from the parent projects. Projects in
Gerrit are organized hierarchically as a tree with the ‘All-Projects’
project as root from which all projects inherit. Each project can have
only a single parent project, multi-inheritance is not supported.

Looking at the access rights of your project in the Gerrit Web UI, you
only see the access rights which are defined on that project. To see the
inherited access rights you must follow the link to the parent project
under `Rights Inherit From`.

Inherited access rights can be overwritten unless they are defined as
[BLOCK rule](access-control.html#block). BLOCK rules are used to limit
the possibilities of the project owners on the inheriting projects. With
this, global policies can be enforced on all projects. Please note that
Gerrit doesn’t prevent you from assigning access rights that contradict
an inherited BLOCK rule, but these access rights will simply have no
effect.

If you are responsible for several projects which require the same
permissions, it makes sense to have a common parent for them and to
maintain the access rights on that common parent. Changing the parent of
a project is only allowed for Gerrit administrators. This means you need
to contact the administrator of your Gerrit server if you want to
reparent your project. One way to do this is to change the parent
project in the Web UI, save the modifications for review and get the
change approved and merged by a Gerrit administrator.

## References

Access rights in Gerrit are assigned on references (aka refs). Refs in
Git exist in different namespaces, e.g. all branches normally exist
under `refs/heads/` and all tags under `refs/tags/`. In addition there
are a number of [special refs](access-control.html#references_special)
and [magic refs](access-control.html#references_magic).

Access rights can be assigned on a concrete ref, e.g.
`refs/heads/master` but also on ref patterns and regular expressions for
ref names.

A ref pattern ends with `+/*+` and describes a complete ref name
namespace, e.g. access rights assigned on `+refs/heads/*+` apply to all
branches.

Regular expressions must start with `^`, e.g. access rights assigned on
`+^refs/heads/rel-.*+` would apply to all `+rel-*+` branches.

## Groups

Access rights are granted to groups. It is useful to know that Gerrit
maintains its own groups internally but also supports different external
group backends.

The Gerrit internal groups can be seen in the Gerrit Web UI by clicking
on the `Groups` \> `List` menu entry. By clicking on a group you can
edit the group members (`Members` tab) and the group options (`General`
tab).

Gerrit internal groups contain users as members, but can also include
other groups, even external groups.

Every group is owned by an owner group. Only members of the owner group
can administrate the owned group (assign members, edit the group
options). A group can own itself; in this case members of the group can,
for example, add further members to the group. When you create new
groups for your project to assign access rights to committer or other
roles, make sure that they are owned by the project owner group.

An important setting on a group is the option `Make group visible to all
registered users.`, which defines whether non-members can see who is
member of the group.

New internal Gerrit groups can be created under `Groups` \> `Create New
Group`. This menu is only available if you have the global capability
[Create Group](access-control.html#capability_createGroup) assigned.

Gerrit also has a set of special [system
groups](access-control.html#system_groups) that you might find useful.

External groups need to be prefixed when assigning access rights to
them, e.g. [LDAP group names](access-control.html#ldap_groups) need to
be prefixed with `ldap/`.

If the
[singleusergroup](https://gerrit-review.googlesource.com/#/admin/projects/plugins/singleusergroup)
plugin is installed you can also directly assign access rights to users,
by prefixing the username with `user/` or the user’s account ID by
`userid/`.

## Common Access Rights

Different roles in a project, such as developer (committer) or
contributor, need different access rights. Examples for which access
rights are typically assigned for which role are described in the
[Access Control](access-control.html#example_roles) chapter.

## Code Review

Gerrit’s main functionality is code review, however using code review is
optional and you may decide to only use Gerrit as a Git server with
access control. Whether you allow only pushes for review or also direct
pushes depends on the project’s access rights.

To push a commit for review it must be pushed to
[refs/for/\<branch-name\>](access-control.html#refs_for). This means the
[Push](access-control.html#category_push_review) access right must be
assigned on `refs/for/<branch-name>`.

To allow direct pushes and bypass code review, the
[Push](access-control.html#category_push_direct) access right is
required on `refs/heads/<branch-name>`.

By pushing for review you are not only enabling the review workflow, but
you can also get [automatic verifications from a build
server](#continuous-integration) before changes are merged. In addition
you can benefit from Gerrit’s merge strategies that can automatically
merge/rebase commits on server side if necessary. You can control the
merge strategy by configuring the [submit
type](project-configuration.html#submit_type) on the project. If you
bypass code review you always need to merge/rebase manually if the tip
of the destination branch has moved. Please keep this in mind if you
choose to not work with code review because you think it’s easier to
avoid the additional complexity of the review workflow; it might
actually not be easier.

You may also enable [auto-merge on push](user-upload.html#auto_merge) to
benefit from the automatic merge/rebase on server side while pushing
directly into the repository.

## Project Options

As project owner you can control several options on your project. The
different options are described in the [Project
Options](project-configuration.html#project_options) section.

To see the options of your project

  - go to the Gerrit Web UI

  - click on the `Projects` \> `List` menu entry

  - find your project in the project list and click on it

  - click on the `General` menu entry

### Submit Type

An important decision for a project is the choice of the submit type and
the content merge setting (see the `Allow content merges` option). The
[submit type](project-configuration.html#submit_type) is the method
Gerrit uses to submit a change to the project. The submit type defines
what Gerrit should do on submit of a change if the destination branch
has moved while the change was in review. The [content
merge](project-configuration.html#content_merge) setting applies if the
same files have been modified concurrently and tells Gerrit whether it
should attempt a content merge for these files.

When choosing the submit type and the content merge setting one must
weigh development comfort against the safety of not breaking the
destination branch.

The most restrictive submit type is [Fast Forward
Only](project-configuration.html#fast_forward_only). Using this submit
type means that after submitting one change all other open changes for
the same destination branch must be rebased manually. This is quite
burdensome and in practice only feasible for branches with very few
changes. On the other hand, if changes are verified before submit, e.g.
automatically by a CI integration, with this submit type, you can be
sure that the destination branch never gets broken.

Choosing [Merge If
Necessary](project-configuration.html#merge_if_necessary) as submit type
makes the life for developers more comfortable, especially if content
merge is enabled. If this submit strategy is used developers only need
to rebase manually if the same files have been modified concurrently or
if the content merge on such a file fails. The drawback with this submit
type is that there is a risk of breaking the destination branch, e.g. if
one change moves a class into another package and another change imports
this class from the old location. Experience shows that in practice
`Merge If Necessary` with content merge enabled works pretty well and
breaking the destination branch happens rarely. This is why this setting
is recommended at least for development branches. You likely want to
start with `Merge If Necessary` with content merge enabled and only
switch to a more restrictive policy if you are facing issues with the
build and test stability of the destination branches.

It is also possible to define the submit type dynamically via
[Prolog](#prolog-submit-type). This way you can use different submit
types for different branches.

Please note that there are other submit types available; they are
described in the [Submit Type](project-configuration.html#submit_type)
section.

## Labels

The code review process includes that reviewers formally express their
opinion about a change by voting on different
[labels](config-labels.html). By default Gerrit comes with the
[Code-Review](config-labels.html#label_Code-Review) label and many
Gerrit servers have the [Verified](config-labels.html#label_Verified)
label configured globally. However projects can also define their own
[custom labels](config-labels.html#label_custom) to formalize
project-specific workflows. For example if a project requires an IP
approval from a special IP-team, it can define an `IP-Review` label and
grant permissions to the IP-team to vote on this label.

The behavior of a label can be controlled by its
[function](config-labels.html#label_function), e.g. it can be configured
whether a max positive voting on the label is required for submit or if
the voting on the label is optional.

By using a custom [submit rule](#submit-rules) it can be controlled per
change whether a label is required for submit or not.

A useful feature on labels is the possibility to automatically copy
scores forward to new patch sets if it was a [trivial
rebase](config-labels.html#label_copyAllScoresOnTrivialRebase) or if
[there was no code
change](config-labels.html#label_copyAllScoresIfNoCodeChange) (e.g. only
the commit message was edited).

## Submit Rules

A [submit rule](prolog-cookbook.html#SubmitRule) in Gerrit is logic that
defines when a change is submittable. By default, a change is
submittable when it gets at least one highest vote on each label and has
no lowest vote (aka veto vote) on any label.

The submit rules in Gerrit are implemented in
[Prolog](prolog-cookbook.html) and projects that need more flexibility
can define their own submit rules to decide when a change should be
submittable. A good [example](prolog-cookbook.html#NonAuthorCodeReview)
from the Prolog cookbook shows how to allow submit only if a change has
a `Code-Review+2` vote from a person that is not the change author. This
way a four-eyes principle for the reviews can be enforced.

A Prolog submit rule has access to
[information](prolog-change-facts.html) about the change for which it is
testing the submittability. Among others the list of the modified files
can be accessed, which allows special logic if certain files are
touched. For example, a common practice is to require a vote on an
additional label, like `Library-Compliance`, if the dependencies of the
project are changed.

It is also possible to control the [submit
type](prolog-cookbook.html#SubmitType) from Prolog. For example this can
be used to define a more restrictive submit type such as `Fast Forward
Only` for stable branches while using a more liberal submit type, e.g.
`Merge If Necessary` with content merge, for development branches. How
this can be done can be seen from an
[example](prolog-cookbook.html#SubmitTypePerBranch) in the Prolog
cookbook.

Submit rules are maintained in the
[rules.pl](prolog-cookbook.html#RulesFile) file in the
`refs/meta/config` branch of the project. How to write submit rules is
explained in the [Prolog
cookbook](prolog-cookbook.html#HowToWriteSubmitRules). There is also
good support for [testing submit
rules](prolog-cookbook.html#TestingSubmitRules) while developing them.

## Continuous Integration

With Gerrit you can have continuous integration builds not only for
updates of central branches but also whenever a new change/patch set is
uploaded for review. This way you get automatic verification of all
changes **before** they are merged and any build and test issues are
detected early. To indicate the build and test status the continuous
integration system normally votes with the
[Verified](config-labels.html#label_Verified) label on the change.

There are several solutions for integrating continuous integration
systems. The most commonly used are:

  - [Gerrit
    Trigger](https://wiki.jenkins-ci.org/display/JENKINS/Gerrit+Trigger)
    plugin for [Jenkins](http://jenkins-ci.org/)

  - [Zuul](http://www.mediawiki.org/wiki/Continuous_integration/Zuul)
    for [Jenkins](http://jenkins-ci.org/)

For the integration with the continuous integration system you must have
a service user that is able to access Gerrit. To create a service user
in Gerrit you can use the [create-account](cmd-create-account.html) SSH
command if the [Create
Account](access-control.html#capability_createAccount) global capability
is granted. If not, you need to ask a Gerrit administrator to create the
service user.

If the
[serviceuser](https://gerrit-review.googlesource.com/#/admin/projects/plugins/serviceuser)
plugin is installed you can also create new service users in the Gerrit
Web UI under `People` \> `Create Service User`. For this the `Create
Service User` global capability must be assigned.

The service user must have [read](access-control.html#category_read)
access to your project. In addition, if automatic change verification is
enabled, the service user must be allowed to vote on the
[Verified](config-labels.html#label_Verified) label.

Continuous integration systems usually integrate with Gerrit by
listening to the Gerrit [stream events](cmd-stream-events.html#events).
For this the service user must have the [Stream
Events](access-control.html#capability_streamEvents) global capability
assigned.

## Commit Validation

Gerrit provides an [extension point to do validation of new
commits](https://gerrit-review.googlesource.com/Documentation/config-validation.html#new-commit-validation).
A Gerrit plugin implementing this extension point can perform validation
checks when new commits are pushed to Gerrit. The plugin can either
provide a message to the client or reject the commit and cause the push
to fail.

There are some plugins available that provide commit
    validation:

  - [uploadvalidator](https://gerrit-review.googlesource.com/#/admin/projects/plugins/uploadvalidator):
    
    The `uploadvalidator` plugin allows project owners to configure
    blocked file extensions, required footers and a maximum allowed path
    length.

  - [commit-message-length-validator](https://gerrit-review.googlesource.com/#/admin/projects/plugins/commit-message-length-validator)
    
    The `commit-message-length-validator` core plugin validates that
    commit messages conform to line length limits.

## Branch Administration

As project owner you can administrate the branches of your project in
the Gerrit Web UI under `Projects` \> `List` \> \<your project\> \>
`Branches`. In the Web UI both [branch
creation](project-configuration.html#branch-creation) and [branch
deletion](project-configuration.html#branch-deletion) are allowed for
project owners without requiring any additional access rights.

By setting `HEAD` on the project you can define its [default
branch](project-configuration.html#default-branch). For convenience
reasons, when the repository is cloned Git creates a local branch for
this default branch and checks it out.

## Email Notifications

With Gerrit individual users control their own email subscriptions. By
editing the [watched projects](user-notify.html#user) in the Web UI
under `Settings` \> `Watched Projects` users can decide which events to
be informed about by email. The change notifications can be filtered by
[change search expressions](user-search.html).

This means as a project owner you normally don’t need to do anything
about email notifications, except maybe telling your project team where
to configure the watches.

Gerrit also supports [notifications on project
level](user-notify.html#project) that allow project owners to set up
email notifications for team mailing lists or groups of users. This
configuration is done in the `project.config` file in the
`refs/meta/config` branch as explained in the section about [project
level notifications](user-notify.html#project).

## Dashboards

Gerrit comes with a pre-defined user dashboard that provides a view of
the changes that are relevant for a user. Users can also define their
own [custom dashboards](user-dashboards.html) where the dashboard
sections can be freely configured. As a project owner you can configure
such custom dashboards on [project
level](user-dashboards.html#project-dashboards). This way you can define
a view of the changes that are relevant for your project and share this
dashboard with all users. The project dashboards can be seen in the Web
UI under `Projects` \> `List` \> \<your project\> \> `Dashboards`.

## Issue Tracker Integration

There are several possibilities to integrate issue trackers with Gerrit.

  - Comment Links
    
    As described in the [Comment Links](#comment-links) section, comment
    links can be used to link IDs from commit message footers to issues
    in an issue tracker system.

  - Tracking IDs
    
    Gerrit can be configured to index IDs from commit message footers so
    that the [tr/bug](user-search.html#tr) search operators can be used
    to query for changes with a certain ID. The [configuration of
    tracking IDs](config-gerrit.html#trackingid) can only be done
    globally by a Gerrit administrator.

  - Issue Tracker System Plugins
    
    There are Gerrit plugins for a tight integration with
    [Jira](https://gerrit-review.googlesource.com/#/admin/projects/plugins/its-jira),
    [Bugzilla](https://gerrit-review.googlesource.com//admin/projects/plugins/its-bugzilla)
    and [IBM Rational Team
    Concert](https://gerrit-review.googlesource.com//admin/projects/plugins/its-rtc).
    If installed, these plugins can e.g. be used to automatically add
    links to Gerrit changes to the issues in the issue tracker system or
    to automatically close an issue if the corresponding change is
    merged. If installed, project owners may enable/disable the issue
    tracker integration from the Gerrit Web UI under `Projects` \>
    `Lists` \> \<your project\> \> `General`.

## Comment Links

Gerrit can linkify strings in commit messages, summary comments and
inline comments. A string that matches a defined regular expression is
then displayed as hyperlink to a configured backend system.

So called comment links can be configured globally by a Gerrit
administrator, but also per project by the project owner. Comment links
on project level are defined in the `project.config` file in the
`refs/meta/config` branch of the project as described in the
documentation of the [commentlink](config-gerrit.html#commentlink)
configuration parameter.

Often comment links are used to link an ID in a commit message footer to
an issue in an issue tracker system. For example, to link the ID from
the `Bug` footer to Jira the following configuration can be used:

``` 
  [commentlink "myjira"]
    match = ([Bb][Uu][Gg]:\\s+)(\\S+)
    link =  https://myjira/browse/$2
```

## Reviewers

Normally it is not needed to explicitly assign reviewers to every change
since the project members either [watch the
project](user-notify.html#user) and get notified by email or regularly
check the list of open changes in the Gerrit Web UI. The project members
then pick the changes themselves that are interesting to them for
review.

If authors of changes want to have a review by a particular person (e.g.
someone who is known to be expert in the touched code area, or a
stakeholder for the implemented feature), they can request the review by
adding this person in the Gerrit Web UI as a reviewer on the change.
Gerrit will then notify this person by email about the review request.

With the
[reviewers](https://gerrit-review.googlesource.com/#/admin/projects/plugins/reviewers)
plugin it is possible to configure default reviewers who will be
automatically added to each change. The default reviewers can be
configured in the Gerrit Web UI under `Projects` \> `List` \> \<your
project\> \> `General` in the `reviewers Plugin` section.

The
[reviewers-by-blame](https://gerrit-review.googlesource.com/#/admin/projects/plugins/reviewers-by-blame)
plugin can automatically add reviewers to changes based on the [git
blame](https://www.kernel.org/pub/software/scm/git/docs/git-blame.html)
computation on the changed files. This means that the plugin will add
those users as reviewer that authored most of the lines touched by the
change, since these users should be familiar with the code and can most
likely review the change. How many reviewers the plugin will add to a
change at most can be configured in the Gerrit Web UI under `Projects`
\> `List` \> \<your project\> \> `General` in the `reviewers-by-blame
Plugin` section.

## Download Commands

On the change screen in the `Downloads` drop-down panel Gerrit offers
commands for downloading the currently viewed patch set.

The download commands are implemented by Gerrit plugins. This means that
the available download commands depend on the installed Gerrit
    plugins:

  - [download-commands](https://gerrit-review.googlesource.com/#/admin/projects/plugins/download-commands)
    plugin:
    
    The `download-commands` plugin provides the default download
    commands (`Checkout`, `Cherry Pick`, `Format Patch` and `Pull`).
    
    Gerrit administrators may configure which of the commands are shown
    on the change
    screen.

  - [egit](https://gerrit-review.googlesource.com/#/admin/projects/plugins/egit)
    plugin:
    
    The `egit` plugin provides the change ref as a download command,
    which is needed for downloading a change from within
    [EGit](https://www.eclipse.org/egit/).

  - [project-download-commands](https://gerrit-review.googlesource.com/#/admin/projects/plugins/project-download-commands)
    plugin:
    
    The `project-download-commands` plugin enables project owners to
    configure project-specific download commands. For example, a
    project-specific download command may update submodules, trigger a
    build, execute the tests or even do a deployment.
    
    The project-specific download commands must be configured in the
    `project.config` file in the `refs/meta/config` branch of the
    project:
    
    ``` 
      [plugin "project-download-commands"]
        Build = git fetch ${url} ${ref} && git checkout FETCH_HEAD && bazel build ${project}
        Update = git fetch ${url} ${ref} && git checkout FETCH_HEAD && git submodule update
    ```
    
    Project-specific download commands that are defined on a parent
    project are inherited by the child projects. A child project can
    overwrite an inherited download command, or remove it by assigning
    no value to it.

## Theme

Gerrit supports project-specific themes for customizing the appearance
of the change screen and the diff screens. It is possible to define an
HTML header and footer and to adapt Gerrit’s CSS. Details about themes
are explained in the [Themes](config-themes.html) section.

Project-specific themes can only be installed by Gerrit administrators
since the theme files must be copied into the Gerrit installation
folder.

## Integration with other tools

Gerrit provides many possibilities for the integration with other tools:

  - Stream Events:
    
    The [stream-events](cmd-stream-events.html) SSH command allows to
    listen to Gerrit [events](cmd-stream-events.html#events). Other
    tools can use this to react on actions done in Gerrit.
    
    The [Stream Events](access-control.html#capability_streamEvents)
    global capability is required for using the `stream-events` command.

  - REST API:
    
    Gerrit provides a rich [REST API](rest-api.html) that other tools
    can use to query information from Gerrit and and to trigger actions
    in Gerrit.

  - Gerrit Plugins:
    
    The Gerrit functionality can be extended by plugins and there are
    many extension points, e.g. plugins can
    
      - [add new menu entries](dev-plugins.html#top-menu-extensions)
    
      - [extend existing screens](dev-plugins.html#ui_extension) and
        [add new screens](dev-plugins.html#screen)
    
      - [do validation](config-validation.html), e.g. of new commits
    
      - add new REST endpoints and [SSH commands](dev-plugins.html#ssh)
    
    How to develop a Gerrit plugin is described in the [Plugin
    Development](dev-plugins.html) section.

## Project Lifecycle

### Project Creation

New projects can be created in the Gerrit Web UI under `Projects` \>
`Create Project`. The `Create Project` menu entry is only available if
you have the [Create
Project](access-control.html#capability_createProject) global capability
assigned.

Projects can also be created via REST or SSH as described in the
[Project Setup](project-configuration.html#project-creation) section.

Creating the project with an initial empty commit is generally
recommended because some tools have issues with cloning repositories
that are completely empty. However, if you plan to [import an existing
history](#import-history) into the new project, it is better to create
the project without an initial empty commit.

### Import Existing History

If you have an existing history you can import it into a Gerrit project.
To do this you need to have a local Git repository that contains this
history. If your existing codebase is in another VCS you must migrate it
to Git first. For Subversion you can use the [git
svn](http://git-scm.com/book/en/Git-and-Other-Systems-Git-and-Subversion)
command as described in the [Subversion migration
guide](http://git-scm.com/book/en/Git-and-Other-Systems-Migrating-to-Git#Subversion).
An importer for Perforce is available in the `contrib` section of the
Git source code; how to use [git p4](http://git-scm.com/docs/git-p4) to
do the import from Perforce is described in the [Perforce migration
guide](http://git-scm.com/book/en/Git-and-Other-Systems-Migrating-to-Git#Perforce).

To import an existing history into a Gerrit project you bypass code
review and push it directly to `refs/heads/<branch>`. For this you must
have the corresponding [Push](access-control.html#category_push_direct)
access right assigned. If the destination branch in the Gerrit
repository already contains a history (e.g. an initial empty commit),
you can overwrite it by doing a force push. In this case force push must
be allowed in the access controls of the project.

Some Gerrit servers may disallow forging committers by blocking the
[Forge Committer](access-control.html#category_forge_committer) access
right globally. In this case you must use the [git
filter-branch](https://www.kernel.org/pub/software/scm/git/docs/git-filter-branch.html)
command to rewrite the committer information for all commits (the author
information that records who was writing the code stays intact; signed
tags will lose their
signature):

``` 
  $ git filter-branch --tag-name-filter cat --env-filter 'GIT_COMMITTER_NAME="John Doe"; GIT_COMMITTER_EMAIL="john.doe@example.com";' -- --all
```

If a [max object size
limit](config-gerrit.html#receive.maxObjectSizeLimit) is configured on
the server you may need to remove large objects from the history before
you are able to push. To find large objects in the history of your
project you can use the `reposize.sh` script which you can download from
Gerrit:

``` 
  $ curl -Lo reposize.sh http://review.example.com:8080/tools/scripts/reposize.sh

or

  $ scp -p -P 29418 john.doe@review.example.com:scripts/reposize.sh .
```

You can then use the [git
filter-branch](https://www.kernel.org/pub/software/scm/git/docs/git-filter-branch.html)
command to remove the large objects from the history of all
branches:

``` 
  $ git filter-branch -f --index-filter 'git rm --cached --ignore-unmatch path/to/large-file.jar' -- --all
```

Since this command rewrites all commits in the repository it’s a good
idea to create a fresh clone from this rewritten repository before
pushing to Gerrit, this will ensure that the original objects which have
been rewritten are removed.

### Project Deletion

Gerrit core does not support the deletion of projects.

If the
[delete-project](https://gerrit-review.googlesource.com/#/admin/projects/plugins/delete-project)
plugin is installed, projects can be deleted from the Gerrit Web UI
under `Projects` \> `List` \> \<project\> \> `General` by clicking on
the `Delete` command under `Project Commands`. The `Delete` command is
only available if you have the `Delete Projects` global capability
assigned, or if you own the project and you have the `Delete Own
Projects` global capability assigned. If neither of these capabilities
is granted, you need to contact a Gerrit administrator to request the
deletion of your project.

Instead of deleting a project you may set the [project
state](project-configuration.html#project-state) to `ReadOnly` or
`Hidden`.

### Project Rename

Gerrit core does not support the renaming of projects.

As workaround you may

1.  [create a new project](#project-creation) with the new name

2.  [import the history of the old project](#import-history)

3.  [delete the old project](#project-deletion)

Please note that a drawback of this workaround is that the whole review
history (changes, review comments) is lost.

Alternatively, you can use the
[importer](https://gerrit.googlesource.com/plugins/importer/) plugin to
copy the project *including the review history*, and then [delete the
old project](#project-deletion).

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

