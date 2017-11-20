---
title: " Gerrit Code Review - Single Sign-On Security"
sidebar: gerritdoc_sidebar
permalink: config-sso.html
---
Gerrit supports integration with some types of single sign-on security
solutions, making it possible for end-users to setup and manage
accounts, without administrator involvement.

## OpenID

By default a new Gerrit installation relies upon OpenID to perform user
authentication services. To enable OpenID, the auth.type setting should
be `OpenID`:

``` 
  git config --file $site_path/etc/gerrit.config auth.type OpenID
```

As this is the default setting there is nothing required from the site
administrator to make use of the OpenID authentication services.

  - [openid.net](http://openid.net/)

If Jetty is being used, you may need to increase the header buffer size
parameter, due to very long header lines. Add the following to
`$JETTY_HOME/etc/jetty.xml` under
`org.mortbay.jetty.nio.SelectChannelConnector`:

``` 
  <Set name="headerBufferSize">16384</Set>
```

In order to use permissions beyond those granted to the `Anonymous
Users` and `Registered Users` groups, an account must only have OpenIDs
which match at least one pattern from the `auth.trustedOpenID` list in
`gerrit.config`. Patterns may be either a [standard Java regular
expression
(java.util.regex)](http://download.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
(must start with `^` and end with `$`) or be a simple prefix (any other
string).

Out of the box Gerrit is configured to trust two patterns, which will
match any OpenID provider on the Internet:

  - `http://` — trust all OpenID providers using the HTTP protocol

  - `https://` — trust all OpenID providers using the HTTPS protocol

To trust only
Yahoo\!:

``` 
  git config --file $site_path/etc/gerrit.config auth.trustedOpenID https://me.yahoo.com
```

### Database Schema

User identities obtained from OpenID providers are stored as [external
IDs](config-accounts.html#external-ids).

### Multiple Identities

Users may link more than one OpenID identity to the same Gerrit account,
making it easier for their browser to sign in to Gerrit if they are
frequently switching between different unique OpenID accounts.

> **Warning**
> 
> Users wishing to link an alternative identity should **NOT** log in
> separately with that identity. Doing so will result in a new account
> being created, and subsequent attempts to link that account with the
> existing account will fail. In cases where this happens, the
> administrator will need to manually merge the accounts. See [Merging
> Gerrit User
> Accounts](https://code.google.com/p/gerrit/wiki/SqlMergeUserAccounts)
> on the Gerrit Wiki for details.

Linking another identity is also useful for users whose primary OpenID
provider shuts down. For example Google will [shut down their OpenID
service on 20th April
2015](https://developers.google.com/+/api/auth-migration). Users must
add an alternative identity, using another OpenID provider, before that
shutdown date. User who fail to add an alternative identity before that
date, and end up with their account only having a disabled Google
identity, will need to create a separate account with an alternative
provider and then ask the administrator to merge the accounts using the
previously mentioned method.

To link another identity to an existing account:

  - Login with the existing account

  - Select menu Settings → Identities

  - Click the *Link Another Identity* button

  - Select the OpenID provider for the other identity

  - Authenticate with the other identity

Login using the other identity can only be performed after the linking
is successful.

## HTTP Basic Authentication

When using HTTP authentication, Gerrit assumes that the servlet
container or the frontend web server has performed all user
authentication prior to handing the request off to Gerrit.

As a result of this assumption, Gerrit can assume that any and all
requests have already been authenticated. The "Sign In" and "Sign Out"
links are therefore not displayed in the web UI.

To enable this form of authentication:

``` 
  git config --file $site_path/etc/gerrit.config auth.type HTTP
  git config --file $site_path/etc/gerrit.config --unset auth.httpHeader
  git config --file $site_path/etc/gerrit.config auth.emailFormat '{0}@example.com'
```

The auth.type must always be HTTP, indicating the user identity will be
obtained from the HTTP authorization data.

The auth.httpHeader must always be unset. If set to any value (including
`Authorization`) then Gerrit won’t correctly honor the standard
`Authorization` HTTP header.

The auth.emailFormat field (*optional*) sets the preferred email address
during first login. Gerrit will replace `{0}` with the username, as
obtained from the Authorization header. A format such as shown in the
example would be typical, to add the domain name of the organization.

If Apache HTTPd is being used as the primary web server and the Apache
server will be handling user authentication, a configuration such as the
following is recommended to ensure Apache performs the authentication at
the proper time:

``` 
  <Location "/login/">
    AuthType Basic
    AuthName "Gerrit Code Review"
    Require valid-user
    ...
  </Location>
```

### Database Schema

User identities are stored as [external
IDs](config-accounts.html#external-ids) with "gerrit" as scheme. The
user string obtained from the authorization header is stored as ID of
the external ID.

## Computer Associates Siteminder

Siteminder is a commercial single sign on solution marketed by Computer
Associates. It is very common in larger enterprise environments.

When using Siteminder, Gerrit assumes it has been installed in a servlet
container which is running behind an Apache web server, and that the
Siteminder authentication module has been configured within Apache to
protect the entire Gerrit application. In this configuration all users
must authenticate with Siteminder before they can access any resource on
Gerrit.

As a result of this assumption, Gerrit can assume that any and all
requests have already been authenticated. The "Sign In" and "Sign Out"
links are therefore not displayed in the web UI.

To enable this form of authentication:

``` 
  git config --file $site_path/etc/gerrit.config auth.type HTTP
  git config --file $site_path/etc/gerrit.config auth.httpHeader SM_USER
  git config --file $site_path/etc/gerrit.config auth.emailFormat '{0}@example.com'
```

The auth.type must always be HTTP, indicating the user identity will be
obtained from the HTTP authorization data.

The auth.httpHeader indicates in which HTTP header field the Siteminder
product has stored the username. Usually this is "SM\_USER", but may
differ in your environment. Please refer to your organization’s single
sign-on or security group to ensure the setting is correct.

The auth.emailFormat field (*optional*) sets the user’s preferred email
address when they first login. Gerrit will replace `{0}` with the
username, as supplied by Siteminder. A format such as shown in the
example would be typical, to add the domain name of the organization.

If Jetty is being used, you may need to increase the header buffer size
parameter, due to very long header lines. Add the following to
`$JETTY_HOME/etc/jetty.xml` under
`org.mortbay.jetty.nio.SelectChannelConnector`:

``` 
  <Set name="headerBufferSize">16384</Set>
```

### Database Schema

User identities are stored as [external
IDs](config-accounts.html#external-ids) with "gerrit" as scheme. The
user string obtained from Siteminder (e.g. the value in the "SM\_USER"
HTTP header) is stored as ID in the external ID.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

