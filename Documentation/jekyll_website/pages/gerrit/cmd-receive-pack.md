---
title: " git-receive-pack"
sidebar: cmd_sidebar
permalink: cmd-receive-pack.html
---
## NAME

git-receive-pack - Receive what is pushed into the repository

## SYNOPSIS

> 
> 
>     git receive-pack
>       [--reviewer <address> | --re <address>]
>       [--cc <address>]
>       <project>

## DESCRIPTION

Invoked by *git push* and updates the project’s repository with the
information fed from the *git push* end.

End users can supply options to this command by passing them through to
*git push*, which will relay them automatically.

## OPTIONS

  - \<project\>  
    The remote repository that will receive the pushed objects, and
    create (or update) changes. Within Gerrit Code Review this is the
    name of a project. The optional leading `/` and or trailing `.git`
    suffix will be removed, if supplied.

  - \--reviewer \<address\>; --re \<address\>  
    Automatically add \<address\> as a reviewer to any change.
    Deprecated, use `refs/for/branch%r=address` instead.

  - \--cc \<address\>  
    Carbon-copy \<address\> on the created or updated changes.
    Deprecated, use `refs/for/branch%cc=address` instead.

## ACCESS

Any user who has SSH access to Gerrit.

## EXAMPLES

Send a review for a change on the master branch to
<charlie@example.com>:

``` 
        git push ssh://review.example.com:29418/project HEAD:refs/for/master%r=charlie@example.com
```

Send reviews, but tagging them with the topic name
*bug42*:

``` 
        git push ssh://review.example.com:29418/project HEAD:refs/for/master%r=charlie@example.com,topic=bug42
```

Also CC two other
parties:

``` 
        git push ssh://review.example.com:29418/project HEAD:refs/for/master%r=charlie@example.com,cc=alice@example.com,cc=bob@example.com
```

Configure a push macro to perform the last
action:

``` 
        git config remote.charlie.url ssh://review.example.com:29418/project
        git config remote.charlie.push HEAD:refs/for/master%r=charlie@example.com,cc=alice@example.com,cc=bob@example.com
```

afterwards `.git/config` contains the following:

    [remote "charlie"]
      url = ssh://review.example.com:29418/project
      push = HEAD:refs/for/master%r=charlie@example.com,cc=alice@example.com,cc=bob@example.com

and now sending a new change for review to charlie, CC’ing both alice
and bob is much easier:

``` 
        git push charlie
```

## SEE ALSO

  - [Uploading Changes](user-upload.html)

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

