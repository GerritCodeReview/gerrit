---
title: " prolog-shell"
sidebar: gerritdoc_sidebar
permalink: pgm-prolog-shell.html
---
## NAME

prolog-shell - Simple interactive Prolog interpreter

## SYNOPSIS

> 
> 
>     java -jar gerrit.war prolog-shell
>       [-s FILE.pl …]

## DESCRIPTION

Provides a simple interactive Prolog interpreter for development and
testing.

## OPTIONS

  - \-s  
    Dynamically load the Prolog source code at startup, as though the
    user had entered `['FILE.pl'].` into the interpreter once it was
    running. This option may be supplied more than once to load multiple
    files.

## EXAMPLES

Define a simple predicate and test it:

``` 
        $ cat >simple.pl
        food(apple).
        food(orange).
        ^D

        $ java -jar gerrit.war prolog-shell -s simple.pl
        Gerrit Code Review 2.2.1-84-ge9c3992 - Interactive Prolog Shell
        based on Prolog Cafe 1.2.5 (mantis)
                 Copyright(C) 1997-2009 M.Banbara and N.Tamura
        (type Ctrl-D or "halt." to exit, "['path/to/file.pl']." to load a file)

        {consulting /usr/local/google/users/sop/gerrit/gerrit/simple.pl ...}
        {/usr/local/google/users/sop/gerrit/gerrit/simple.pl consulted 99 msec}

        | ?- food(Type).

        Type = apple ? ;

        Type = orange ? ;

        no
        | ?-
```

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

