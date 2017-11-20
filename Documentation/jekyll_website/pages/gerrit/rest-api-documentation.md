---
title: " Gerrit Code Review - /Documentation/ REST API"
sidebar: restapi_sidebar
permalink: rest-api-documentation.html
---
This page describes the documentation search related REST endpoints.
Please also take note of the general information on the [REST
API](rest-api.html).

Please note that this feature is only usable with documentation
built-in. You’ll need to `bazel build withdocs` or `bazel build release`
to test this feature.

## Documentation Search Endpoints

### Search Documentation

*GET /Documentation/*

With `q` parameter, search our documentation index for the terms.

A list of [DocResult](#doc-result) entities is returned describing the
results.

**Request.**

``` 
  GET /Documentation/?q=test HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "title": "Gerrit Code Review - REST API Developers\u0027 Notes",
      "url": "Documentation/dev-rest-api.html"
    },
    {
      "title": "Gerrit Code Review - REST API",
      "url": "Documentation/rest-api.html"
    },
    {
      "title": "Gerrit Code Review - JavaScript API",
      "url": "Documentation/js-api.html"
    },
    {
      "title": "Gerrit Code Review - /plugins/ REST API",
      "url": "Documentation/rest-api-plugins.html"
    },
    {
      "title": "Gerrit Code Review - /config/ REST API",
      "url": "Documentation/rest-api-config.html"
    },
    {
      "title": "Gerrit Code Review for Git",
      "url": "Documentation/index.html"
    },
    {
      "title": "Gerrit Code Review - /access/ REST API",
      "url": "Documentation/rest-api-access.html"
    },
    {
      "title": "Gerrit Code Review - Plugin Development",
      "url": "Documentation/dev-plugins.html"
    },
    {
      "title": "Gerrit Code Review - Developer Setup",
      "url": "Documentation/dev-readme.html"
    },
    {
      "title": "Gerrit Code Review - Hooks",
      "url": "Documentation/config-hooks.html"
    },
    {
      "title": "Gerrit Code Review - /groups/ REST API",
      "url": "Documentation/rest-api-groups.html"
    },
    {
      "title": "Gerrit Code Review - /accounts/ REST API",
      "url": "Documentation/rest-api-accounts.html"
    },
    {
      "title": "Gerrit Code Review - /projects/ REST API",
      "url": "Documentation/rest-api-documentation.html"
    },
    {
      "title": "Gerrit Code Review - /projects/ REST API",
      "url": "Documentation/rest-api-projects.html"
    },
    {
      "title": "Gerrit Code Review - Prolog Submit Rules Cookbook",
      "url": "Documentation/prolog-cookbook.html"
    },
    {
      "title": "Gerrit Code Review - /changes/ REST API",
      "url": "Documentation/rest-api-changes.html"
    },
    {
      "title": "Gerrit Code Review - Configuration",
      "url": "Documentation/config-gerrit.html"
    },
    {
      "title": "Gerrit Code Review - Access Controls",
      "url": "Documentation/access-control.html"
    },
    {
      "title": "Gerrit Code Review - Licenses",
      "url": "Documentation/licenses.html"
    }
  ]
```

get::/Documentation/?q=keyword

## JSON Entities

### DocResult

The `DocResult` entity contains information about a document.

<table>
<colgroup>
<col width="14%" />
<col width="28%" />
<col width="57%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th></th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>title</code></p></td>
<td></td>
<td><p>The title of the document.</p></td>
</tr>
<tr class="even">
<td><p><code>url</code></p></td>
<td></td>
<td><p>The URL of the document.</p></td>
</tr>
</tbody>
</table>

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

