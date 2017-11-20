---
title: " Gerrit Code Review - REST API"
sidebar: gerritdoc_sidebar
permalink: rest-api.html
---
Gerrit Code Review comes with a REST like API available over HTTP. The
API is suitable for automated tools to build upon, as well as supporting
some ad-hoc scripting use cases.

See also: [REST API Developers' Notes](dev-rest-api.html).

## Endpoints

  - [/access/](rest-api-access.html)  
    Access Right related REST endpoints

  - [/accounts/](rest-api-accounts.html)  
    Account related REST endpoints

  - [/changes/](rest-api-changes.html)  
    Change related REST endpoints

  - [/config/](rest-api-config.html)  
    Config related REST endpoints

  - [/groups/](rest-api-groups.html)  
    Group related REST endpoints

  - [/plugins/](rest-api-plugins.html)  
    Plugin related REST endpoints

  - [/projects/](rest-api-projects.html)  
    Project related REST endpoints

  - [/Documentation/](rest-api-documentation.html)  
    Documentation related REST endpoints

## Protocol Details

### Authentication

By default all REST endpoints assume anonymous access and filter results
to correspond to what anonymous users can read (which may be nothing at
all).

Users (and programs) can authenticate with HTTP passwords by prefixing
the endpoint URL with `/a/`. For example to authenticate to
`/projects/`, request the URL `/a/projects/`. Gerrit will use HTTP basic
authentication with the HTTP password from the user’s account settings
page. This form of authentication bypasses the need for XSRF tokens.

An authorization cookie may be presented in the request URL inside the
`access_token` query parameter. XSRF tokens are not required when a
valid `access_token` is used in the URL.

### CORS

Cross-site scripting may be supported if the administrator has
configured
[site.allowOriginRegex](config-gerrit.html#site.allowOriginRegex).

Approved web applications running from an allowed origin can rely on
CORS preflight to authorize requests requiring cookie based
authentication, or mutations (POST, PUT, DELETE). Mutations require a
valid XSRF token in the `X-Gerrit-Auth` request header.

Alternatively applications can use `access_token` in the URL (see above)
to authorize requests. Mutations sent as POST with a request content
type of `text/plain` can skip CORS preflight. Gerrit accepts additional
query parameters `$m` to override the correct method (PUT, POST, DELETE)
and `$ct` to specify the actual content type, such as `application/json;
charset=UTF-8`.
Example:

``` 
    POST /changes/42/topic?$m=PUT&$ct=application/json%3B%20charset%3DUTF-8&access_token=secret HTTP/1.1
        Content-Type: text/plain
        Content-Length: 23

        {"topic": "new-topic"}
```

### Preconditions

Clients can request PUT to create a new resource and not overwrite an
existing one by adding `If-None-Match: *` to the request HTTP headers.
If the named resource already exists the server will respond with HTTP
412 Precondition Failed.

### Output Format

JSON responses are encoded using UTF-8 and use content type
`application/json`.

By default most APIs return pretty-printed JSON, which uses extra
whitespace to make the output more readable for humans.

Compact JSON can be requested by setting the `pp=0` query parameter, or
by setting the `Accept` HTTP request header to include
`application/json`:

``` 
  GET /projects/ HTTP/1.0
  Accept: application/json
```

Producing (and parsing) the non-pretty compact format is more efficient,
so tools should request it whenever possible.

To prevent against Cross Site Script Inclusion (XSSI) attacks, the JSON
response body starts with a magic prefix line that must be stripped
before feeding the rest of the response body to a JSON parser:

``` 
  )]}'
  [ ... valid JSON ... ]
```

Responses will be gzip compressed by the server if the HTTP
`Accept-Encoding` request header is set to `gzip`. This may save on
network transfer time for larger responses.

### Input Format

Unknown JSON parameters will simply be ignored by Gerrit without causing
an exception. This also applies to case-sensitive parameters, such as
map keys.

### Timestamp

Timestamps are given in UTC and have the format "*yyyy-mm-dd
hh:mm:ss.fffffffff*" where "*ffffffffff*" represents nanoseconds.

### Encoding

All IDs that appear in the URL of a REST call (e.g. project name, group
name) must be URL encoded.

### Response Codes

The Gerrit REST endpoints use HTTP status codes as described in the
[HTTP
specification](http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html).

In most cases, the response body of an error response will be a
plaintext, human-readable error message.

Here are examples that show how HTTP status codes are used in the
context of the Gerrit REST API.

#### 400 Bad Request

"`400 Bad Request`" is returned if the request is not understood by the
server due to malformed syntax.

E.g. "`400 Bad Request`" is returned if JSON input is expected but the
*Content-Type* of the request is not *application/json* or the request
body doesn’t contain valid JSON.

"`400 Bad Request`" is also returned if required input fields are not
set or if options are set which cannot be used together.

#### 403 Forbidden

"`403 Forbidden`" is returned if the operation is not allowed because
the calling user does not have sufficient permissions.

E.g. some REST endpoints require that the calling user has certain
[global capabilities](access-control.html#global_capabilities) assigned.

"`403 Forbidden`" is also returned if `self` is used as account ID and
the REST call was done without authentication.

#### 404 Not Found

"`404 Not Found`" is returned if the resource that is specified by the
URL is not found or is not visible to the calling user. A resource
cannot be found if the URL contains a non-existing ID or view.

#### 405 Method Not Allowed

"`405 Method Not Allowed`" is returned if the resource exists but
doesn’t support the operation.

E.g. some of the `/groups/` endpoints are only supported for Gerrit
internal groups; if they are invoked for an external group the response
is "`405 Method Not Allowed`".

#### 409 Conflict

"`409 Conflict`" is returned if the request cannot be completed because
the current state of the resource doesn’t allow the operation.

E.g. if you try to submit a change that is abandoned, this fails with
"`409 Conflict`" because the state of the change doesn’t allow the
submit operation.

"`409 Conflict`" is also returned if you try to create a resource but
the name is already occupied by an existing resource.

#### 412 Precondition Failed

"`412 Precondition Failed`" is returned if a precondition from the
request header fields is not fulfilled, as described in the
[Preconditions](#preconditions) section.

#### 422 Unprocessable Entity

"`422 Unprocessable Entity`" is returned if the ID of a resource that is
specified in the request body cannot be resolved.

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

