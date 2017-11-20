---
title: " gerrit ls-members"
sidebar: cmd_sidebar
permalink: cmd-ls-members.html
---
## NAME

gerrit ls-members - Show members of a given group

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit ls-members GROUPNAME
>       [--recursive]

## DESCRIPTION

Displays the members of the given group, one per line, so long as the
given group is visible to the user. The users' id, username, full name
and email are shown tab-separated.

## ACCESS

Any user who has SSH access to Gerrit.

## SCRIPTING

This command is intended to be used in scripts. Output is either an
error message or a heading followed by zero or more lines, one for each
member of the group. If any field is not set, or if the field is the
user’s full name and the name is empty, "n/a" is emitted as the field
value.

All non-printable characters (ASCII value 31 or less) are escaped
according to the conventions used in languages like C, Python, and Perl,
employing standard sequences like `\n` and `\t`, and `\xNN` for all
others. In shell scripts, the `printf` command can be used to unescape
the output.

## OPTIONS

  - \--recursive  
    If a member of the group is itself a group, the sub-group’s members
    are included in the list. Otherwise members of any sub-group are not
    shown and no indication is given that a sub-group is present

## EXAMPLES

List members of the Administrators
group:

``` 
        $ ssh -p 29418 review.example.com gerrit ls-members Administrators
        id      username  full name    email
        100000  jim     Jim Bob somebody@example.com
        100001  johnny  John Smith      n/a
        100002  mrnoname        n/a     someoneelse@example.com
```

List members of a non-existent
group:

``` 
        $ ssh -p 29418 review.example.com gerrit ls-members BadlySpelledGroup
        Group not found or not visible
```

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

