---
title: " Gerrit Code Review - /groups/ REST API"
sidebar: restapi_sidebar
permalink: rest-api-groups.html
---
This page describes the group related REST endpoints. Please also take
note of the general information on the [REST API](rest-api.html).

## Group Endpoints

### List Groups

*GET /groups/*

Lists the groups accessible by the caller. This is the same as using the
[ls-groups](cmd-ls-groups.html) command over SSH, and accepts the same
options as query parameters.

As result a map is returned that maps the group names to
[GroupInfo](#group-info) entries. The entries in the map are sorted by
group name.

**Request.**

``` 
  GET /groups/ HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "Administrators": {
      "id": "6a1e70e1a88782771a91808c8af9bbb7a9871389",
      "url": "#/admin/groups/uuid-6a1e70e1a88782771a91808c8af9bbb7a9871389",
      "options": {
      },
      "description": "Gerrit Site Administrators",
      "group_id": 1,
      "owner": "Administrators",
      "owner_id": "6a1e70e1a88782771a91808c8af9bbb7a9871389",
      "created_on": "2013-02-01 09:59:32.126000000"
    },
    "Anonymous Users": {
      "id": "global%3AAnonymous-Users",
      "url": "#/admin/groups/uuid-global%3AAnonymous-Users",
      "options": {
      },
      "description": "Any user, signed-in or not",
      "group_id": 2,
      "owner": "Administrators",
      "owner_id": "6a1e70e1a88782771a91808c8af9bbb7a9871389",
      "created_on": "2013-02-01 09:59:32.126000000"
    },
    "MyProject_Committers": {
      "id": "834ec36dd5e0ed21a2ff5d7e2255da082d63bbd7",
      "url": "#/admin/groups/uuid-834ec36dd5e0ed21a2ff5d7e2255da082d63bbd7",
      "options": {
        "visible_to_all": true,
      },
      "group_id": 6,
      "owner": "MyProject_Committers",
      "owner_id": "834ec36dd5e0ed21a2ff5d7e2255da082d63bbd7",
      "created_on": "2013-02-01 09:59:32.126000000"
    },
    "Non-Interactive Users": {
      "id": "5057f3cbd3519d6ab69364429a89ffdffba50f73",
      "url": "#/admin/groups/uuid-5057f3cbd3519d6ab69364429a89ffdffba50f73",
      "options": {
      },
      "description": "Users who perform batch actions on Gerrit",
      "group_id": 4,
      "owner": "Administrators",
      "owner_id": "6a1e70e1a88782771a91808c8af9bbb7a9871389",
      "created_on": "2013-02-01 09:59:32.126000000"
    },
    "Project Owners": {
      "id": "global%3AProject-Owners",
      "url": "#/admin/groups/uuid-global%3AProject-Owners",
      "options": {
      },
      "description": "Any owner of the project",
      "group_id": 5,
      "owner": "Administrators",
      "owner_id": "6a1e70e1a88782771a91808c8af9bbb7a9871389",
      "created_on": "2013-02-01 09:59:32.126000000"
    },
    "Registered Users": {
      "id": "global%3ARegistered-Users",
      "url": "#/admin/groups/uuid-global%3ARegistered-Users",
      "options": {
      },
      "description": "Any signed-in user",
      "group_id": 3,
      "owner": "Administrators",
      "owner_id": "6a1e70e1a88782771a91808c8af9bbb7a9871389",
      "created_on": "2013-02-01 09:59:32.126000000"
    }
  }
```

get::/groups/

#### Group Options

Additional fields can be obtained by adding `o` parameters, each option
requires more lookups and slows down the query response time to the
client so they are generally disabled by default. Optional fields are:

  - `INCLUDES`: include list of direct subgroups.

<!-- end list -->

  - `MEMBERS`: include list of direct group members.

#### Find groups that are owned by another group

By setting `ownedBy` and specifying the [{group-id}](#group-id) of
another group, it is possible to find all the groups for which the
owning group is the given group.

**Request.**

``` 
  GET /groups/?ownedBy=7ca042f4d5847936fcb90ca91057673157fd06fc HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "MyProject-Committers": {
      "id": "9999c971bb4ab872aab759d8c49833ee6b9ff320",
      "url": "#/admin/groups/uuid-9999c971bb4ab872aab759d8c49833ee6b9ff320",
      "options": {
        "visible_to_all": true
      },
      "description":"contains all committers for MyProject",
      "group_id": 551,
      "owner": "MyProject-Owners",
      "owner_id": "7ca042f4d5847936fcb90ca91057673157fd06fc",
      "created_on": "2013-02-01 09:59:32.126000000"
    }
  }
```

#### Check if a group is owned by the calling user

By setting the option `owned` and specifying a group to inspect with the
option `group`/`g`, it is possible to find out if this group is owned by
the calling user.

\[NOTE\] Earlier the `group`/`g` option was named `query`/`q`. Using
`query`/`q` still works, but this option is deprecated and may be
removed in future. Hence all users should be adapted to use `group`/`g`
instead.

**Request.**

``` 
  GET /groups/?owned&q=MyProject-Committers HTTP/1.0
```

If the group is owned by the calling user, the returned map contains
this group. If the calling user doesn’t own this group an empty map is
returned.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "MyProject-Committers": {
      "id": "9999c971bb4ab872aab759d8c49833ee6b9ff320",
      "url": "#/admin/groups/uuid-9999c971bb4ab872aab759d8c49833ee6b9ff320",
      "options": {
        "visible_to_all": true
      },
      "description":"contains all committers for MyProject",
      "group_id": 551,
      "owner": "MyProject-Owners",
      "owner_id": "7ca042f4d5847936fcb90ca91057673157fd06fc",
      "created_on": "2013-02-01 09:59:32.126000000"
    }
  }
```

#### Group Limit

The `/groups/` URL also accepts a limit integer in the `n` parameter.
This limits the results to show `n` groups.

Query the first 25 groups in group list.

``` 
  GET /groups/?n=25 HTTP/1.0
```

The `/groups/` URL also accepts a start integer in the `S` parameter.
The results will skip `S` groups from group list.

Query 25 groups starting from index 50.

``` 
  GET /groups/?n=25&S=50 HTTP/1.0
```

#### Suggest Group

The `suggest` or `s` option indicates a user-entered string that should
be auto-completed to group names. If this option is set and `n` is not
set, then `n` defaults to 10.

When using this option, the `project` or `p` option can be used to name
the current project, to allow context-dependent suggestions.

Not compatible with `visible-to-all`, `owned`, `user`, `match`, `group`,
or `S`. (Attempts to use one of those options combined with `suggest`
will error out.)

**Request.**

``` 
  GET /groups/?suggest=ad&p=All-Projects HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "Administrators": {
      "url": "#/admin/groups/uuid-59b92f35489e62c80d1ab1bf0c2d17843038df8b",
      "options": {},
      "description": "Gerrit Site Administrators",
      "group_id": 1,
      "owner": "Administrators",
      "owner_id": "59b92f35489e62c80d1ab1bf0c2d17843038df8b",
      "created_on": "2013-02-01 09:59:32.126000000",
      "id": "59b92f35489e62c80d1ab1bf0c2d17843038df8b"
    }
  }
```

  - Regex(r)  
    Limit the results to those groups that match the specified regex.
    
    Boundary matchers *^* and *$* are implicit. For example: the regex
    *test.\** will match any groups that start with *test* and regex
    *.\*test* will match any group that end with *test*.
    
    The match is case sensitive.
    
    List all groups that match regex `test.*group`:
    
    **Request.**
    
    ``` 
      GET /groups/?r=test.*group HTTP/1.0
    ```
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Disposition: attachment
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      {
        "test/some-group": {
          "url": "#/admin/groups/uuid-59b92f35489e62c80d1ab1bf0c2d17843038df8b",
          "options": {},
          "description": "Gerrit Site Administrators",
          "group_id": 1,
          "owner": "Administrators",
          "owner_id": "59b92f35489e62c80d1ab1bf0c2d17843038df8b",
          "created_on": "2013-02-01 09:59:32.126000000",
          "id": "59b92f35489e62c80d1ab1bf0c2d17843038df8b"
        }
        "test/some-other-group": {
          "url": "#/admin/groups/uuid-99b92f35489e62c80d1ab1bf0c2d17843038df8b",
          "options": {},
          "description": "Gerrit Site Administrators",
          "group_id": 1,
          "owner": "Administrators",
          "owner_id": "99b92f35489e62c80d1ab1bf0c2d17843038df8b",
          "created_on": "2014-02-01 09:59:32.126000000",
          "id": "99b92f35489e62c80d1ab1bf0c2d17843038df8b"
        }
      }
    ```

  - Substring(m)  
    Limit the results to those groups that match the specified
    substring.
    
    The match is case insensitive.
    
    List all groups that match substring `test/`:
    
    **Request.**
    
    ``` 
      GET /groups/?m=test%2F HTTP/1.0
    ```
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Disposition: attachment
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      {
        "test/test": {
          "url": "#/admin/groups/uuid-786a95e85f9a2223a96545f10003f396aba871f2",
          "options": {},
          "group_id": 15,
          "owner": "test/test",
          "owner_id": "786a95e85f9a2223a96545f10003f396aba871f2",
          "created_on": "2017-07-11 13:56:24.000000000",
          "id": "786a95e85f9a2223a96545f10003f396aba871f2"
        }
      }
    ```

### Query Groups

*GET /groups/?query2=\<query\>*

Queries internal groups visible to the caller. The [query
string](user-search-groups.html#_search_operators) must be provided by
the `query2` parameter. The `start` and `limit` parameters can be used
to skip/limit results.

As result a list of [GroupInfo](#group-info) entities is returned.

\[NOTE\] `query2` is a temporary name and in future this option may be
renamed to `query`. `query2` was chosen to maintain backwards
compatibility with the deprecated `query` parameter on the [List
Groups](#list-groups) endpoint.

**Request.**

``` 
  GET /groups/?query2=inname:test HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "url": "#/admin/groups/uuid-68236a40ca78de8be630312d8ba50250bc5638ae",
      "options": {},
      "description": "Group for running tests on MyProject",
      "group_id": 20,
      "owner": "MyProject-Test-Group",
      "owner_id": "59b92f35489e62c80d1ab1bf0c2d17843038df8b",
      "created_on": "2013-02-01 09:59:32.126000000",
      "id": "68236a40ca78de8be630312d8ba50250bc5638ae"
    },
    {
      "url": "#/admin/groups/uuid-99a534526313324a2667025c3f4e089199b736aa",
      "options": {},
      "description": "Testers for ProjectX",
      "group_id": 17,
      "owner": "ProjectX-Testers",
      "owner_id": "59b92f35489e62c80d1ab1bf0c2d17843038df8b",
      "created_on": "2013-02-01 09:59:32.126000000",
      "id": "99a534526313324a2667025c3f4e089199b736aa"
    }
  ]
```

If the number of groups matching the query exceeds either the internal
limit or a supplied `limit` query parameter, the last group object has a
`_more_groups: true` JSON field set.

#### Group Limit

The `/groups/?query2=<query>` URL also accepts a limit integer in the
`limit` parameter. This limits the results to `limit` groups.

Query the first 25 groups in group list.

``` 
  GET /groups/?query2=<query>&limit=25 HTTP/1.0
```

The `/groups/` URL also accepts a start integer in the `start`
parameter. The results will skip `start` groups from group list.

Query 25 groups starting from index 50.

``` 
  GET /groups/?query2=<query>&limit=25&start=50 HTTP/1.0
```

#### Group Options

Additional fields can be obtained by adding `o` parameters. Each option
requires more lookups and slows down the query response time to the
client so they are generally disabled by default. The supported fields
are described in the context of the [List Groups](#group-options) REST
endpoint.

### Get Group

*GET /groups/[{group-id}](#group-id)*

Retrieves a group.

**Request.**

``` 
  GET /groups/6a1e70e1a88782771a91808c8af9bbb7a9871389 HTTP/1.0
```

As response a [GroupInfo](#group-info) entity is returned that describes
the group.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "6a1e70e1a88782771a91808c8af9bbb7a9871389",
    "name": "Administrators",
    "url": "#/admin/groups/uuid-6a1e70e1a88782771a91808c8af9bbb7a9871389",
    "options": {
    },
    "description": "Gerrit Site Administrators",
    "group_id": 1,
    "owner": "Administrators",
    "owner_id": "6a1e70e1a88782771a91808c8af9bbb7a9871389",
    "created_on": "2013-02-01 09:59:32.126000000"
  }
```

### Create Group

*PUT /groups/[{group-name}](#group-name)*

Creates a new Gerrit internal group.

In the request body additional data for the group can be provided as
[GroupInput](#group-input).

**Request.**

``` 
  PUT /groups/MyProject-Committers HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "description": "contains all committers for MyProject",
    "visible_to_all": true,
    "owner": "MyProject-Owners",
    "owner_id": "7ca042f4d5847936fcb90ca91057673157fd06fc"
  }
```

As response the [GroupInfo](#group-info) entity is returned that
describes the created group.

**Response.**

``` 
  HTTP/1.1 201 Created
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "9999c971bb4ab872aab759d8c49833ee6b9ff320",
    "name": "MyProject-Committers",
    "url": "#/admin/groups/uuid-9999c971bb4ab872aab759d8c49833ee6b9ff320",
    "options": {
      "visible_to_all": true
    },
    "description":"contains all committers for MyProject",
    "group_id": 551,
    "owner": "MyProject-Owners",
    "owner_id": "7ca042f4d5847936fcb90ca91057673157fd06fc",
    "created_on": "2013-02-01 09:59:32.126000000"
  }
```

If the group creation fails because the name is already in use the
response is "`409 Conflict`".

### Get Group Detail

*GET /groups/[{group-id}](#group-id)/detail*

Retrieves a group with the direct [members](#members) and the directly
[included groups](#includes).

**Request.**

``` 
  GET /groups/6a1e70e1a88782771a91808c8af9bbb7a9871389/detail HTTP/1.0
```

As response a [GroupInfo](#group-info) entity is returned that describes
the group.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "6a1e70e1a88782771a91808c8af9bbb7a9871389",
    "name": "Administrators",
    "url": "#/admin/groups/uuid-6a1e70e1a88782771a91808c8af9bbb7a9871389",
    "options": {
    },
    "description": "Gerrit Site Administrators",
    "group_id": 1,
    "owner": "Administrators",
    "owner_id": "6a1e70e1a88782771a91808c8af9bbb7a9871389",
    "created_on": "2013-02-01 09:59:32.126000000",
    "members": [
      {
        "_account_id": 1000097,
        "name": "Jane Roe",
        "email": "jane.roe@example.com",
        "username": "jane"
      },
      {
        "_account_id": 1000096,
        "name": "John Doe",
        "email": "john.doe@example.com"
        "username": "john"
      }
    ],
    "includes": []
  }
```

### Get Group Name

*GET /groups/[{group-id}](#group-id)/name*

Retrieves the name of a group.

**Request.**

``` 
  GET /groups/9999c971bb4ab872aab759d8c49833ee6b9ff320/name HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  "MyProject-Committers"
```

### Rename Group

*PUT /groups/[{group-id}](#group-id)/name*

Renames a Gerrit internal group.

The new group name must be provided in the request body.

**Request.**

``` 
  PUT /groups/MyProject-Committers/name HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "name": "My-Project-Committers"
  }
```

As response the new group name is returned.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  "My-Project-Committers"
```

If renaming the group fails because the new name is already in use the
response is "`409 Conflict`".

### Get Group Description

*GET /groups/[{group-id}](#group-id)/description*

Retrieves the description of a
group.

**Request.**

``` 
  GET /groups/9999c971bb4ab872aab759d8c49833ee6b9ff320/description HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  "contains all committers for MyProject"
```

If the group does not have a description an empty string is returned.

### Set Group Description

*PUT /groups/[{group-id}](#group-id)/description*

Sets the description of a Gerrit internal group.

The new group description must be provided in the request
body.

**Request.**

``` 
  PUT /groups/9999c971bb4ab872aab759d8c49833ee6b9ff320/description HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "description": "The committers of MyProject."
  }
```

As response the new group description is returned.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  "The committers of MyProject."
```

If the description was deleted the response is "`204 No Content`".

### Delete Group Description

*DELETE /groups/[{group-id}](#group-id)/description*

Deletes the description of a Gerrit internal
group.

**Request.**

``` 
  DELETE /groups/9999c971bb4ab872aab759d8c49833ee6b9ff320/description HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

### Get Group Options

*GET /groups/[{group-id}](#group-id)/options*

Retrieves the options of a group.

**Request.**

``` 
  GET /groups/9999c971bb4ab872aab759d8c49833ee6b9ff320/options HTTP/1.0
```

As response a [GroupOptionsInfo](#group-options-info) entity is returned
that describes the options of the group.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "visible_to_all": true
  }
```

### Set Group Options

*PUT /groups/[{group-id}](#group-id)/options*

Sets the options of a Gerrit internal group.

The new group options must be provided in the request body as a
[GroupOptionsInput](#group-options-input) entity.

**Request.**

``` 
  PUT /groups/9999c971bb4ab872aab759d8c49833ee6b9ff320/options HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "visible_to_all": true
  }
```

As response the new group options are returned as a
[GroupOptionsInfo](#group-options-info) entity.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "visible_to_all": true
  }
```

### Get Group Owner

*GET /groups/[{group-id}](#group-id)/owner*

Retrieves the owner group of a Gerrit internal group.

**Request.**

``` 
  GET /groups/9999c971bb4ab872aab759d8c49833ee6b9ff320/owner HTTP/1.0
```

As response a [GroupInfo](#group-info) entity is returned that describes
the owner group.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "6a1e70e1a88782771a91808c8af9bbb7a9871389",
    "name": "Administrators",
    "url": "#/admin/groups/uuid-6a1e70e1a88782771a91808c8af9bbb7a9871389",
    "options": {
    },
    "description": "Gerrit Site Administrators",
    "group_id": 1,
    "owner": "Administrators",
    "owner_id": "6a1e70e1a88782771a91808c8af9bbb7a9871389",
    "created_on": "2013-02-01 09:59:32.126000000"
  }
```

### Set Group Owner

*PUT /groups/[{group-id}](#group-id)/owner*

Sets the owner group of a Gerrit internal group.

The new owner group must be provided in the request body.

The new owner can be specified by name, by group UUID or by the legacy
numeric group ID.

**Request.**

``` 
  PUT /groups/9999c971bb4ab872aab759d8c49833ee6b9ff320/owner HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "owner": "6a1e70e1a88782771a91808c8af9bbb7a9871389"
  }
```

As response a [GroupInfo](#group-info) entity is returned that describes
the new owner group.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "6a1e70e1a88782771a91808c8af9bbb7a9871389",
    "name": "Administrators",
    "url": "#/admin/groups/uuid-6a1e70e1a88782771a91808c8af9bbb7a9871389",
    "options": {
    },
    "description": "Gerrit Site Administrators",
    "group_id": 1,
    "owner": "Administrators",
    "owner_id": "6a1e70e1a88782771a91808c8af9bbb7a9871389",
    "created_on": "2013-02-01 09:59:32.126000000"
  }
```

### Get Audit Log

*GET /groups/[{group-id}](#group-id)/log.audit*

Gets the audit log of a Gerrit internal
group.

**Request.**

``` 
  GET /groups/9999c971bb4ab872aab759d8c49833ee6b9ff320/log.audit HTTP/1.0
```

As response a list of [GroupAuditEventInfo](#group-audit-event-info)
entities is returned that describes the audit events of the group. The
returned audit events are sorted by date in reverse order so that the
newest audit event comes first.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "member": {
        "url": "#/admin/groups/uuid-fdda826a0815859ab48d22a05a43472f0f55f89a",
        "options": {},
        "group_id": 3,
        "owner": "Administrators",
        "owner_id": "e56678641565e7f59dd5c6878f5bcbc842bf150a",
        "created_on": "2013-02-01 09:59:32.126000000",
        "id": "fdda826a0815859ab48d22a05a43472f0f55f89a",
        "name": "MyGroup"
      },
      "type": "REMOVE_GROUP",
      "user": {
        "_account_id": 1000000,
        "name": "Administrator",
        "email": "admin@example.com",
        "username": "admin"
      },
      "date": "2015-07-03 09:22:26.348000000"
    },
    {
      "member": {
        "url": "#/admin/groups/uuid-fdda826a0815859ab48d22a05a43472f0f55f89a",
        "options": {},
        "group_id": 3,
        "owner": "Administrators",
        "owner_id": "e56678641565e7f59dd5c6878f5bcbc842bf150a",
        "created_on": "2013-02-01 09:59:32.126000000",
        "id": "fdda826a0815859ab48d22a05a43472f0f55f89a",
        "name": "MyGroup"
      },
      "type": "ADD_GROUP",
      "user": {
        "_account_id": 1000000,
        "name": "Administrator",
        "email": "admin@example.com",
        "username": "admin"
      },
      "date": "2015-07-03 08:43:36.592000000"
    },
    {
      "member": {
        "_account_id": 1000000,
        "name": "Administrator",
        "email": "admin@example.com",
        "username": "admin"
      },
      "type": "ADD_USER",
      "user": {
        "_account_id": 1000001,
        "name": "John Doe",
        "email": "john.doe@example.com",
        "username": "jdoe"
      },
      "date": "2015-07-01 13:36:36.602000000"
    }
  ]
```

### Index Group

*POST /groups/[{group-id}](#group-id)/index*

Adds or updates the internal group in the secondary index.

**Request.**

``` 
  POST /groups/fdda826a0815859ab48d22a05a43472f0f55f89a/index HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

## Group Member Endpoints

### List Group Members

*GET /groups/[{group-id}](#group-id)/members/*

Lists the direct members of a Gerrit internal group.

As result a list of detailed
[AccountInfo](rest-api-accounts.html#account-info) entries is returned.
The entries in the list are sorted by full name, preferred email and id.

**Request.**

``` 
  GET /groups/834ec36dd5e0ed21a2ff5d7e2255da082d63bbd7/members/ HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "_account_id": 1000097,
      "name": "Jane Roe",
      "email": "jane.roe@example.com",
      "username": "jane"
    },
    {
      "_account_id": 1000096,
      "name": "John Doe",
      "email": "john.doe@example.com",
      "username": "john"
    }
  ]
```

get::/groups/1/members/

To resolve the included groups of a group recursively and to list all
members the parameter `recursive` can be set.

Members from included external groups and from included groups which are
not visible to the calling user are
ignored.

**Request.**

``` 
  GET /groups/834ec36dd5e0ed21a2ff5d7e2255da082d63bbd7/members/?recursive HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "_account_id": 1000097,
      "name": "Jane Roe",
      "email": "jane.roe@example.com",
      "username": "jane"
    },
    {
      "_account_id": 1000096,
      "name": "John Doe",
      "email": "john.doe@example.com",
      "username": "john"
    },
    {
      "_account_id": 1000098,
      "name": "Richard Roe",
      "email": "richard.roe@example.com",
      "username": "rroe"
    }
  ]
```

### Get Group Member

*GET
/groups/[{group-id}](#group-id)/members/[{account-id}](rest-api-accounts.html#account-id)*

Retrieves a group
member.

**Request.**

``` 
  GET /groups/834ec36dd5e0ed21a2ff5d7e2255da082d63bbd7/members/1000096 HTTP/1.0
```

As response a detailed
[AccountInfo](rest-api-accounts.html#account-info) entity is returned
that describes the group member.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "_account_id": 1000096,
    "name": "John Doe",
    "email": "john.doe@example.com",
    "username": "john"
  }
```

### Add Group Member

*PUT
/groups/[{group-id}](#group-id)/members/[{account-id}](rest-api-accounts.html#account-id)*

Adds a user as member to a Gerrit internal group.

**Request.**

``` 
  PUT /groups/MyProject-Committers/members/John%20Doe HTTP/1.0
```

As response a detailed
[AccountInfo](rest-api-accounts.html#account-info) entity is returned
that describes the group member.

**Response.**

``` 
  HTTP/1.1 201 Created
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "_account_id": 1000037,
    "name": "John Doe",
    "email": "john.doe@example.com",
    "username": "john"
  }
```

The request also succeeds if the user is already a member of this group,
but then the HTTP response code is `200 OK`.

### Add Group Members

*POST /groups/[{group-id}](#group-id)/members*

OR

*POST /groups/[{group-id}](#group-id)/members.add*

Adds one or several users to a Gerrit internal group.

The users to be added to the group must be provided in the request body
as a [MembersInput](#members-input) entity.

**Request.**

``` 
  POST /groups/MyProject-Committers/members.add HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "members": [
      "jane.roe@example.com",
      "john.doe@example.com"
    ]
  }
```

As response a list of detailed
[AccountInfo](rest-api-accounts.html#account-info) entities is returned
that describes the group members that were specified in the
[MembersInput](#members-input). An
[AccountInfo](rest-api-accounts.html#account-info) entity is returned
for each user specified in the input, independently of whether the user
was newly added to the group or whether the user was already a member of
the group.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "_account_id": 1000057,
      "name": "Jane Roe",
      "email": "jane.roe@example.com",
      "username": "jane"
    },
    {
      "_account_id": 1000037,
      "name": "John Doe",
      "email": "john.doe@example.com",
      "username": "john"
    }
  ]
```

### Remove Group Member

*DELETE
/groups/[{group-id}](#group-id)/members/[{account-id}](rest-api-accounts.html#account-id)*

Removes a user from a Gerrit internal group.

**Request.**

``` 
  DELETE /groups/MyProject-Committers/members/John%20Doe HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

### Remove Group Members

*POST /groups/[{group-id}](#group-id)/members.delete*

Removes one or several users from a Gerrit internal group.

The users to be removed from the group must be provided in the request
body as a [MembersInput](#members-input) entity.

**Request.**

``` 
  POST /groups/MyProject-Committers/members.delete HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "members": [
      "jane.roe@example.com",
      "john.doe@example.com"
    ]
  }
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

## Subgroup Endpoints

### List Subgroups

*GET /groups/[{group-id}](#group-id)/groups/*

Lists the direct subgroups of a group.

As result a list of [GroupInfo](#group-info) entries is returned. The
entries in the list are sorted by group name and UUID.

**Request.**

``` 
  GET /groups/834ec36dd5e0ed21a2ff5d7e2255da082d63bbd7/groups/ HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "id": "7ca042f4d5847936fcb90ca91057673157fd06fc",
      "name": "MyProject-Verifiers",
      "url": "#/admin/groups/uuid-7ca042f4d5847936fcb90ca91057673157fd06fc",
      "options": {
      },
      "group_id": 38,
      "owner": "MyProject-Verifiers",
      "owner_id": "7ca042f4d5847936fcb90ca91057673157fd06fc",
      "created_on": "2013-02-01 09:59:32.126000000"
    }
  ]
```

### Get Subgroup

*GET /groups/[{group-id}](#group-id)/groups/[{group-id}](#group-id)*

Retrieves a
subgroup.

**Request.**

``` 
  GET /groups/834ec36dd5e0ed21a2ff5d7e2255da082d63bbd7/groups/7ca042f4d5847936fcb90ca91057673157fd06fc HTTP/1.0
```

As response a [GroupInfo](#group-info) entity is returned that describes
the subgroup.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "7ca042f4d5847936fcb90ca91057673157fd06fc",
    "name": "MyProject-Verifiers",
    "url": "#/admin/groups/uuid-7ca042f4d5847936fcb90ca91057673157fd06fc",
    "options": {
    },
    "group_id": 38,
    "owner": "Administrators",
    "owner_id": "6a1e70e1a88782771a91808c8af9bbb7a9871389",
    "created_on": "2013-02-01 09:59:32.126000000"
  }
```

### Add Subgroup

*PUT /groups/[{group-id}](#group-id)/groups/[{group-id}](#group-id)*

Adds an internal or external group as subgroup to a Gerrit internal
group. External groups must be specified using the UUID.

**Request.**

``` 
  PUT /groups/MyProject-Committers/groups/MyGroup HTTP/1.0
```

As response a [GroupInfo](#group-info) entity is returned that describes
the subgroup.

**Response.**

``` 
  HTTP/1.1 201 Created
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "6a1e70e1a88782771a91808c8af9bbb7a9871389",
    "name": "MyGroup",
    "url": "#/admin/groups/uuid-6a1e70e1a88782771a91808c8af9bbb7a9871389",
    "options": {
    },
    "group_id": 8,
    "owner": "Administrators",
    "owner_id": "6a1e70e1a88782771a91808c8af9bbb7a9871389",
    "created_on": "2013-02-01 09:59:32.126000000"
  }
```

The request also succeeds if the group is already a subgroup of this
group.

### Add Subgroups

*POST /groups/[{group-id}](#group-id)/groups*

OR

*POST /groups/[{group-id}](#group-id)/groups.add*

Adds one or several groups as subgroups to a Gerrit internal group.

The subgroups to be added must be provided in the request body as a
[GroupsInput](#groups-input) entity.

**Request.**

``` 
  POST /groups/MyProject-Committers/groups.add HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "groups": [
      "MyGroup",
      "MyOtherGroup"
    ]
  }
```

As response a list of [GroupInfo](#group-info) entities is returned that
describes the groups that were specified in the
[GroupsInput](#groups-input). A [GroupInfo](#group-info) entity is
returned for each group specified in the input, independently of whether
the group was newly added as subgroup or whether the group was already a
subgroup of the group.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "id": "6a1e70e1a88782771a91808c8af9bbb7a9871389",
      "name": "MyGroup",
      "url": "#/admin/groups/uuid-6a1e70e1a88782771a91808c8af9bbb7a9871389",
      "options": {
      },
      "group_id": 8,
      "owner": "Administrators",
      "owner_id": "6a1e70e1a88782771a91808c8af9bbb7a9871389",
      "created_on": "2013-02-01 09:59:32.126000000"
    },
    {
      "id": "5057f3cbd3519d6ab69364429a89ffdffba50f73",
      "name": "MyOtherGroup",
      "url": "#/admin/groups/uuid-5057f3cbd3519d6ab69364429a89ffdffba50f73",
      "options": {
      },
      "group_id": 10,
      "owner": "MyOtherGroup",
      "owner_id": "5057f3cbd3519d6ab69364429a89ffdffba50f73",
      "created_on": "2013-02-01 09:59:32.126000000"
    }
  ]
```

### Remove Subgroup

*DELETE /groups/[{group-id}](#group-id)/groups/[{group-id}](#group-id)*

Removes a subgroup from a Gerrit internal group.

**Request.**

``` 
  DELETE /groups/MyProject-Committers/groups/MyGroup HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

### Remove Subgroups

*POST /groups/[{group-id}](#group-id)/groups.delete*

Removes one or several subgroups from a Gerrit internal group.

The subgroups to be removed must be provided in the request body as a
[GroupsInput](#groups-input) entity.

**Request.**

``` 
  POST /groups/MyProject-Committers/groups.delete HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "groups": [
      "MyGroup",
      "MyOtherGroup"
    ]
  }
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

## IDs

### {group-id}

Identifier for a group.

This can be:

  - the UUID of the group

  - the legacy numeric ID of the group

  - the name of the group if it is unique

### {group-name}

Group name that uniquely identifies one group.

## JSON Entities

### GroupAuditEventInfo

The `GroupAuditEventInfo` entity contains information about an audit
event of a group.

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
<td><p><code>member</code></p></td>
<td><p>The group member that is added/removed. If <code>type</code> is <code>ADD_USER</code> or <code>REMOVE_USER</code> the member is returned as detailed <a href="rest-api-accounts.html#account-info">AccountInfo</a> entity, if <code>type</code> is <code>ADD_GROUP</code> or <code>REMOVE_GROUP</code> the member is returned as <a href="#group-info">GroupInfo</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>type</code></p></td>
<td><p>The event type, can be: <code>ADD_USER</code>, <code>REMOVE_USER</code>, <code>ADD_GROUP</code> or <code>REMOVE_GROUP</code>.</p>
<p><code>ADD_USER</code>: A user was added as member to the group.</p>
<p><code>REMOVE_USER</code>: A user member was removed from the group.</p>
<p><code>ADD_GROUP</code>: A group was included as member in the group.</p>
<p><code>REMOVE_GROUP</code>: An included group was removed from the group.</p></td>
</tr>
<tr class="odd">
<td><p><code>user</code></p></td>
<td><p>The user that did the add/remove as detailed <a href="rest-api-accounts.html#account-info">AccountInfo</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>date</code></p></td>
<td><p>The timestamp of the event.</p></td>
</tr>
</tbody>
</table>

### GroupInfo

The `GroupInfo` entity contains information about a group. This can be a
Gerrit internal group, or an external group that is known to Gerrit.

<table>
<colgroup>
<col width="14%" />
<col width="14%" />
<col width="71%" />
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
<td><p>The URL encoded UUID of the group.</p></td>
</tr>
<tr class="even">
<td><p><code>name</code></p></td>
<td><p>not set if returned in a map where the group name is used as map key</p></td>
<td><p>The name of the group.</p></td>
</tr>
<tr class="odd">
<td><p><code>url</code></p></td>
<td><p>optional</p></td>
<td><p>URL to information about the group. Typically a URL to a web page that permits users to apply to join the group, or manage their membership.</p></td>
</tr>
<tr class="even">
<td><p><code>options</code></p></td>
<td></td>
<td><p><a href="#group-options-info">Options of the group</a></p></td>
</tr>
<tr class="odd">
<td><p><code>description</code></p></td>
<td><p>only for internal groups</p></td>
<td><p>The description of the group.</p></td>
</tr>
<tr class="even">
<td><p><code>group_id</code></p></td>
<td><p>only for internal groups</p></td>
<td><p>The numeric ID of the group.</p></td>
</tr>
<tr class="odd">
<td><p><code>owner</code></p></td>
<td><p>only for internal groups</p></td>
<td><p>The name of the owner group.</p></td>
</tr>
<tr class="even">
<td><p><code>owner_id</code></p></td>
<td><p>only for internal groups</p></td>
<td><p>The URL encoded UUID of the owner group.</p></td>
</tr>
<tr class="odd">
<td><p><code>created_on</code></p></td>
<td><p>only for internal groups</p></td>
<td><p>The <a href="rest-api.html#timestamp">timestamp</a> of when the group was created.</p></td>
</tr>
<tr class="even">
<td><p><code>_more_groups</code></p></td>
<td><p>optional, only for internal groups, not set if <code>false</code></p></td>
<td><p>Whether the query would deliver more results if not limited.<br />
Only set on the last group that is returned by a <a href="#query-groups">group query</a>.</p></td>
</tr>
<tr class="odd">
<td><p><code>members</code></p></td>
<td><p>optional, only for internal groups</p></td>
<td><p>A list of <a href="rest-api-accounts.html#account-info">AccountInfo</a> entities describing the direct members.<br />
Only set if <a href="#members">members</a> are requested.</p></td>
</tr>
<tr class="even">
<td><p><code>includes</code></p></td>
<td><p>optional, only for internal groups</p></td>
<td><p>A list of <a href="#group-info">GroupInfo</a> entities describing the direct subgroups.<br />
Only set if <a href="#includes">subgroups</a> are requested.</p></td>
</tr>
</tbody>
</table>

The type of a group can be deduced from the group’s UUID:

<table>
<colgroup>
<col width="50%" />
<col width="50%" />
</colgroup>
<tbody>
<tr class="odd">
<td><p>UUID matches &quot;^[0-9a-f]{40}$&quot;</p></td>
<td><p>Gerrit internal group</p></td>
</tr>
<tr class="even">
<td><p>UUID starts with &quot;global:&quot;</p></td>
<td><p>Gerrit system group</p></td>
</tr>
<tr class="odd">
<td><p>UUID starts with &quot;ldap:&quot;</p></td>
<td><p>LDAP group</p></td>
</tr>
<tr class="even">
<td><p>UUID starts with &quot;&lt;prefix&gt;:&quot;</p></td>
<td><p>other external group</p></td>
</tr>
</tbody>
</table>

### GroupInput

The *GroupInput* entity contains information for the creation of a new
internal group.

<table>
<colgroup>
<col width="14%" />
<col width="14%" />
<col width="71%" />
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
<td><p><code>name</code></p></td>
<td><p>optional</p></td>
<td><p>The name of the group (not encoded).<br />
If set, must match the group name in the URL.</p></td>
</tr>
<tr class="even">
<td><p><code>description</code></p></td>
<td><p>optional</p></td>
<td><p>The description of the group.</p></td>
</tr>
<tr class="odd">
<td><p><code>visible_to_all</code></p></td>
<td><p>optional</p></td>
<td><p>Whether the group is visible to all registered users.<br />
<code>false</code> if not set.</p></td>
</tr>
<tr class="even">
<td><p><code>owner_id</code></p></td>
<td><p>optional</p></td>
<td><p>The URL encoded ID of the owner group.<br />
This can be a group UUID, a legacy numeric group ID or a unique group name.<br />
If not set, the new group will be self-owned.</p></td>
</tr>
<tr class="odd">
<td><p><code>members</code></p></td>
<td><p>optional</p></td>
<td><p>The initial members in a list of<br />
<a href="rest-api-accounts.html#account-id">account ids</a>.</p></td>
</tr>
</tbody>
</table>

### GroupOptionsInfo

Options of the group.

<table>
<colgroup>
<col width="14%" />
<col width="14%" />
<col width="71%" />
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
<td><p><code>visible_to_all</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the group is visible to all registered users.</p></td>
</tr>
</tbody>
</table>

### GroupOptionsInput

New options for a group.

<table>
<colgroup>
<col width="14%" />
<col width="14%" />
<col width="71%" />
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
<td><p><code>visible_to_all</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the group is visible to all registered users.</p></td>
</tr>
</tbody>
</table>

### GroupsInput

The `GroupsInput` entity contains information about groups that should
be included into a group or that should be deleted from a group.

<table>
<colgroup>
<col width="14%" />
<col width="14%" />
<col width="71%" />
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
<td><p><code>_one_group</code></p></td>
<td><p>optional</p></td>
<td><p>The <a href="#group-id">id</a> of one group that should be included or deleted.</p></td>
</tr>
<tr class="even">
<td><p><code>groups</code></p></td>
<td><p>optional</p></td>
<td><p>A list of <a href="#group-id">group ids</a> that identify the groups that should be included or deleted.</p></td>
</tr>
</tbody>
</table>

### MembersInput

The `MembersInput` entity contains information about accounts that
should be added as members to a group or that should be deleted from the
group.

<table>
<colgroup>
<col width="14%" />
<col width="14%" />
<col width="71%" />
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
<td><p><code>_one_member</code></p></td>
<td><p>optional</p></td>
<td><p>The <a href="rest-api-accounts.html#account-id">id</a> of one account that should be added or deleted.</p></td>
</tr>
<tr class="even">
<td><p><code>members</code></p></td>
<td><p>optional</p></td>
<td><p>A list of <a href="rest-api-accounts.html#account-id">account ids</a> that identify the accounts that should be added or deleted.</p></td>
</tr>
</tbody>
</table>

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

