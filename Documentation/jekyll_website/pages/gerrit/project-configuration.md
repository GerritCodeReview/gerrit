---
title: " Gerrit Code Review - Project Configuration"
sidebar: gerritdoc_sidebar
permalink: project-configuration.html
---
## Project Creation

There are several ways to create a new project in Gerrit:

  - in the Web UI under *Projects* \> *Create Project*

  - via the [Create Project](rest-api-projects.html#create-project) REST
    endpoint

  - via the [create-project](cmd-create-project.html) SSH command

To be able to create new projects the global capability [Create
Project](access-control.html#capability_createProject) must be granted.

In addition, projects can be created
[manually](#manual_project_creation).

### Manual Project Creation

1.  Create a Git repository under `gerrit.basePath`:
    
    ``` 
      git --git-dir=$base_path/new/project.git init
    ```
    
    > **Tip**
    > 
    > By tradition the repository directory name should have a `.git`
    > suffix.
    
    To also make this repository available over the anonymous git://
    protocol, don’t forget to create a `git-daemon-export-ok` file:
    
    ``` 
      touch $base_path/new/project.git/git-daemon-export-ok
    ```

2.  Register Project
    
    Either restart the server, or flush the `project_list` cache:
    
    ``` 
      ssh -p 29418 localhost gerrit flush-caches --cache project_list
    ```

## Project Options

### Submit Type

The method Gerrit uses to submit a change to a project can be modified
by any project owner through the project console, `Projects` \> `List`
\> my/project. In general, a submitted change is only merged if all its
dependencies are also submitted, with exceptions documented below. The
following submit types are supported:

  - Fast Forward Only
    
    With this method no merge commits are produced. All merges must be
    handled on the client, prior to uploading to Gerrit for review.
    
    To submit a change, the change must be a strict superset of the
    destination branch. That is, the change must already contain the tip
    of the destination branch at submit time.

  - Merge If Necessary
    
    This is the default for a new project.
    
    If the change being submitted is a strict superset of the
    destination branch, then the branch is fast-forwarded to the change.
    If not, then a merge commit is automatically created. This is
    identical to the classical `git merge` behavior, or `git merge
    --ff`.

  - Always Merge
    
    Always produce a merge commit, even if the change is a strict
    superset of the destination branch. This is identical to the
    behavior of `git merge --no-ff`, and may be useful if the project
    needs to follow submits with `git log --first-parent`.

  - Cherry Pick
    
    Always cherry pick the patch set, ignoring the parent lineage and
    instead creating a brand new commit on top of the current branch
    head.
    
    When cherry picking a change, Gerrit automatically appends onto the
    end of the commit message a short summary of the change’s approvals,
    and a URL link back to the change on the web. The committer header
    is also set to the submitter, while the author header retains the
    original patch set author.
    
    Note that Gerrit ignores dependencies between changes when using
    this submit type unless
    [`change.submitWholeTopic`](config-gerrit.html#change.submitWholeTopic)
    is enabled and depending changes share the same topic. So generally
    submitters must remember to submit changes in the right order when
    using this submit type. If all you want is extra information in the
    commit message, consider using the Rebase Always submit strategy.

  - Rebase If Necessary
    
    If the change being submitted is a strict superset of the
    destination branch, then the branch is fast-forwarded to the change.
    If not, then the change is automatically rebased and then the branch
    is fast-forwarded to the change.

When Gerrit tries to do a merge, by default the merge will only succeed
if there is no path conflict. A path conflict occurs when the same file
has also been changed on the other side of the merge.

  - Rebase Always
    
    Basically, the same as Rebase If Necessary, but it creates a new
    patchset even if fast forward is possible AND like Cherry Pick it
    ensures footers such as Change-Id, Reviewed-On, and others are
    present in resulting commit that is merged.

Thus, Rebase Always can be considered similar to Cherry Pick, but with
the important distinction that Rebase Always does not ignore
dependencies.

If `Allow content merges` is enabled, Gerrit will try to do a content
merge when a path conflict occurs.

### State

This setting defines the state of the project. A project can have the
following states:

  - `Active`:
    
    The project is active and users can see and modify the project
    according to their access rights on the project.

  - `Read Only`:
    
    The project is read only and all modifying operations on it are
    disabled. E.g. this means that pushing to this project fails for all
    users even if they have push permissions assigned on it.
    
    Setting a project to this state is an easy way to temporary close a
    project, as you can keep all write access rights in place and they
    will become active again as soon as the project state is set back to
    `Active`.
    
    This state also makes sense if a project was moved to another
    location. In this case all new development should happen in the new
    project and you want to prevent that somebody accidentally works on
    the old project, while keeping the old project around for old
    references.

  - `Hidden`:
    
    The project is hidden and only visible to project owners. Other
    users are not able to see the project even if they have read
    permissions granted on the project.

### Use target branch when determining new changes to open

The `create-new-change-for-all-not-in-target` option provides a
convenience for selecting [the merge base](user-upload.html#base) by
setting it automatically to the target branch’s tip so you can create
new changes for all commits not in the target branch.

This option is disabled if the tip of the push is a merge commit.

This option also only works if there are no merge commits in the commit
chain, in such cases it fails warning the user that such pushes can only
be performed by manually specifying [bases](user-upload.html#base)

This option is useful if you want to push a change to your personal
branch first and for review to another branch for example. Or in cases
where a commit is already merged into a branch and you want to create a
new open change for that commit on another branch.

### Require Change-Id

The `Require Change-Id in commit message` option defines whether a
[Change-Id](user-changeid.html) in the commit message is required for
pushing a commit for review. If this option is set, trying to push a
commit for review that doesn’t contain a Change-Id in the commit message
fails with [missing Change-Id in commit message
footer](error-missing-changeid.html).

It is recommended to set this option and use a [commit-msg
hook](user-changeid.html#create) (or other client side tooling like
EGit) to automatically generate Change-Id’s for new commits. This way
the Change-Id is automatically in place when changes are reworked or
rebased and uploading new patch sets gets easy.

If this option is not set, commits can be uploaded without a Change-Id,
but then users have to remember to copy the assigned Change-Id from the
change screen and insert it manually into the commit message when they
want to upload a second patch set.

### Maximum Git Object Size Limit

This option defines the maximum allowed Git object size that
receive-pack will accept. If an object is larger than the given size the
pack-parsing will abort and the push operation will fail.

With this option users can be prevented from uploading commits that
contain files which are too large.

Normally the [maximum Git object size
limit](config-gerrit.html#receive.maxObjectSizeLimit) is configured
globally for a Gerrit server. At the project level, the maximum Git
object size limit can be further reduced, but not extended. The
displayed effective limit shows the maximum Git object size limit that
is actually used on the project.

The defined maximum Git object size limit is inherited by any child
project.

### Require Signed-off-by

The `Require Signed-off-by in commit message` option defines whether a
[Signed-off-by](user-signedoffby.html) line in the commit message is
required for pushing a commit. If this option is set, trying to push a
commit that doesn’t contain a Signed-off-by line in the commit message
fails with [not Signed-off-by author/committer/uploader in commit
message footer](error-not-signed-off-by.html).

## Branch Administration

### Branch Creation

There are several ways to create a new branch in a project:

  - in the Web UI under *Projects* \> *List* \> \<project\> \>
    *Branches*

  - via the [Create Branch](rest-api-projects.html#create-branch) REST
    endpoint

  - via the [create-branch](cmd-create-branch.html) SSH command

  - by using a git client to push a commit to a non-existing branch

To be able to create new branches the user must have the [Create
Reference](access-control.html#category_create) access right. In
addition, project owners and Gerrit administrators can create new
branches from the Web UI or via REST even without having the `Create
Reference` access right.

When using the Web UI, the REST endpoint or the SSH command it is only
possible to create branches on commits that already exist in the
repository.

If a branch name does not start with `refs/` it is automatically
prefixed with `refs/heads/`.

The starting revision for a new branch can be any valid SHA-1
expression, as long as it resolves to a commit. Abbreviated SHA-1s are
not supported.

### Branch Deletion

There are several ways to delete a branch:

  - in the Web UI under *Projects* \> *List* \> \<project\> \>
    *Branches*

  - via the [Delete Branch](rest-api-projects.html#delete-branch) REST
    endpoint

  - by using a git client
    
    ``` 
      $ git push origin --delete refs/heads/<branch-to-delete>
    ```
    
    another method, by force pushing nothing to an existing branch:
    
    ``` 
      $ git push --force origin :refs/heads/<branch-to-delete>
    ```

To be able to delete branches, the user must have the [Delete
Reference](access-control.html#category_delete) or the
[Push](access-control.html#category_push) access right with the `force`
option. In addition, project owners and Gerrit administrators can delete
branches from the Web UI or via REST even without having the `Force
Push` access right.

### Default Branch

The default branch of a remote repository is defined by its `HEAD`. For
convenience reasons, when the repository is cloned Git creates a local
branch for this default branch and checks it out.

Project owners can set `HEAD`

  - in the Web UI under *Projects* \> *List* \> \<project\> \>
    *Branches* or

  - via the [Set HEAD](rest-api-projects.html#set-head) REST endpoint

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

