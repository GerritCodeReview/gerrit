---
title: " gerrit apropos"
sidebar: cmd_sidebar
permalink: cmd-apropos.html
---
## NAME

gerrit apropos - Search Gerrit documentation index

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit apropos
>       <query>

## DESCRIPTION

Queries the documentation index and returns results with the title and
URL from the matched documents.

## ACCESS

Any user who has SSH access to Gerrit.

## SCRIPTING

This command is intended to be used in scripts.

> **Note**
> 
> This feature is only available if documentation index was built.

## EXAMPLES

    $ ssh -p 29418 review.example.com gerrit apropos capabilities
    
        Gerrit Code Review - /config/ REST API:
        http://localhost:8080/Documentation/rest-api-config.html
    
        Gerrit Code Review - /accounts/ REST API:
        http://localhost:8080/Documentation/rest-api-accounts.html
    
        Gerrit Code Review - Project Configuration File Format:
        http://localhost:8080/Documentation/config-project-config.html
    
        Gerrit Code Review - Access Controls:
        http://localhost:8080/Documentation/access-control.html
    
        Gerrit Code Review - Plugin Development:
        http://localhost:8080/Documentation/dev-plugins.html
    
        Gerrit Code Review - REST API:
        http://localhost:8080/Documentation/rest-api.html
    
        Gerrit Code Review - /access/ REST API:
        http://localhost:8080/Documentation/rest-api-access.html

## SEE ALSO

  - [Access Controls](access-control.html)

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

