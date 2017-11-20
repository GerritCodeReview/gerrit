---
title: " Gerrit Code Review - /plugins/ REST API"
sidebar: restapi_sidebar
permalink: rest-api-plugins.html
---
This page describes the plugin related REST endpoints. Please also take
note of the general information on the [REST API](rest-api.html).

## Plugin Endpoints

Gerrit REST endpoints for installed plugins are available under
*/plugins/[{plugin-id}](#plugin-id)/gerrit~\<endpoint-id\>*. The
`gerrit~` prefix ensures that the Gerrit REST endpoints for plugins do
not clash with any REST endpoint that a plugin may offer under its
namespace.

### List Plugins

*GET /plugins/*

Lists the plugins installed on the Gerrit server. Only the enabled
plugins are returned unless the `all` option is specified.

To be allowed to see the installed plugins, a user must be a member of a
group that is granted the *View Plugins* capability or the *Administrate
Server* capability.

As result a map is returned that maps the plugin IDs to
[PluginInfo](#plugin-info) entries. The entries in the map are sorted by
plugin ID.

**Request.**

``` 
  GET /plugins/ HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "delete-project": {
      "id": "delete-project",
      "index_url": "plugins/delete-project/",
      "filename": "delete-project.jar",
      "version": "2.9-SNAPSHOT"
    }
  }
```

#### Plugin Options

  - All(a)  
    List all plugins including those that are disabled.

**Request.**

``` 
  GET /plugins/?all HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "delete-project": {
      "id": "delete-project",
      "index_url": "plugins/delete-project/",
      "filename": "delete-project.jar",
      "version": "2.9-SNAPSHOT"
    },
    "reviewers-by-blame": {
      "id": "reviewers-by-blame",
      "index_url": "plugins/reviewers-by-blame/",
      "filename": "reviewers-by-blame.jar",
      "version": "2.9-SNAPSHOT",
      "disabled": true
    }
  }
```

  - Limit(n)  
    Limit the number of plugins to be included in the results.
    
    Query the first plugin in the plugin list:
    
    **Request.**
    
    ``` 
      GET /plugins/?n=1 HTTP/1.0
    ```
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Disposition: attachment
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      {
        "delete-project": {
          "id": "delete-project",
          "index_url": "plugins/delete-project/",
          "filename": "delete-project.jar",
          "version": "2.9-SNAPSHOT"
        }
      }
    ```

  - Prefix(p)  
    Limit the results to those plugins that start with the specified
    prefix.
    
    The match is case sensitive. May not be used together with `m` or
    `r`.
    
    List all plugins that start with `delete`:
    
    **Request.**
    
    ``` 
      GET /plugins/?p=delete HTTP/1.0
    ```
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Disposition: attachment
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      {
        "delete-project": {
          "id": "delete-project",
          "index_url": "plugins/delete-project/",
          "filename": "delete-project.jar",
          "version": "2.9-SNAPSHOT"
        }
      }
    ```
    
    E.g. this feature can be used by suggestion client UI’s to limit
    results.

  - Regex(r)  
    Limit the results to those plugins that match the specified regex.
    
    Boundary matchers *^* and *$* are implicit. For example: the regex
    *test.\** will match any plugins that start with *test* and regex
    *.\*test* will match any project that end with *test*.
    
    The match is case sensitive. May not be used together with `m` or
    `p`.
    
    List all plugins that match regex `some.*plugin`:
    
    **Request.**
    
    ``` 
      GET /plugins/?r=some.*plugin HTTP/1.0
    ```
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Disposition: attachment
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      {
        "some-plugin": {
          "id": "some-plugin",
          "index_url": "plugins/some-plugin/",
          "filename": "some-plugin.jar",
          "version": "2.9-SNAPSHOT"
        },
        "some-other-plugin": {
          "id": "some-other-plugin",
          "index_url": "plugins/some-other-plugin/",
          "filename": "some-other-plugin.jar",
          "version": "2.9-SNAPSHOT"
        }
      }
    ```

  - Skip(S)  
    Skip the given number of plugins from the beginning of the list.
    
    Query the second plugin in the plugin list:
    
    **Request.**
    
    ``` 
      GET /plugins/?all&n=1&S=1 HTTP/1.0
    ```
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Disposition: attachment
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      {
        "reviewers-by-blame": {
          "id": "reviewers-by-blame",
          "index_url": "plugins/reviewers-by-blame/",
          "filename": "reviewers-by-blame.jar",
          "version": "2.9-SNAPSHOT",
          "disabled": true
        }
      }
    ```

  - Substring(m)  
    Limit the results to those plugins that match the specified
    substring.
    
    The match is case insensitive. May not be used together with `r` or
    `p`.
    
    List all plugins that match substring `project`:
    
    **Request.**
    
    ``` 
      GET /plugins/?m=project HTTP/1.0
    ```
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Disposition: attachment
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      {
        "delete-project": {
          "id": "delete-project",
          "index_url": "plugins/delete-project/",
          "filename": "delete-project.jar",
          "version": "2.9-SNAPSHOT"
        }
      }
    ```

### Install Plugin

*PUT /plugins/[{plugin-id}](#plugin-id)*

Installs a new plugin on the Gerrit server. If a plugin with the
specified name already exists it is overwritten. Note: if the plugin
provides its own name in the MANIFEST file, then the plugin name from
the MANIFEST file has precedence over the {plugin-id} above.

The plugin jar can either be sent as binary data in the request body or
a URL to the plugin jar must be provided in the request body inside a
[PluginInput](#plugin-input) entity.

**Request.**

``` 
  PUT /plugins/delete-project HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "url": "file:///gerrit/plugins/delete-project/delete-project-2.8.jar"
  }
```

To provide the plugin jar as binary data in the request body the
following curl command can be
used:

``` 
  curl --user admin:TNNuLkWsIV8w -X PUT --data-binary @delete-project-2.8.jar 'http://gerrit:8080/a/plugins/delete-project'
```

As response a [PluginInfo](#plugin-info) entity is returned that
describes the plugin.

**Response.**

``` 
  HTTP/1.1 201 Created
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "delete-project",
    "version": "2.8"
  }
```

If an existing plugin was overwritten the response is "`200 OK`".

### Get Plugin Status

*GET /plugins/[{plugin-id}](#plugin-id)/gerrit~status*

Retrieves the status of a plugin on the Gerrit server.

**Request.**

``` 
  GET /plugins/delete-project/gerrit~status HTTP/1.0
```

As response a [PluginInfo](#plugin-info) entity is returned that
describes the plugin.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "delete-project",
    "version": "2.8"
  }
```

### Enable Plugin

*POST /plugins/[{plugin-id}](#plugin-id)/gerrit~enable*

Enables a plugin on the Gerrit server.

**Request.**

``` 
  POST /plugins/delete-project/gerrit~enable HTTP/1.0
```

As response a [PluginInfo](#plugin-info) entity is returned that
describes the plugin.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "delete-project",
    "version": "2.8"
  }
```

### Disable Plugin

*POST /plugins/[{plugin-id}](#plugin-id)/gerrit~disable*

OR

*DELETE /plugins/[{plugin-id}](#plugin-id)*

Disables a plugin on the Gerrit server.

**Request.**

``` 
  POST /plugins/delete-project/gerrit~disable HTTP/1.0
```

As response a [PluginInfo](#plugin-info) entity is returned that
describes the plugin.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "delete-project",
    "version": "2.8",
    "disabled": true
  }
```

### Reload Plugin

*POST /plugins/[{plugin-id}](#plugin-id)/gerrit~reload*

Reloads a plugin on the Gerrit server.

**Request.**

``` 
  POST /plugins/delete-project/gerrit~reload HTTP/1.0
```

As response a [PluginInfo](#plugin-info) entity is returned that
describes the plugin.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "delete-project",
    "version": "2.8",
    "disabled": true
  }
```

## IDs

### {plugin-id}

The ID of the plugin.

## JSON Entities

### PluginInfo

The `PluginInfo` entity describes a plugin.

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
<td><p><code>id</code></p></td>
<td></td>
<td><p>The ID of the plugin.</p></td>
</tr>
<tr class="even">
<td><p><code>version</code></p></td>
<td></td>
<td><p>The version of the plugin.</p></td>
</tr>
<tr class="odd">
<td><p><code>index_url</code></p></td>
<td><p>optional</p></td>
<td><p>URL of the plugin’s default page.</p></td>
</tr>
<tr class="even">
<td><p><code>filename</code></p></td>
<td><p>optional</p></td>
<td><p>The plugin’s filename.</p></td>
</tr>
<tr class="odd">
<td><p><code>disabled</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the plugin is disabled.</p></td>
</tr>
</tbody>
</table>

### PluginInput

The `PluginInput` entity describes a plugin that should be installed.

<table>
<colgroup>
<col width="14%" />
<col width="85%" />
</colgroup>
<thead>
<tr class="header">
<th>Field Name</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p><code>url</code></p></td>
<td><p>URL to the plugin jar.</p></td>
</tr>
</tbody>
</table>

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

