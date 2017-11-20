---
title: " Gerrit Code Review - Contributor Agreements"
sidebar: gerritdoc_sidebar
permalink: config-cla.html
---
Users can be required to sign one or more contributor agreements before
being able to submit a change in a project.

Contributor agreements are global and can be configured by modifying the
`project.config` file on the `All-Projects` project. Push permission
needs to be granted for the `refs/meta/config` branch to be able to push
back the `project.config` file. Consult [access
controls](access-control.html) for details on how access permissions
work.

To retrieve the `project.config` file, initialize a temporary Git
repository to edit the configuration:

``` 
  mkdir cfg_dir
  cd cfg_dir
  git init
```

Download the existing configuration from Gerrit:

``` 
  git fetch ssh://localhost:29418/All-Projects refs/meta/config
  git checkout FETCH_HEAD
```

Contributor agreements are defined as contributor-agreement sections in
`project.config`:

``` 
  [contributor-agreement "Individual"]
    description = If you are going to be contributing code on your own, this is the one you want. You can sign this one online.
    agreementUrl = static/cla_individual.html
    autoVerify = group CLA Accepted - Individual
    accepted = group CLA Accepted - Individual
```

Each `contributor-agreement` section within the `project.config` file
must have a unique name. The section name will appear in the web UI.

If not already present, add the group(s) used in the `autoVerify` and
`accepted` variables in the `groups` file:

``` 
    # UUID                                      Group Name
    #
    3dedb32915ecdbef5fced9f0a2587d164cd614d4    CLA Accepted - Individual
```

Commit the configuration change, and push it back:

``` 
  git commit -a -m "Add Individual contributor agreement"
  git push ssh://localhost:29418/All-Projects HEAD:refs/meta/config
```

  - contributor-agreement.\<name\>.description  
    Short text describing the contributor agreement. This text will
    appear when the user selects an agreement.

  - contributor-agreement.\<name\>.agreementUrl  
    An absolute URL or a relative path to an HTML file containing the
    text of the contributor agreement. The URL must use the http or
    https scheme. The path is relative to the `gerrit.basePath` variable
    in `gerrit.config`.

  - contributor-agreement.\<name\>.autoVerify  
    If present, the user can sign the contributor agreement online. The
    value is the group to which the user will be added after signing the
    agreement. The group’s UUID must also appear in the `groups` file.

  - contributor-agreement.\<name\>.accepted  
    List of groups that will be considered when verifying that a
    contributor agreement has been accepted. The groups' UUID must also
    appear in the `groups` file.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

