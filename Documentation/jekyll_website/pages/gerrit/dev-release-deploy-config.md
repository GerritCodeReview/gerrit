---
title: " Deploy Gerrit Artifacts"
sidebar: gerritdoc_sidebar
permalink: dev-release-deploy-config.html
---
## Deploy Configuration settings for Maven Central

Some Gerrit artifacts (e.g. the Gerrit WAR file, the Gerrit Plugin API
and the Gerrit Extension API) are published on Maven Central in the
`com.google.gerrit` repository.

To be able to publish artifacts to Maven Central some preparations must
be done:

  - Create an account on [Sonatype’s
    Jira](https://issues.sonatype.org/secure/Signup!default.jspa).
    
    Sonatype is the company that runs Maven Central and you need a
    Sonatype account to be able to upload artifacts to Maven Central.

  - Configure your Sonatype user and password in `~/.m2/settings.xml`:
    
        <server>
          <id>sonatype-nexus-staging</id>
          <username>USER</username>
          <password>PASSWORD</password>
        </server>

  - Request permissions to upload artifacts to the `com.google.gerrit`
    repository on Maven Central:
    
    Ask for this permission by adding a comment on the
    [OSSRH-7392](https://issues.sonatype.org/browse/OSSRH-7392) Jira
    ticket at Sonatype.
    
    The request needs to be approved by someone who already has this
    permission by commenting on the same issue.

  - Generate and publish a PGP key
    
    A PGP key is needed to be able to sign the release artifacts before
    the upload to Maven Central, and to sign the release announcement
    email.
    
    Generate and publish a PGP key as described in [Working with PGP
    Signatures](http://central.sonatype.org/pages/working-with-pgp-signatures.html).
    In addition to the keyserver mentioned there it is recommended to
    also publish the key to the [Ubuntu key
    server](https://keyserver.ubuntu.com/).
    
    Please be aware that after publishing your public key it may take a
    while until it is visible to the Sonatype server.
    
    Add an entry for the public key in the [key
    list](https://gerrit.googlesource.com/homepage/+/md-pages/releases/public-keys.md)
    on the homepage.
    
    The PGP passphrase can be put in `~/.m2/settings.xml`:
    
        <settings>
          <profiles>
            <profile>
              <id>gpg</id>
              <properties>
                <gpg.executable>gpg2</gpg.executable>
                <gpg.passphrase>mypassphrase</gpg.passphrase>
              </properties>
            </profile>
          </profiles>
          <activeProfiles>
            <activeProfile>gpg</activeProfile>
          </activeProfiles>
        </settings>
    
    It can also be included in the key chain on OS X.

## Deploy Configuration in Maven `settings.xml`

Gerrit Subproject Artifacts are stored on [Google Cloud
Storage](https://developers.google.com/storage/). Via the [Developers
Console](https://console.developers.google.com/project/164060093628) the
Gerrit maintainers have access to the `Gerrit Code Review` project. This
projects host several buckets for storing Gerrit artifacts:

  - `gerrit-api`:
    
    Bucket to store the Gerrit Extension API Jar and the Gerrit Plugin
    API Jar.

  - `gerrit-maven`:
    
    Bucket to store Gerrit Subproject Artifacts (e.g. `gwtjsonrpc`
    etc.).

To upload artifacts to a bucket the user must authenticate with a
username and password. The username and password need to be retrieved
from the [Storage Setting in the Google Cloud Platform
Console](https://console.cloud.google.com/storage/settings?project=api-project-164060093628):

Select the `Interoperability` tab, and if no keys are listed under
`Interoperable storage access keys`, select *Create a new key*.

Using `Access Key` as username and `Secret` as the password, add the
configuration in the `~/.m2/settings.xml` file to make the credentials
known to Maven:

``` 
  <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
    <servers>
      <server>
        <id>gerrit-api-repository</id>
        <username>GOOG..EXAMPLE.....EXAMPLE</username>
        <password>EXAMPLE..EXAMPLE..EXAMPLE</password>
      </server>
      <server>
        <id>gerrit-maven-repository</id>
        <username>GOOG..EXAMPLE.....EXAMPLE</username>
        <password>EXAMPLE..EXAMPLE..EXAMPLE</password>
      </server>
      <server>
        <id>gerrit-plugins-repository</id>
        <username>GOOG..EXAMPLE.....EXAMPLE</username>
        <password>EXAMPLE..EXAMPLE..EXAMPLE</password>
      </server>
    </servers>
  </settings>
```

### Gerrit Subprojects

  - You will need to have the following in the `pom.xml` to make it
    deployable to the `gerrit-maven` storage bucket:

<!-- end list -->

``` 
  <distributionManagement>
    <repository>
      <id>gerrit-maven-repository</id>
      <name>Gerrit Maven Repository</name>
      <url>gs://gerrit-maven</url>
      <uniqueVersion>true</uniqueVersion>
    </repository>
  </distributionManagement>
```

> **Note**
> 
> In case of JGit the `pom.xml` already contains a
> `distributionManagement` section. To deploy the artifacts to the
> `gerrit-maven` repository, replace the existing
> `distributionManagement` section with this snippet.

  - Add these two snippets to the `pom.xml` to enable the wagon
    provider:

<!-- end list -->

``` 
  <pluginRepositories>
    <pluginRepository>
      <id>gerrit-maven</id>
      <url>https://gerrit-maven.commondatastorage.googleapis.com</url>
    </pluginRepository>
  </pluginRepositories>
```

``` 
  <build>
    <extensions>
      <extension>
        <groupId>com.googlesource.gerrit</groupId>
        <artifactId>gs-maven-wagon</artifactId>
        <version>3.3</version>
      </extension>
    </extensions>
  </build>
```

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

