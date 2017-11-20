---
title: " Making a Release of a Gerrit Subproject"
sidebar: gerritdoc_sidebar
permalink: dev-release-subproject.html
---
## Make a Snapshot

  - Build the latest snapshot and install it into the local Maven
    repository:
    
    ``` 
      mvn clean install
    ```

  - Test Gerrit with this snapshot locally

## Publish Snapshot

If a snapshot for a subproject was created that should be referenced by
Gerrit while current Gerrit development is ongoing, this snapshot needs
to be published.

  - Make sure you have done the configuration needed for deployment:
    
      - [Configuration in Maven
        `settings.xml`](dev-release-deploy-config.html#deploy-configuration-settings-xml)
    
      - [Configuration for Subprojects in
        `pom.xml`](dev-release-deploy-config.html#deploy-configuration-subprojects)

  - Deploy the new snapshot:
    
    ``` 
      mvn deploy
    ```

  - Change the `id`, `bin_sha1`, and `src_sha1` values in the
    `maven_jar` for the subproject in `/lib/BUCK` to the `SNAPSHOT`
    version.
    
    When Gerrit gets released, a release of the subproject has to be
    done and Gerrit has to reference the released subproject version.

## Prepare the Release

  - [First create (and test) the latest snapshot for the
    subproject](#make-snapshot)

  - Update the top level `pom.xml` in the subproject to reflect the new
    project version (the exact value of the tag you will create below)

  - Create the Release Tag
    
    ``` 
      git tag -a -m "prolog-cafe 1.3" v1.3
    ```

  - Build and install into local Maven repository:
    
    ``` 
      mvn clean install
    ```

## Publish the Release

  - Make sure you have done the configuration needed for deployment:
    
      - [Configuration in Maven
        `settings.xml`](dev-release-deploy-config.html#deploy-configuration-settings-xml)
    
      - Configuration in `pom.xml` for
        [subprojects](dev-release-deploy-config.html#deploy-configuration-subprojects)

  - Deploy the new release:
    
    ``` 
      mvn deploy
    ```

  - Push the pom change(s) to the project’s repository
    `refs/for/<master|stable>`

  - Push the Release Tag
    
    ``` 
      git push gerrit-review refs/tags/v1.3:refs/tags/v1.3
    ```

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

