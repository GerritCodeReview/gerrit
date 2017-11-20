---
title: " Gerrit Code Review - /projects/ REST API"
sidebar: restapi_sidebar
permalink: rest-api-projects.html
---
This page describes the project related REST endpoints. Please also take
note of the general information on the [REST API](rest-api.html).

## Project Endpoints

### List Projects

*GET /projects/*

Lists the projects accessible by the caller. This is the same as using
the [ls-projects](cmd-ls-projects.html) command over SSH, and accepts
the same options as query parameters.

As result a map is returned that maps the project names to
[ProjectInfo](#project-info) entries. The entries in the map are sorted
by project name.

**Request.**

``` 
  GET /projects/?d HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "external/bison": {
      "id": "external%2Fbison",
      "description": "GNU parser generator"
    },
    "external/gcc": {
      "id": "external%2Fgcc"
    },
    "external/openssl": {
      "id": "external%2Fopenssl",
      "description": "encryption\ncrypto routines"
    },
    "test": {
      "id": "test",
      "description": "\u003chtml\u003e is escaped"
    }
  }
```

#### Project Options

  - Branch(b)  
    Limit the results to the projects having the specified branch and
    include the sha1 of the branch in the results.
    
    Get projects that have a *master* branch:
    
    **Request.**
    
        GET /projects/?b=master HTTP/1.0
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Disposition: attachment
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      {
        "some-project": {
          "id": "some-project",
          "branches": {
            "master": "c5ed9dfcbf002ca0e432d788dab6ca2387829ca7"
          }
        },
        "some-other-project": {
          "id": "some-other-project",
          "branches": {
            "master": "ef1c270142f9581ecf768f4193fc8f8a81102ec2"
          }
        },
      }
    ```

  - Description(d)  
    Include project description in the results.
    
    Get all the projects with their description:
    
    **Request.**
    
        GET /projects/?d HTTP/1.0
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Disposition: attachment
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      {
        "some-project": {
          "id": "some-project",
          "description": "Description of some project."
        },
        "some-other-project": {
          "id": "some-other-project",
           "description": "Description of some other project."
          }
        },
      }
    ```

  - Limit(n)  
    Limit the number of projects to be included in the results.
    
    Query the first project in the project list:
    
    **Request.**
    
    ``` 
      GET /projects/?n=1 HTTP/1.0
    ```
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Disposition: attachment
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      {
        "some-project": {
          "id": "some-project"
        }
      }
    ```

  - Prefix(p)  
    Limit the results to those projects that start with the specified
    prefix.
    
    The match is case sensitive. May not be used together with `m` or
    `r`.
    
    List all projects that start with `platform/`:
    
    **Request.**
    
    ``` 
      GET /projects/?p=platform%2F HTTP/1.0
    ```
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Disposition: attachment
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      {
        "platform/drivers": {
          "id": "platform%2Fdrivers"
        },
        "platform/tools": {
          "id": "platform%2Ftools"
        }
      }
    ```
    
    E.g. this feature can be used by suggestion client UI’s to limit
    results.

  - Regex(r)  
    Limit the results to those projects that match the specified regex.
    
    Boundary matchers *^* and *$* are implicit. For example: the regex
    *test.\** will match any projects that start with *test* and regex
    *.\*test* will match any project that end with *test*.
    
    The match is case sensitive. May not be used together with `m` or
    `p`.
    
    List all projects that match regex `test.*project`:
    
    **Request.**
    
    ``` 
      GET /projects/?r=test.*project HTTP/1.0
    ```
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Disposition: attachment
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      {
        "test/some-project": {
          "id": "test%2Fsome-project"
        },
        "test/some-other-project": {
          "id": "test%2Fsome-other-project"
        }
      }
    ```

  - Skip(S)  
    Skip the given number of projects from the beginning of the list.
    
    Query the second project in the project list:
    
    **Request.**
    
    ``` 
      GET /projects/?n=1&S=1 HTTP/1.0
    ```
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Disposition: attachment
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      {
        "some-other-project": {
          "id": "some-other-project"
        }
      }
    ```

  - Substring(m)  
    Limit the results to those projects that match the specified
    substring.
    
    The match is case insensitive. May not be used together with `r` or
    `p`.
    
    List all projects that match substring `test/`:
    
    **Request.**
    
    ``` 
      GET /projects/?m=test%2F HTTP/1.0
    ```
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Disposition: attachment
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      {
        "test/some-project": {
          "id": "test%2Fsome-project"
        },
        "some-path/test/some-other-project": {
          "id": "some-path%2Ftest%2Fsome-other-project"
        }
      }
    ```

  - Tree(t)  
    Get projects inheritance in a tree-like format. This option does not
    work together with the branch option.
    
    Get all the projects with tree option:
    
    **Request.**
    
        GET /projects/?t HTTP/1.0
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Disposition: attachment
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      {
        "All-Projects" {
          "id": "All-Projects"
        },
        "child-project": {
          "id": "child-project",
          "parent":"parent-project"
        },
        "parent-project": {
          "id": "parent-project",
          "parent":"All-Projects"
        }
      }
    ```

  - Type(type)  
    Get projects with specified type: ALL, CODE, PERMISSIONS.
    
    Get all the projects of type *PERMISSIONS*:
    
    **Request.**
    
        GET /projects/?type=PERMISSIONS HTTP/1.0
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Disposition: attachment
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      {
        "All-Projects" {
          "id": "All-Projects"
        },
        "some-parent-project": {
          "id": "some-parent-project"
        }
      }
    ```

  - All  
    Get all projects, including those whose state is "HIDDEN".
    
    **Request.**
    
        GET /projects/?all HTTP/1.0
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Disposition: attachment
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      {
        "All-Projects" {
          "id": "All-Projects",
          "state": "ACTIVE"
        },
        "some-other-project": {
          "id": "some-other-project",
          "state": "HIDDEN"
        }
      }
    ```

### Query Projects

*GET /projects/?query=\<query\>*

Queries projects visible to the caller. The [query
string](user-search-projects.html#_search_operators) must be provided by
the `query` parameter. The `start` and `limit` parameters can be used to
skip/limit results.

As result a list of [ProjectInfo](#project-info) entities is returned.

**Request.**

``` 
  GET /projects/?query=name:test HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "test": {
      "id": "test",
      "description": "\u003chtml\u003e is escaped"
    }
  }
```

#### Project Limit

The `/projects/?query=<query>` URL also accepts a limit integer in the
`limit` parameter. This limits the results to `limit` projects.

Query the first 25 projects in project list.

``` 
  GET /projects/?query=<query>&limit=25 HTTP/1.0
```

The `/projects/` URL also accepts a start integer in the `start`
parameter. The results will skip `start` groups from project list.

Query 25 projects starting from index 50.

``` 
  GET /groups/?query=<query>&limit=25&start=50 HTTP/1.0
```

#### Project Options

Additional fields can be obtained by adding `o` parameters. Each option
requires more lookups and slows down the query response time to the
client so they are generally disabled by default. The supported fields
are described in the context of the [List Projects](#project-options)
REST endpoint.

### Get Project

*GET /projects/[{project-name}](#project-name)*

Retrieves a project.

**Request.**

``` 
  GET /projects/plugins%2Freplication HTTP/1.0
```

As response a [ProjectInfo](#project-info) entity is returned that
describes the project.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "plugins%2Freplication",
    "name": "plugins/replication",
    "parent": "Public-Plugins",
    "description": "Copies to other servers using the Git protocol",
    "state": "ACTIVE",
    "labels": {
      "Code-Review": {
        "values": {
          " 0": "No score",
          "+1": "Approved"
        },
        "default_value": 0
      }
    }
  }
```

### Create Project

*PUT /projects/[{project-name}](#project-name)*

Creates a new project.

In the request body additional data for the project can be provided as
[ProjectInput](#project-input).

**Request.**

``` 
  PUT /projects/MyProject HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "description": "This is a demo project.",
    "submit_type": "CHERRY_PICK",
    "owners": [
      "MyProject-Owners"
    ]
  }
```

As response the [ProjectInfo](#project-info) entity is returned that
describes the created project.

**Response.**

``` 
  HTTP/1.1 201 Created
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "MyProject",
    "name": "MyProject",
    "parent": "All-Projects",
    "description": "This is a demo project.",
    "labels": {
      "Code-Review": {
        "values": {
          " 0": "No score",
          "+1": "Approved"
        },
        "default_value": 0
      }
    }
  }
```

### Get Project Description

*GET /projects/[{project-name}](#project-name)/description*

Retrieves the description of a project.

**Request.**

``` 
  GET /projects/plugins%2Freplication/description HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  "Copies to other servers using the Git protocol"
```

If the project does not have a description an empty string is returned.

### Set Project Description

*PUT /projects/[{project-name}](#project-name)/description*

Sets the description of a project.

The new project description must be provided in the request body inside
a [ProjectDescriptionInput](#project-description-input) entity.

**Request.**

``` 
  PUT /projects/plugins%2Freplication/description HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "description": "Plugin for Gerrit that handles the replication.",
    "commit_message": "Update the project description"
  }
```

As response the new project description is returned.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  "Plugin for Gerrit that handles the replication."
```

If the description was deleted the response is "`204 No Content`".

### Delete Project Description

*DELETE /projects/[{project-name}](#project-name)/description*

Deletes the description of a project.

The request body does not need to include a
[ProjectDescriptionInput](#project-description-input) entity if no
commit message is specified.

Please note that some proxies prohibit request bodies for DELETE
requests. In this case, if you want to specify a commit message, use
[PUT](#set-project-description) to delete the description.

**Request.**

``` 
  DELETE /projects/plugins%2Freplication/description HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

### Get Project Parent

*GET /projects/[{project-name}](#project-name)/parent*

Retrieves the name of a project’s parent project. For the `All-Projects`
root project an empty string is returned.

**Request.**

``` 
  GET /projects/plugins%2Freplication/parent HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  "All-Projects"
```

### Set Project Parent

*PUT /projects/[{project-name}](#project-name)/parent*

Sets the parent project for a project.

The new name of the parent project must be provided in the request body
inside a [ProjectParentInput](#project-parent-input) entity.

**Request.**

``` 
  PUT /projects/plugins%2Freplication/parent HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "parent": "Public-Plugins",
    "commit_message": "Update the project parent"
  }
```

As response the new parent project name is returned.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  "Public-Plugins"
```

### Get HEAD

*GET /projects/[{project-name}](#project-name)/HEAD*

Retrieves for a project the name of the branch to which `HEAD` points.

**Request.**

``` 
  GET /projects/plugins%2Freplication/HEAD HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  "refs/heads/master"
```

### Set HEAD

*PUT /projects/[{project-name}](#project-name)/HEAD*

Sets `HEAD` for a project.

The new ref to which `HEAD` should point must be provided in the request
body inside a [HeadInput](#head-input) entity.

**Request.**

``` 
  PUT /projects/plugins%2Freplication/HEAD HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "ref": "refs/heads/stable"
  }
```

As response the new ref to which `HEAD` points is returned.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  "refs/heads/stable"
```

### Get Repository Statistics

*GET /projects/[{project-name}](#project-name)/statistics.git*

Return statistics for the repository of a project.

**Request.**

``` 
  GET /projects/plugins%2Freplication/statistics.git HTTP/1.0
```

The repository statistics are returned as a
[RepositoryStatisticsInfo](#repository-statistics-info) entity.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "number_of_loose_objects": 127,
    "number_of_loose_refs": 15,
    "number_of_pack_files": 15,
    "number_of_packed_objects": 67,
    "number_of_packed_refs": 0,
    "size_of_loose_objects": 29466,
    "size_of_packed_objects": 9646
  }
```

### Get Config

*GET /projects/[{project-name}](#project-name)/config*

Gets some configuration information about a project. Note that this
config info is not simply the contents of `project.config`; it generally
contains fields that may have been inherited from parent projects.

**Request.**

``` 
  GET /projects/myproject/config
```

A [ConfigInfo](#config-info) entity is returned that describes the
project configuration. Some fields are only visible to users that have
read access to `refs/meta/config`.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "description": "demo project",
    "use_contributor_agreements": {
      "value": true,
      "configured_value": "TRUE",
      "inherited_value": false
    },
    "use_content_merge": {
      "value": true,
      "configured_value": "INHERIT",
      "inherited_value": true
    },
    "use_signed_off_by": {
      "value": false,
      "configured_value": "INHERIT",
      "inherited_value": false
    },
    "create_new_change_for_all_not_in_target": {
      "value": false,
      "configured_value": "INHERIT",
      "inherited_value": false
    },
    "require_change_id": {
      "value": false,
      "configured_value": "FALSE",
      "inherited_value": true
    },
    "max_object_size_limit": {
      "value": "15m",
      "configured_value": "15m",
      "inherited_value": "20m"
    },
    "submit_type": "MERGE_IF_NECESSARY",
    "state": "ACTIVE",
    "commentlinks": {},
    "plugin_config": {
      "helloworld": {
        "language": {
          "display_name": "Preferred Language",
          "type": "STRING",
          "value": "en"
        }
      }
    },
    "actions": {
      "cookbook~hello-project": {
        "method": "POST",
        "label": "Say hello",
        "title": "Say hello in different languages",
        "enabled": true
      }
    }
  }
```

### Set Config

*PUT /projects/[{project-name}](#project-name)/config*

Sets the configuration of a project.

The new configuration must be provided in the request body as a
[ConfigInput](#config-input) entity.

**Request.**

``` 
  PUT /projects/myproject/config HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "description": "demo project",
    "use_contributor_agreements": "FALSE",
    "use_content_merge": "INHERIT",
    "use_signed_off_by": "INHERIT",
    "create_new_change_for_all_not_in_target": "INHERIT",
    "enable_signed_push": "INHERIT",
    "require_signed_push": "INHERIT",
    "reject_implicit_merges": "INHERIT",
    "require_change_id": "TRUE",
    "max_object_size_limit": "10m",
    "submit_type": "REBASE_IF_NECESSARY",
    "state": "ACTIVE"
  }
```

As response the new configuration is returned as a
[ConfigInfo](#config-info) entity.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "use_contributor_agreements": {
      "value": false,
      "configured_value": "FALSE",
      "inherited_value": false
    },
    "use_content_merge": {
      "value": true,
      "configured_value": "INHERIT",
      "inherited_value": true
    },
    "use_signed_off_by": {
      "value": false,
      "configured_value": "INHERIT",
      "inherited_value": false
    },
    "create_new_change_for_all_not_in_target": {
      "value": true,
      "configured_value": "INHERIT",
      "inherited_value": false
    },
    "require_change_id": {
      "value": true,
      "configured_value": "TRUE",
      "inherited_value": true
    },
    "enable_signed_push": {
      "value": true,
      "configured_value": "INHERIT",
      "inherited_value": false
    },
    "require_signed_push": {
      "value": false,
      "configured_value": "INHERIT",
      "inherited_value": false
    },
    "reject_implicit_merges": {
      "value": false,
      "configured_value": "INHERIT",
      "inherited_value": false
    },
    "max_object_size_limit": {
      "value": "10m",
      "configured_value": "10m",
      "inherited_value": "20m"
    },
    "submit_type": "REBASE_IF_NECESSARY",
    "state": "ACTIVE",
    "commentlinks": {}
  }
```

### Run GC

*POST /projects/[{project-name}](#project-name)/gc*

Run the Git garbage collection for the repository of a project.

Options for the Git garbage collection can be specified in the request
body as a [GCInput](#gc-input) entity.

**Request.**

``` 
  POST /projects/plugins%2Freplication/gc HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "show_progress": true
  }
```

The response is the streamed output of the garbage collection.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: text/plain; charset=UTF-8

  collecting garbage for "plugins/replication":
  Pack refs:              100% (21/21)
  Counting objects:       20
  Finding sources:        100% (20/20)
  Getting sizes:          100% (13/13)
  Compressing objects:     83% (5/6)
  Writing objects:        100% (20/20)
  Selecting commits:      100% (7/7)
  Building bitmaps:       100% (7/7)
  Finding sources:        100% (41/41)
  Getting sizes:          100% (25/25)
  Compressing objects:     52% (12/23)
  Writing objects:        100% (41/41)
  Prune loose objects also found in pack files: 100% (36/36)
  Prune loose, unreferenced objects: 100% (36/36)
  done.
```

#### Asynchronous Execution

The option `async` allows to schedule a background task that
asynchronously executes a Git garbage collection.

The `Location` header of the response refers to the [background
task](rest-api-config.html#get-task) which allows to inspect the
progress of its execution. In case of asynchronous execution the
`show_progress` option is ignored.

**Request.**

``` 
  POST /projects/plugins%2Freplication/gc HTTP/1.0
  Content-Type: application/json;charset=UTF-8

  {
    "async": true
  }
```

The response is empty.

**Response.**

``` 
  HTTP/1.1 202 Accepted
  Content-Disposition: attachment
  Location: https:<host>/a/config/server/tasks/383a0602
```

### Ban Commit

*PUT /projects/[{project-name}](#project-name)/ban*

Marks commits as banned for the project. If a commit is banned Gerrit
rejects every push that includes this commit with [contains banned
commit …](error-contains-banned-commit.html).

> **Note**
> 
> This REST endpoint only marks the commits as banned, but it does not
> remove the commits from the history of any central branch. This needs
> to be done manually.

The commits to be banned must be specified in the request body as a
[BanInput](#ban-input) entity.

The caller must be project owner.

**Request.**

``` 
  PUT /projects/plugins%2Freplication/ban HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "commits": [
      "a8a477efffbbf3b44169bb9a1d3a334cbbd9aa96",
      "cf5b56541f84b8b57e16810b18daca9c3adc377b"
    ],
    "reason": "Violates IP"
  }
```

As response a [BanResultInfo](#ban-result-info) entity is returned.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "newly_banned": [
      "a8a477efffbbf3b44169bb9a1d3a334cbbd9aa96",
      "cf5b56541f84b8b57e16810b18daca9c3adc377b"
    ]
  }
```

### List Access Rights for Project

*GET
/projects/[{project-name}](rest-api-projects.html#project-name)/access*

Lists the access rights for a single project.

As result a [ProjectAccessInfo](#project-access-info) entity is
returned.

**Request.**

``` 
  GET /projects/MyProject/access HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "revision": "61157ed63e14d261b6dca40650472a9b0bd88474",
    "inherits_from": {
      "id": "All-Projects",
      "name": "All-Projects",
      "description": "Access inherited by all other projects."
    },
    "local": {
        "refs/*": {
          "permissions": {
            "read": {
              "rules": {
                "c2ce4749a32ceb82cd6adcce65b8216e12afb41c": {
                  "action": "ALLOW",
                  "force": false
                },
                "global:Anonymous-Users": {
                  "action": "ALLOW",
                  "force": false
                }
              }
            }
          }
        }
    },
    "is_owner": true,
    "owner_of": [
      "refs/*"
    ],
    "can_upload": true,
    "can_add": true,
    "config_visible": true,
    "groups": {
      "c2ce4749a32ceb82cd6adcce65b8216e12afb41c": {
        "url": "#/admin/groups/uuid-c2ce4749a32ceb82cd6adcce65b8216e12afb41c",
        "options": {},
        "description": "Users who perform batch actions on Gerrit",
        "group_id": 2,
        "owner": "Administrators",
        "owner_id": "d5b7124af4de52924ed397913e2c3b37bf186948",
        "created_on": "2009-06-08 23:31:00.000000000",
        "name": "Non-Interactive Users"
      },
      "global:Anonymous-Users": {
        "options": {},
        "name": "Anonymous Users"
      }
    }
  }
```

### Add, Update and Delete Access Rights for Project

*POST
/projects/[{project-name}](rest-api-projects.html#project-name)/access*

Sets access rights for the project using the diff schema provided by
[ProjectAccessInput](#project-access-input). Deductions are used to
remove access sections, permissions or permission rules. The backend
will remove the entity with the finest granularity in the request,
meaning that if an access section without permissions is posted, the
access section will be removed; if an access section with a permission
but no permission rules is posted, the permission will be removed; if an
access section with a permission and a permission rule is posted, the
permission rule will be removed.

Additionally, access sections and permissions will be cleaned up after
applying the deductions by removing items that have no child elements.

After removals have been applied, additions will be applied.

As result a [ProjectAccessInfo](#project-access-info) entity is
returned.

**Request.**

``` 
  POST /projects/MyProject/access HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "remove": [
      "refs/*": {
        "permissions": {
          "read": {
            "rules": {
              "c2ce4749a32ceb82cd6adcce65b8216e12afb41c": {
                "action": "ALLOW"
              }
            }
          }
        }
      }
    ]
  }
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "revision": "61157ed63e14d261b6dca40650472a9b0bd88474",
    "inherits_from": {
      "id": "All-Projects",
      "name": "All-Projects",
      "description": "Access inherited by all other projects."
    },
    "local": {
        "refs/*": {
          "permissions": {
            "read": {
              "rules": {
                "global:Anonymous-Users": {
                  "action": "ALLOW",
                  "force": false
                }
              }
            }
          }
        }
    },
    "is_owner": true,
    "owner_of": [
      "refs/*"
    ],
    "can_upload": true,
    "can_add": true,
    "config_visible": true,
    "groups": {
      "global:Anonymous-Users": {
        "options": {},
        "name": "Anonymous Users"
      }
    }
  }
```

### Create Access Rights Change for review.

'PUT
/projects/[{project-name}](rest-api-projects.html#project-name)/access:review

Sets access rights for the project using the diff schema provided by
[ProjectAccessInput](#project-access-input)

This takes the same input as [Update Access Rights](#set-access), but
creates a pending change for review. Like [Create
Change](#create-change), it returns a [ChangeInfo](#change-info) entity
describing the resulting change.

**Request.**

``` 
  PUT /projects/MyProject/access:review HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "add":{
      "refs/heads/*":{
        "permissions":{
          "read":{
            "rules":{
              "global:Anonymous-Users": {
                "action":"DENY",
                "force":false
              }
            }
          }
        }
      }
    }
  }
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "testproj~refs%2Fmeta%2Fconfig~Ieaf185bf90a1fc3b58461e399385e158a20b31a2",
    "project": "testproj",
    "branch": "refs/meta/config",
    "hashtags": [],
    "change_id": "Ieaf185bf90a1fc3b58461e399385e158a20b31a2",
    "subject": "Review access change",
    "status": "NEW",
    "created": "2017-09-07 14:31:11.852000000",
    "updated": "2017-09-07 14:31:11.852000000",
    "submit_type": "CHERRY_PICK",
    "mergeable": true,
    "insertions": 2,
    "deletions": 0,
    "unresolved_comment_count": 0,
    "has_review_started": true,
    "_number": 7,
    "owner": {
      "_account_id": 1000000
    }
  }
```

### Check Access

*POST /projects/MyProject/check.access*

Runs access checks for other users. This requires the [Administrate
Server](access-control.html#capability_administrateServer) global
capability.

Input for the access checks that should be run must be provided in the
request body inside a [AccessCheckInput](#access-check-input) entity.

**Request.**

``` 
  POST /projects/MyProject/check.access HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "account": "Kristen.Burns@gerritcodereview.com",
    "ref": "refs/heads/secret/bla"
  }
```

The result is a [AccessCheckInfo](#access-check-info) entity detailing
the read access of the given user for the given project (or project-ref
combination).

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "message": "user Kristen Burns \u003cKristen.Burns@gerritcodereview.com\u003e (1000098) cannot see ref refs/heads/secret/bla in project MyProject",
    "status": 403
  }
```

### Index all changes in a project

Adds or updates all the changes belonging to a project in the secondary
index. The indexing task is executed asynchronously in background, so
this command returns immediately.

**Request.**

``` 
  POST /projects/MyProject/index HTTP/1.0
```

**Response.**

    HTTP/1.1 202 Accepted
    Content-Disposition: attachment

## Branch Endpoints

### List Branches

*GET /projects/[{project-name}](#project-name)/branches/*

List the branches of a project.

As result a list of [BranchInfo](#branch-info) entries is returned.

**Request.**

``` 
  GET /projects/work%2Fmy-project/branches/ HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "ref": "HEAD",
      "revision": "master"
    },
    {
      "ref": "refs/meta/config",
      "revision": "76016386a0d8ecc7b6be212424978bb45959d668"
    },
    {
      "ref": "refs/heads/master",
      "revision": "67ebf73496383c6777035e374d2d664009e2aa5c"
    },
    {
      "ref": "refs/heads/stable",
      "revision": "64ca533bd0eb5252d2fee83f63da67caae9b4674",
      "can_delete": true
    }
  ]
```

#### Branch Options

  - Limit(n)  
    Limit the number of branches to be included in the results.
    
    **Request.**
    
    ``` 
      GET /projects/testproject/branches?n=1 HTTP/1.0
    ```
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Disposition: attachment
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      [
        {
          "ref": "HEAD",
          "revision": "master",
        }
      ]
    ```

  - Skip(S)  
    Skip the given number of branches from the beginning of the list.
    
    **Request.**
    
    ``` 
      GET /projects/testproject/branches?n=1&s=0 HTTP/1.0
    ```
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Disposition: attachment
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      [
        {
          "ref": "HEAD",
          "revision": "master",
        }
      ]
    ```

  - Substring(m)  
    Limit the results to those branches that match the specified
    substring.
    
    The match is case insensitive. May not be used together with `r`.
    
    List all branches that match substring `test`:
    
    **Request.**
    
    ``` 
      GET /projects/testproject/branches?m=test HTTP/1.0
    ```
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Disposition: attachment
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      [
        {
          "ref": "refs/heads/test1",
          "revision": "9c9d08a438e55e52f33b608415e6dddd9b18550d",
          "can_delete": true
        }
      ]
    ```

  - Regex(r)  
    Limit the results to those branches that match the specified regex.
    Boundary matchers *^* and *$* are implicit. For example: the regex
    *t\** will match any branches that start with *t* and regex *\*t*
    will match any branches that end with *t*.
    
    The match is case sensitive. May not be used together with `m`.
    
    List all branches that match regex `t.*1`:
    
    **Request.**
    
    ``` 
      GET /projects/testproject/branches?r=t.*1 HTTP/1.0
    ```
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Disposition: attachment
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      [
        {
          "ref": "refs/heads/test1",
          "revision": "9c9d08a438e55e52f33b608415e6dddd9b18550d",
          "can_delete": true
        }
      ]
    ```

### Get Branch

*GET
/projects/[{project-name}](#project-name)/branches/[{branch-id}](#branch-id)*

Retrieves a branch of a project.

**Request.**

``` 
  GET /projects/work%2Fmy-project/branches/master HTTP/1.0
```

As response a [BranchInfo](#branch-info) entity is returned that
describes the branch.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "ref": "refs/heads/master",
    "revision": "67ebf73496383c6777035e374d2d664009e2aa5c"
  }
```

### Create Branch

*PUT
/projects/[{project-name}](#project-name)/branches/[{branch-id}](#branch-id)*

Creates a new branch.

In the request body additional data for the branch can be provided as
[BranchInput](#branch-input).

**Request.**

``` 
  PUT /projects/MyProject/branches/stable HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "revision": "76016386a0d8ecc7b6be212424978bb45959d668"
  }
```

As response a [BranchInfo](#branch-info) entity is returned that
describes the created branch.

**Response.**

``` 
  HTTP/1.1 201 Created
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "ref": "refs/heads/stable",
    "revision": "76016386a0d8ecc7b6be212424978bb45959d668",
    "can_delete": true
  }
```

### Delete Branch

*DELETE
/projects/[{project-name}](#project-name)/branches/[{branch-id}](#branch-id)*

Deletes a branch.

**Request.**

``` 
  DELETE /projects/MyProject/branches/stable HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

### Delete Branches

*POST /projects/[{project-name}](#project-name)/branches:delete*

Delete one or more branches.

The branches to be deleted must be provided in the request body as a
[DeleteBranchesInput](#delete-branches-input) entity.

**Request.**

``` 
  POST /projects/MyProject/branches:delete HTTP/1.0
  Content-Type: application/json;charset=UTF-8

  {
    "branches": [
      "stable-1.0",
      "stable-2.0"
    ]
  }
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

If some branches could not be deleted, the response is "`409 Conflict`"
and the error message is contained in the response body.

### Get Content

*GET
/projects/[{project-name}](#project-name)/branches/[{branch-id}](#branch-id)/files/[{file-id}](rest-api-changes.html#file-id)/content*

Gets the content of a file from the HEAD revision of a certain
branch.

**Request.**

``` 
  GET /projects/gerrit/branches/master/files/gerrit-server%2Fsrc%2Fmain%2Fjava%2Fcom%2Fgoogle%2Fgerrit%2Fserver%2Fproject%2FRefControl.java/content HTTP/1.0
```

The content is returned as base64 encoded string.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: text/plain; charset=UTF-8

  Ly8gQ29weXJpZ2h0IChDKSAyMDEwIFRoZSBBbmRyb2lkIE9wZW4gU291cmNlIFByb2plY...
```

### Get Mergeable Information

*GET
/projects/[{project-name}](#project-name)/branches/[{branch-id}](#branch-id)/mergeable*

Gets whether the source is mergeable with the target branch.

The `source` query parameter is required, which can be anything that
could be resolved to a commit, see examples of the `source` attribute in
[MergeInput](rest-api-changes.html#merge-input).

Also takes an optional parameter `strategy`, which can be `recursive`,
`resolve`, `simple-two-way-in-core`, `ours` or `theirs`, default will
use project
settings.

**Request.**

``` 
  GET /projects/test/branches/master/mergeable?source=testbranch&strategy=recursive HTTP/1.0
```

As response a [MergeableInfo](rest-api-changes.html#mergeable-info)
entity is returned.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "submit_type": "MERGE_IF_NECESSARY",
    "strategy": "recursive",
    "mergeable": true,
    "commit_merged": false,
    "content_merged": false
  }
```

or when there were conflicts.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "submit_type": "MERGE_IF_NECESSARY",
    "strategy": "recursive",
    "mergeable": false,
    "conflicts": [
      "common.txt",
      "shared.txt"
    ]
  }
```

or when the *testbranch* has been already merged.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "submit_type": "MERGE_IF_NECESSARY",
    "strategy": "recursive",
    "mergeable": true,
    "commit_merged": true,
    "content_merged": true
  }
```

or when only the content of *testbranch* has been merged.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "submit_type": "MERGE_IF_NECESSARY",
    "strategy": "recursive",
    "mergeable": true,
    "commit_merged": false,
    "content_merged": true
  }
```

### Get Reflog

*GET
/projects/[{project-name}](#project-name)/branches/[{branch-id}](#branch-id)/reflog*

Gets the reflog of a certain branch.

The caller must be project owner.

**Request.**

``` 
  GET /projects/gerrit/branches/master/reflog HTTP/1.0
```

As response a list of [ReflogEntryInfo](#reflog-entry-info) entities is
returned that describe the reflog entries. The reflog entries are
returned in reverse order.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "old_id": "976ced8f4fc0909d7e1584d18455299545881d60",
      "new_id": "2eaa94bac536654eb592c941e33b91f925698d16",
      "who": {
        "name": "Jane Roe",
        "email": "jane.roe@example.com",
        "date": "2014-06-30 11:53:43.000000000",
        "tz": 120
      },
      "comment": "merged: fast forward"
    },
    {
      "old_id": "c271c6a7161b74f85560c5899c8c73ee89ca5e29",
      "new_id": "976ced8f4fc0909d7e1584d18455299545881d60",
      "who": {
        "name": "John Doe",
        "email": "john.doe@example.com",
        "date": "2013-10-02 10:45:26.000000000",
        "tz": 120
      },
      "comment": "merged: fast forward"
    },
    {
      "old_id": "0000000000000000000000000000000000000000",
      "new_id": "c271c6a7161b74f85560c5899c8c73ee89ca5e29",
      "who": {
        "name": "John Doe",
        "email": "john.doe@example.com",
        "date": "2013-09-30 19:08:44.000000000",
        "tz": 120
      },
      "comment": ""
    }
  ]
```

The get reflog endpoint also accepts a limit integer in the `n`
parameter. This limits the results to show the last `n` reflog entries.

Query the last 25 reflog entries.

``` 
  GET /projects/gerrit/branches/master/reflog?n=25 HTTP/1.0
```

The reflog can also be filtered by timestamp by specifying the `from`
and `to` parameters. The timestamp for `from` and `to` must be given as
UTC in the following format:
`yyyyMMdd_HHmm`.

``` 
  GET /projects/gerrit/branches/master/reflog?from=20130101_0000&to=20140101_0000=25 HTTP/1.0
```

## Child Project Endpoints

### List Child Projects

*GET /projects/[{project-name}](#project-name)/children/*

List the direct child projects of a project.

**Request.**

``` 
  GET /projects/Public-Plugins/children/ HTTP/1.0
```

As result a list of [ProjectInfo](#project-info) entries is returned
that describe the child projects.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "id": "plugins%2Freplication",
      "name": "plugins/replication",
      "parent": "Public-Plugins",
      "description": "Copies to other servers using the Git protocol"
    },
    {
      "id": "plugins%2Freviewnotes",
      "name": "plugins/reviewnotes",
      "parent": "Public-Plugins",
      "description": "Annotates merged commits using notes on refs/notes/review."
    },
    {
      "id": "plugins%2Fsingleusergroup",
      "name": "plugins/singleusergroup",
      "parent": "Public-Plugins",
      "description": "GroupBackend enabling users to be directly added to access rules"
    }
  ]
```

To resolve the child projects of a project recursively the parameter
`recursive` can be set.

Child projects that are not visible to the calling user are ignored and
are not resolved further.

**Request.**

``` 
  GET /projects/Public-Projects/children/?recursive HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "id": "gerrit",
      "name": "gerrit",
      "parent": "Public-Projects",
      "description": "Gerrit Code Review"
    },
    {
      "id": "plugins%2Freplication",
      "name": "plugins/replication",
      "parent": "Public-Plugins",
      "description": "Copies to other servers using the Git protocol"
    },
    {
      "id": "plugins%2Freviewnotes",
      "name": "plugins/reviewnotes",
      "parent": "Public-Plugins",
      "description": "Annotates merged commits using notes on refs/notes/review."
    },
    {
      "id": "plugins%2Fsingleusergroup",
      "name": "plugins/singleusergroup",
      "parent": "Public-Plugins",
      "description": "GroupBackend enabling users to be directly added to access rules"
    },
    {
      "id": "Public-Plugins",
      "name": "Public-Plugins",
      "parent": "Public-Projects",
      "description": "Parent project for plugins/*"
    }
  ]
```

### Get Child Project

*GET
/projects/[{project-name}](#project-name)/children/[{project-name}](#project-name)*

Retrieves a child project. If a non-direct child project should be
retrieved the parameter `recursive` must be set.

**Request.**

``` 
  GET /projects/Public-Plugins/children/plugins%2Freplication HTTP/1.0
```

As response a [ProjectInfo](#project-info) entity is returned that
describes the child project.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "plugins%2Freplication",
    "name": "plugins/replication",
    "parent": "Public-Plugins",
    "description": "Copies to other servers using the Git protocol"
  }
```

## Tag Endpoints

### Create Tag

*PUT /projects/[{project-name}](#project-name)/tags/[{tag-id}](#tag-id)*

Create a new tag on the project.

In the request body additional data for the tag can be provided as
[TagInput](#tag-input).

If a message is provided in the input, the tag is created as an
annotated tag with the current user as tagger. Signed tags are not
supported.

**Request.**

``` 
  PUT /projects/MyProject/tags/v1.0 HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "message": "annotation",
    "revision": "c83117624b5b5d8a7f86093824e2f9c1ed309d63"
  }
```

As response a [TagInfo](#tag-info) entity is returned that describes the
created tag.

**Response.**

``` 
  HTTP/1.1 201 Created
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'

  "object": "d48d304adc4b6674e11dc2c018ea05fcbdda32fd",
  "message": "annotation",
  "tagger": {
    "name": "David Pursehouse",
    "email": "dpursehouse@collab.net",
    "date": "2016-06-06 01:22:03.000000000",
    "tz": 540
  },
  "ref": "refs/tags/v1.0",
  "revision": "c83117624b5b5d8a7f86093824e2f9c1ed309d63"
  }
```

### List Tags

*GET /projects/[{project-name}](#project-name)/tags/*

List the tags of a project.

As result a list of [TagInfo](#tag-info) entries is returned.

Only includes tags under the `refs/tags/` namespace.

**Request.**

``` 
  GET /projects/work%2Fmy-project/tags/ HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "ref": "refs/tags/v1.0",
      "revision": "49ce77fdcfd3398dc0dedbe016d1a425fd52d666",
      "object": "1624f5af8ae89148d1a3730df8c290413e3dcf30",
      "message": "Annotated tag",
      "tagger": {
        "name": "David Pursehouse",
        "email": "david.pursehouse@sonymobile.com",
        "date": "2014-10-06 07:35:03.000000000",
        "tz": 540
      }
    },
    {
      "ref": "refs/tags/v2.0",
      "revision": "1624f5af8ae89148d1a3730df8c290413e3dcf30"
    },
    {
      "ref": "refs/tags/v3.0",
      "revision": "c628685b3c5a3614571ecb5c8fceb85db9112313",
      "object": "1624f5af8ae89148d1a3730df8c290413e3dcf30",
      "message": "Signed tag\n-----BEGIN PGP SIGNATURE-----\nVersion: GnuPG v1.4.11 (GNU/Linux)\n\niQEcBAABAgAGBQJUMlqYAAoJEPI2qVPgglptp7MH/j+KFcittFbxfSnZjUl8n5IZ\nveZo7wE+syjD9sUbMH4EGv0WYeVjphNTyViBof+stGTNkB0VQzLWI8+uWmOeiJ4a\nzj0LsbDOxodOEMI5tifo02L7r4Lzj++EbqtKv8IUq2kzYoQ2xjhKfFiGjeYOn008\n9PGnhNblEHZgCHguGR6GsfN8bfA2XNl9B5Ysl5ybX1kAVs/TuLZR4oDMZ/pW2S75\nhuyNnSgcgq7vl2gLGefuPs9lxkg5Fj3GZr7XPZk4pt/x1oiH7yXxV4UzrUwg2CC1\nfHrBlNbQ4uJNY8TFcwof52Z0cagM5Qb/ZSLglHbqEDGA68HPqbwf5z2hQyU2/U4\u003d\n\u003dZtUX\n-----END PGP SIGNATURE-----",
      "tagger": {
        "name": "David Pursehouse",
        "email": "david.pursehouse@sonymobile.com",
        "date": "2014-10-06 09:02:16.000000000",
        "tz": 540
      }
    }
  ]
```

#### Tag Options

  - Limit(n)  
    Limit the number of tags to be included in the results.
    
    **Request.**
    
    ``` 
      GET /projects/work%2Fmy-project/tags?n=2 HTTP/1.0
    ```
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Disposition: attachment
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      [
        {
          "ref": "refs/tags/v1.0",
          "revision": "49ce77fdcfd3398dc0dedbe016d1a425fd52d666",
          "object": "1624f5af8ae89148d1a3730df8c290413e3dcf30",
          "message": "Annotated tag",
          "tagger": {
            "name": "David Pursehouse",
            "email": "david.pursehouse@sonymobile.com",
            "date": "2014-10-06 07:35:03.000000000",
            "tz": 540
          }
        },
        {
          "ref": "refs/tags/v2.0",
          "revision": "1624f5af8ae89148d1a3730df8c290413e3dcf30"
        }
      ]
    ```

  - Skip(S)  
    Skip the given number of tags from the beginning of the list.
    
    **Request.**
    
    ``` 
      GET /projects/work%2Fmy-project/tags?n=2&s=1 HTTP/1.0
    ```
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Disposition: attachment
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      [
        {
          "ref": "refs/tags/v2.0",
          "revision": "1624f5af8ae89148d1a3730df8c290413e3dcf30"
        },
        {
          "ref": "refs/tags/v3.0",
          "revision": "c628685b3c5a3614571ecb5c8fceb85db9112313",
          "object": "1624f5af8ae89148d1a3730df8c290413e3dcf30",
          "message": "Signed tag\n-----BEGIN PGP SIGNATURE-----\nVersion: GnuPG v1.4.11 (GNU/Linux)\n\niQEcBAABAgAGBQJUMlqYAAoJEPI2qVPgglptp7MH/j+KFcittFbxfSnZjUl8n5IZ\nveZo7wE+syjD9sUbMH4EGv0WYeVjphNTyViBof+stGTNkB0VQzLWI8+uWmOeiJ4a\nzj0LsbDOxodOEMI5tifo02L7r4Lzj++EbqtKv8IUq2kzYoQ2xjhKfFiGjeYOn008\n9PGnhNblEHZgCHguGR6GsfN8bfA2XNl9B5Ysl5ybX1kAVs/TuLZR4oDMZ/pW2S75\nhuyNnSgcgq7vl2gLGefuPs9lxkg5Fj3GZr7XPZk4pt/x1oiH7yXxV4UzrUwg2CC1\nfHrBlNbQ4uJNY8TFcwof52Z0cagM5Qb/ZSLglHbqEDGA68HPqbwf5z2hQyU2/U4\u003d\n\u003dZtUX\n-----END PGP SIGNATURE-----",
          "tagger": {
            "name": "David Pursehouse",
            "email": "david.pursehouse@sonymobile.com",
            "date": "2014-10-06 09:02:16.000000000",
            "tz": 540
          }
        }
      ]
    ```

  - Substring(m)  
    Limit the results to those tags that match the specified substring.
    
    The match is case insensitive. May not be used together with `r`.
    
    List all tags that match substring `v2`:

\+ .Request

``` 
  GET /projects/testproject/tags?m=v2 HTTP/1.0
```

\+ .Response

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "ref": "refs/tags/v2.0",
      "revision": "1624f5af8ae89148d1a3730df8c290413e3dcf30"
    },
  ]
```

  - Regex(r)  
    Limit the results to those tags that match the specified regex.
    Boundary matchers *^* and *$* are implicit. For example: the regex
    *t\** will match any tags that start with *t* and regex *\*t* will
    match any tags that end with *t*.
    
    The match is case sensitive. May not be used together with `m`.
    
    List all tags that match regex `v.*0`:
    
    **Request.**
    
    ``` 
      GET /projects/testproject/tags?r=v.*0 HTTP/1.0
    ```
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Disposition: attachment
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      [
        {
          "ref": "refs/tags/v1.0",
          "revision": "49ce77fdcfd3398dc0dedbe016d1a425fd52d666",
          "object": "1624f5af8ae89148d1a3730df8c290413e3dcf30",
          "message": "Annotated tag",
          "tagger": {
            "name": "David Pursehouse",
            "email": "david.pursehouse@sonymobile.com",
            "date": "2014-10-06 07:35:03.000000000",
            "tz": 540
          }
        },
        {
          "ref": "refs/tags/v2.0",
          "revision": "1624f5af8ae89148d1a3730df8c290413e3dcf30"
        },
        {
          "ref": "refs/tags/v3.0",
          "revision": "c628685b3c5a3614571ecb5c8fceb85db9112313",
          "object": "1624f5af8ae89148d1a3730df8c290413e3dcf30",
          "message": "Signed tag\n-----BEGIN PGP SIGNATURE-----\nVersion: GnuPG v1.4.11 (GNU/Linux)\n\niQEcBAABAgAGBQJUMlqYAAoJEPI2qVPgglptp7MH/j+KFcittFbxfSnZjUl8n5IZ\nveZo7wE+syjD9sUbMH4EGv0WYeVjphNTyViBof+stGTNkB0VQzLWI8+uWmOeiJ4a\nzj0LsbDOxodOEMI5tifo02L7r4Lzj++EbqtKv8IUq2kzYoQ2xjhKfFiGjeYOn008\n9PGnhNblEHZgCHguGR6GsfN8bfA2XNl9B5Ysl5ybX1kAVs/TuLZR4oDMZ/pW2S75\nhuyNnSgcgq7vl2gLGefuPs9lxkg5Fj3GZr7XPZk4pt/x1oiH7yXxV4UzrUwg2CC1\nfHrBlNbQ4uJNY8TFcwof52Z0cagM5Qb/ZSLglHbqEDGA68HPqbwf5z2hQyU2/U4\u003d\n\u003dZtUX\n-----END PGP SIGNATURE-----",
          "tagger": {
            "name": "David Pursehouse",
            "email": "david.pursehouse@sonymobile.com",
            "date": "2014-10-06 09:02:16.000000000",
            "tz": 540
          }
        }
      ]
    ```

### Get Tag

*GET /projects/[{project-name}](#project-name)/tags/[{tag-id}](#tag-id)*

Retrieves a tag of a project.

**Request.**

``` 
  GET /projects/work%2Fmy-project/tags/v1.0 HTTP/1.0
```

As response a [TagInfo](#tag-info) entity is returned that describes the
tag.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "ref": "refs/tags/v1.0",
    "revision": "49ce77fdcfd3398dc0dedbe016d1a425fd52d666",
    "object": "1624f5af8ae89148d1a3730df8c290413e3dcf30",
    "message": "Annotated tag",
    "tagger": {
      "name": "David Pursehouse",
      "email": "david.pursehouse@sonymobile.com",
      "date": "2014-10-06 07:35:03.000000000",
      "tz": 540
    }
  }
```

### Delete Tag

*DELETE
/projects/[{project-name}](#project-name)/tags/[{tag-id}](#tag-id)*

Deletes a tag.

**Request.**

``` 
  DELETE /projects/MyProject/tags/v1.0 HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

### Delete Tags

*POST /projects/[{project-name}](#project-name)/tags:delete*

Delete one or more tags.

The tags to be deleted must be provided in the request body as a
[DeleteTagsInput](#delete-tags-input) entity.

**Request.**

``` 
  POST /projects/MyProject/tags:delete HTTP/1.0
  Content-Type: application/json;charset=UTF-8

  {
    "tags": [
      "v1.0",
      "v2.0"
    ]
  }
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

If some tags could not be deleted, the response is "`409 Conflict`" and
the error message is contained in the response body.

## Commit Endpoints

### Get Commit

*GET
/projects/[{project-name}](#project-name)/commits/[{commit-id}](#commit-id)*

Retrieves a commit of a project.

The commit must be visible to the
caller.

**Request.**

``` 
  GET /projects/work%2Fmy-project/commits/a8a477efffbbf3b44169bb9a1d3a334cbbd9aa96 HTTP/1.0
```

As response a [CommitInfo](rest-api-changes.html#commit-info) entity is
returned that describes the commit.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "commit": "184ebe53805e102605d11f6b143486d15c23a09c",
    "parents": [
      {
        "commit": "1eee2c9d8f352483781e772f35dc586a69ff5646",
        "subject": "Migrate contributor agreements to All-Projects."
      }
    ],
    "author": {
      "name": "Shawn O. Pearce",
      "email": "sop@google.com",
      "date": "2012-04-24 18:08:08.000000000",
      "tz": -420
    },
    "committer": {
      "name": "Shawn O. Pearce",
      "email": "sop@google.com",
      "date": "2012-04-24 18:08:08.000000000",
      "tz": -420
    },
    "subject": "Use an EventBus to manage star icons",
    "message": "Use an EventBus to manage star icons\n\nImage widgets that need to ..."
  }
```

### Get Included In

*GET
/projects/[{project-name}](#project-name)/commits/[{commit-id}](#commit-id)/in*

Retrieves the branches and tags in which a change is included. As result
an [IncludedInInfo](rest-api-changes.html#included-in-info) entity is
returned.

**Request.**

``` 
  GET /projects/work%2Fmy-project/commits/a8a477efffbbf3b44169bb9a1d3a334cbbd9aa96/in HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json;charset=UTF-8

  )]}'
  {
    "branches": [
      "master"
    ],
    "tags": []
  }
```

### Get Content

*GET
/projects/[{project-name}](#project-name)/commits/[{commit-id}](#commit-id)/files/[{file-id}](rest-api-changes.html#file-id)/content*

Gets the content of a file from a certain
commit.

**Request.**

``` 
  GET /projects/work%2Fmy-project/commits/a8a477efffbbf3b44169bb9a1d3a334cbbd9aa96/files/gerrit-server%2Fsrc%2Fmain%2Fjava%2Fcom%2Fgoogle%2Fgerrit%2Fserver%2Fproject%2FRefControl.java/content HTTP/1.0
```

The content is returned as base64 encoded string.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: text/plain; charset=UTF-8

  Ly8gQ29weXJpZ2h0IChDKSAyMDEwIFRoZSBBbmRyb2lkIE9wZW4gU291cmNlIFByb2plY...
```

### Cherry Pick Commit

*POST
/projects/[{project-name}](#project-name)/commits/[{commit-id}](#commit-id)/cherrypick*

Cherry-picks a commit of a project to a destination branch.

To cherry pick a change revision, see
[CherryPick](rest-api-changes.html#cherry-pick).

The destination branch must be provided in the request body inside a
[CherryPickInput](rest-api-changes.html#cherrypick-input) entity. If the
commit message is not set, the commit message of the source commit will
be
used.

**Request.**

``` 
  POST /projects/work%2Fmy-project/commits/a8a477efffbbf3b44169bb9a1d3a334cbbd9aa96/cherrypick HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "message" : "Implementing Feature X",
    "destination" : "release-branch"
  }
```

As response a [ChangeInfo](rest-api-changes.html#change-info) entity is
returned that describes the resulting cherry-picked change.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9941",
    "project": "myProject",
    "branch": "release-branch",
    "change_id": "I8473b95934b5732ac55d26311a706c9c2bde9941",
    "subject": "Implementing Feature X",
    "status": "NEW",
    "created": "2013-02-01 09:59:32.126000000",
    "updated": "2013-02-21 11:16:36.775000000",
    "mergeable": true,
    "insertions": 12,
    "deletions": 11,
    "_number": 3965,
    "owner": {
      "name": "John Doe"
    }
  }
```

## Dashboard Endpoints

### List Dashboards

*GET /projects/[{project-name}](#project-name)/dashboards/*

List custom dashboards for a project.

As result a list of [DashboardInfo](#dashboard-info) entries is
returned.

List all dashboards for the `work/my-project` project:

**Request.**

``` 
  GET /projects/work%2Fmy-project/dashboards/ HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "id": "main:closed",
      "ref": "main",
      "path": "closed",
      "description": "Merged and abandoned changes in last 7 weeks",
      "url": "/dashboard/?title\u003dClosed+changes\u0026Merged\u003dstatus:merged+age:7w\u0026Abandoned\u003dstatus:abandoned+age:7w",
      "is_default": true,
      "title": "Closed changes",
      "sections": [
        {
          "name": "Merged",
          "query": "status:merged age:7w"
        },
        {
          "name": "Abandoned",
          "query": "status:abandoned age:7w"
        }
      ]
    }
  ]
```

get::/projects/All-Projects/dashboards/

### Get Dashboard

*GET
/projects/[{project-name}](#project-name)/dashboards/[{dashboard-id}](#dashboard-id)*

Retrieves a project dashboard. The dashboard can be defined on that
project or be inherited from a parent project.

**Request.**

``` 
  GET /projects/work%2Fmy-project/dashboards/main:closed HTTP/1.0
```

As response a [DashboardInfo](#dashboard-info) entity is returned that
describes the dashboard.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "main:closed",
    "ref": "main",
    "path": "closed",
    "description": "Merged and abandoned changes in last 7 weeks",
    "url": "/dashboard/?title\u003dClosed+changes\u0026Merged\u003dstatus:merged+age:7w\u0026Abandoned\u003dstatus:abandoned+age:7w",
    "is_default": true,
    "title": "Closed changes",
    "sections": [
      {
        "name": "Merged",
        "query": "status:merged age:7w"
      },
      {
        "name": "Abandoned",
        "query": "status:abandoned age:7w"
      }
    ]
  }
```

To retrieve the default dashboard of a project use `default` as
dashboard-id.

**Request.**

``` 
  GET /projects/work%2Fmy-project/dashboards/default HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "main:closed",
    "ref": "main",
    "path": "closed",
    "description": "Merged and abandoned changes in last 7 weeks",
    "url": "/dashboard/?title\u003dClosed+changes\u0026Merged\u003dstatus:merged+age:7w\u0026Abandoned\u003dstatus:abandoned+age:7w",
    "is_default": true,
    "title": "Closed changes",
    "sections": [
      {
        "name": "Merged",
        "query": "status:merged age:7w"
      },
      {
        "name": "Abandoned",
        "query": "status:abandoned age:7w"
      }
    ]
  }
```

### Set Dashboard

*PUT
/projects/[{project-name}](#project-name)/dashboards/[{dashboard-id}](#dashboard-id)*

Updates/Creates a project dashboard.

Currently only supported for the `default` dashboard.

The creation/update information for the dashboard must be provided in
the request body as a [DashboardInput](#dashboard-input) entity.

**Request.**

``` 
  PUT /projects/work%2Fmy-project/dashboards/default HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "id": "main:closed",
    "commit_message": "Define the default dashboard"
  }
```

As response the new/updated dashboard is returned as a
[DashboardInfo](#dashboard-info) entity.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "main:closed",
    "ref": "main",
    "path": "closed",
    "description": "Merged and abandoned changes in last 7 weeks",
    "url": "/dashboard/?title\u003dClosed+changes\u0026Merged\u003dstatus:merged+age:7w\u0026Abandoned\u003dstatus:abandoned+age:7w",
    "is_default": true,
    "title": "Closed changes",
    "sections": [
      {
        "name": "Merged",
        "query": "status:merged age:7w"
      },
      {
        "name": "Abandoned",
        "query": "status:abandoned age:7w"
      }
    ]
  }
```

### Delete Dashboard

*DELETE
/projects/[{project-name}](#project-name)/dashboards/[{dashboard-id}](#dashboard-id)*

Deletes a project dashboard.

Currently only supported for the `default` dashboard.

The request body does not need to include a
[DashboardInput](#dashboard-input) entity if no commit message is
specified.

Please note that some proxies prohibit request bodies for DELETE
requests.

**Request.**

``` 
  DELETE /projects/work%2Fmy-project/dashboards/default HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

## IDs

### {branch-id}

The name of a branch or `HEAD`. The prefix `refs/heads/` can be omitted.

### {commit-id}

Commit ID.

### {tag-id}

The name of a tag. The prefix `refs/tags/` can be omitted.

### {dashboard-id}

The ID of a dashboard in the format *\<ref\>:\<path\>*.

A special dashboard ID is `default` which represents the default
dashboard of a project.

### {project-name}

The name of the project.

If the name ends with `.git`, the suffix will be automatically removed.

## JSON Entities

### AccessCheckInfo

The `AccessCheckInfo` entity is the result of an access check.

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
<td><p><code>status</code></p></td>
<td></td>
<td><p>The HTTP status code for the access. 200 means success, 403 means denied and 404 means the project does not exist.</p></td>
</tr>
<tr class="even">
<td><p><code>message</code></p></td>
<td><p>optional</p></td>
<td><p>A clarifying message if <code>status</code> is not 200.</p></td>
</tr>
</tbody>
</table>

### AccessCheckInput

The `AccessCheckInput` entity is either an account or (account, ref)
tuple for which we want to check access.

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
<td><p><code>account</code></p></td>
<td></td>
<td><p>The account for which to check access</p></td>
</tr>
<tr class="even">
<td><p><code>ref</code></p></td>
<td><p>optional</p></td>
<td><p>The refname for which to check access</p></td>
</tr>
</tbody>
</table>

### BanInput

The `BanInput` entity contains information for banning commits in a
project.

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
<td><p><code>commits</code></p></td>
<td></td>
<td><p>List of commits to be banned.</p></td>
</tr>
<tr class="even">
<td><p><code>reason</code></p></td>
<td><p>optional</p></td>
<td><p>Reason for banning the commits.</p></td>
</tr>
</tbody>
</table>

### BanResultInfo

The `BanResultInfo` entity describes the result of banning commits.

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
<td><p><code>newly_banned</code></p></td>
<td><p>optional</p></td>
<td><p>List of newly banned commits.</p></td>
</tr>
<tr class="even">
<td><p><code>already_banned</code></p></td>
<td><p>optional</p></td>
<td><p>List of commits that were already banned.</p></td>
</tr>
<tr class="odd">
<td><p><code>ignored</code></p></td>
<td><p>optional</p></td>
<td><p>List of object IDs that were ignored.</p></td>
</tr>
</tbody>
</table>

### BranchInfo

The `BranchInfo` entity contains information about a branch.

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
<td><p><code>ref</code></p></td>
<td></td>
<td><p>The ref of the branch.</p></td>
</tr>
<tr class="even">
<td><p><code>revision</code></p></td>
<td></td>
<td><p>The revision to which the branch points.</p></td>
</tr>
<tr class="odd">
<td><p><code>can_delete</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the calling user can delete this branch.</p></td>
</tr>
<tr class="even">
<td><p><code>web_links</code></p></td>
<td><p>optional</p></td>
<td><p>Links to the branch in external sites as a list of <a href="rest-api-changes.html#web-link-info">WebLinkInfo</a> entries.</p></td>
</tr>
</tbody>
</table>

### BranchInput

The `BranchInput` entity contains information for the creation of a new
branch.

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
<td><p><code>ref</code></p></td>
<td><p>optional</p></td>
<td><p>The name of the branch. The prefix <code>refs/heads/</code> can be omitted.<br />
If set, must match the branch ID in the URL.</p></td>
</tr>
<tr class="even">
<td><p><code>revision</code></p></td>
<td><p>optional</p></td>
<td><p>The base revision of the new branch.<br />
If not set, <code>HEAD</code> will be used as base revision.</p></td>
</tr>
</tbody>
</table>

### ConfigInfo

The `ConfigInfo` entity contains information about the effective project
configuration.

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
<td><p><code>description</code></p></td>
<td><p>optional</p></td>
<td><p>The description of the project.</p></td>
</tr>
<tr class="even">
<td><p><code>use_contributor_agreements</code></p></td>
<td><p>optional</p></td>
<td><p><a href="#inherited-boolean-info">InheritedBooleanInfo</a> that tells whether authors must complete a contributor agreement on the site before pushing any commits or changes to this project.</p></td>
</tr>
<tr class="odd">
<td><p><code>use_content_merge</code></p></td>
<td><p>optional</p></td>
<td><p><a href="#inherited-boolean-info">InheritedBooleanInfo</a> that tells whether Gerrit will try to perform a 3-way merge of text file content when a file has been modified by both the destination branch and the change being submitted. This option only takes effect if submit type is not FAST_FORWARD_ONLY.</p></td>
</tr>
<tr class="even">
<td><p><code>use_signed_off_by</code></p></td>
<td><p>optional</p></td>
<td><p><a href="#inherited-boolean-info">InheritedBooleanInfo</a> that tells whether each change must contain a Signed-off-by line from either the author or the uploader in the commit message.</p></td>
</tr>
<tr class="odd">
<td><p><code>create_new_change_for_all_not_in_target</code></p></td>
<td><p>optional</p></td>
<td><p><a href="#inherited-boolean-info">InheritedBooleanInfo</a> that tells whether a new change is created for every commit not in target branch.</p></td>
</tr>
<tr class="even">
<td><p><code>require_change_id</code></p></td>
<td><p>optional</p></td>
<td><p><a href="#inherited-boolean-info">InheritedBooleanInfo</a> that tells whether a valid <a href="user-changeid.html">Change-Id</a> footer in any commit uploaded for review is required. This does not apply to commits pushed directly to a branch or tag.</p></td>
</tr>
<tr class="odd">
<td><p><code>enable_signed_push</code></p></td>
<td><p>optional, not set if signed push is disabled</p></td>
<td><p><a href="#inherited-boolean-info">InheritedBooleanInfo</a> that tells whether signed push validation is enabled on the project.</p></td>
</tr>
<tr class="even">
<td><p><code>require_signed_push</code></p></td>
<td><p>optional, not set if signed push is disabled</p></td>
<td><p><a href="#inherited-boolean-info">InheritedBooleanInfo</a> that tells whether signed push validation is required on the project.</p></td>
</tr>
<tr class="odd">
<td><p><code>reject_implicit_merges</code></p></td>
<td><p>optional</p></td>
<td><p><a href="#inherited-boolean-info">InheritedBooleanInfo</a> that tells whether implicit merges should be rejected on changes pushed to the project.</p></td>
</tr>
<tr class="even">
<td><p><code>private_by_default</code></p></td>
<td></td>
<td><p><a href="#inherited-boolean-info">InheritedBooleanInfo</a> that tells whether all new changes are set as private by default.</p></td>
</tr>
<tr class="odd">
<td><p><code>max_object_size_limit</code></p></td>
<td></td>
<td><p>The <a href="config-gerrit.html#receive.maxObjectSizeLimit">max object size limit</a> of this project as a <a href="#max-object-size-limit-info">MaxObjectSizeLimitInfo</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>submit_type</code></p></td>
<td></td>
<td><p>The default submit type of the project, can be <code>MERGE_IF_NECESSARY</code>, <code>FAST_FORWARD_ONLY</code>, <code>REBASE_IF_NECESSARY</code>, <code>REBASE_ALWAYS</code>, <code>MERGE_ALWAYS</code> or <code>CHERRY_PICK</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>match_author_to_committer_date</code></p></td>
<td><p>optional</p></td>
<td><p><a href="#inherited-boolean-info">InheritedBooleanInfo</a> that indicates whether a change’s author date will be changed to match its submitter date upon submit.</p></td>
</tr>
<tr class="even">
<td><p><code>state</code></p></td>
<td><p>optional</p></td>
<td><p>The state of the project, can be <code>ACTIVE</code>, <code>READ_ONLY</code> or <code>HIDDEN</code>.<br />
Not set if the project state is <code>ACTIVE</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>commentlinks</code></p></td>
<td></td>
<td><p>Map with the comment link configurations of the project. The name of the comment link configuration is mapped to the comment link configuration, which has the same format as the <a href="config-gerrit.html#_a_id_commentlink_a_section_commentlink">commentlink section</a> of <code>gerrit.config</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>theme</code></p></td>
<td><p>optional</p></td>
<td><p>The theme that is configured for the project as a <a href="#theme-info">ThemeInfo</a> entity.</p></td>
</tr>
<tr class="odd">
<td><p><code>plugin_config</code></p></td>
<td><p>optional</p></td>
<td><p>Plugin configuration as map which maps the plugin name to a map of parameter names to <a href="#config-parameter-info">ConfigParameterInfo</a> entities.</p></td>
</tr>
<tr class="even">
<td><p><code>actions</code></p></td>
<td><p>optional</p></td>
<td><p>Actions the caller might be able to perform on this project. The information is a map of view names to <a href="rest-api-changes.html#action-info">ActionInfo</a> entities.</p></td>
</tr>
</tbody>
</table>

### ConfigInput

The `ConfigInput` entity describes a new project configuration.

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
<td><p><code>description</code></p></td>
<td><p>optional</p></td>
<td><p>The new description of the project.<br />
If not set, the description is removed.</p></td>
</tr>
<tr class="even">
<td><p><code>use_contributor_agreements</code></p></td>
<td><p>optional</p></td>
<td><p>Whether authors must complete a contributor agreement on the site before pushing any commits or changes to this project.<br />
Can be <code>TRUE</code>, <code>FALSE</code> or <code>INHERIT</code>.<br />
If not set, this setting is not updated.</p></td>
</tr>
<tr class="odd">
<td><p><code>use_content_merge</code></p></td>
<td><p>optional</p></td>
<td><p>Whether Gerrit will try to perform a 3-way merge of text file content when a file has been modified by both the destination branch and the change being submitted. This option only takes effect if submit type is not FAST_FORWARD_ONLY.<br />
Can be <code>TRUE</code>, <code>FALSE</code> or <code>INHERIT</code>.<br />
If not set, this setting is not updated.</p></td>
</tr>
<tr class="even">
<td><p><code>use_signed_off_by</code></p></td>
<td><p>optional</p></td>
<td><p>Whether each change must contain a Signed-off-by line from either the author or the uploader in the commit message.<br />
Can be <code>TRUE</code>, <code>FALSE</code> or <code>INHERIT</code>.<br />
If not set, this setting is not updated.</p></td>
</tr>
<tr class="odd">
<td><p><code>create_new_change_for_all_not_in_target</code></p></td>
<td><p>optional</p></td>
<td><p>Whether a new change will be created for every commit not in target branch.<br />
Can be <code>TRUE</code>, <code>FALSE</code> or <code>INHERIT</code>.<br />
If not set, this setting is not updated.</p></td>
</tr>
<tr class="even">
<td><p><code>require_change_id</code></p></td>
<td><p>optional</p></td>
<td><p>Whether a valid <a href="user-changeid.html">Change-Id</a> footer in any commit uploaded for review is required. This does not apply to commits pushed directly to a branch or tag.<br />
Can be <code>TRUE</code>, <code>FALSE</code> or <code>INHERIT</code>.<br />
If not set, this setting is not updated.</p></td>
</tr>
<tr class="odd">
<td><p><code>reject_implicit_merges</code></p></td>
<td><p>optional</p></td>
<td><p>Whether a check for implicit merges will be performed when changes are pushed for review.<br />
Can be <code>TRUE</code>, <code>FALSE</code> or <code>INHERIT</code>.<br />
If not set, this setting is not updated.</p></td>
</tr>
<tr class="even">
<td><p><code>max_object_size_limit</code></p></td>
<td><p>optional</p></td>
<td><p>The <a href="config-gerrit.html#receive.maxObjectSizeLimit">max object size limit</a> of this project as a <a href="#max-object-size-limit-info">MaxObjectSizeLimitInfo</a> entity.<br />
If set to <code>0</code>, the max object size limit is removed.<br />
If not set, this setting is not updated.</p></td>
</tr>
<tr class="odd">
<td><p><code>submit_type</code></p></td>
<td><p>optional</p></td>
<td><p>The default submit type of the project, can be <code>MERGE_IF_NECESSARY</code>, <code>FAST_FORWARD_ONLY</code>, <code>REBASE_IF_NECESSARY</code>, <code>REBASE_ALWAYS</code>, <code>MERGE_ALWAYS</code> or <code>CHERRY_PICK</code>.<br />
If not set, the submit type is not updated.</p></td>
</tr>
<tr class="even">
<td><p><code>state</code></p></td>
<td><p>optional</p></td>
<td><p>The state of the project, can be <code>ACTIVE</code>, <code>READ_ONLY</code> or <code>HIDDEN</code>.<br />
Not set if the project state is <code>ACTIVE</code>.<br />
If not set, the project state is not updated.</p></td>
</tr>
<tr class="odd">
<td><p><code>plugin_config_values</code></p></td>
<td><p>optional</p></td>
<td><p>Plugin configuration values as map which maps the plugin name to a map of parameter names to values.</p></td>
</tr>
</tbody>
</table>

### ConfigParameterInfo

The `ConfigParameterInfo` entity describes a project configuration
parameter.

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
<td><p><code>display_name</code></p></td>
<td><p>optional</p></td>
<td><p>The display name of the configuration parameter.</p></td>
</tr>
<tr class="even">
<td><p><code>description</code></p></td>
<td><p>optional</p></td>
<td><p>The description of the configuration parameter.</p></td>
</tr>
<tr class="odd">
<td><p><code>warning</code></p></td>
<td><p>optional</p></td>
<td><p>Warning message for the configuration parameter.</p></td>
</tr>
<tr class="even">
<td><p><code>type</code></p></td>
<td></td>
<td><p>The type of the configuration parameter. Can be <code>STRING</code>, <code>INT</code>, <code>LONG</code>, <code>BOOLEAN</code>, <code>LIST</code> or <code>ARRAY</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>value</code></p></td>
<td><p>optional</p></td>
<td><p>The value of the configuration parameter as string. If the parameter is inheritable this is the effective value which is deduced from <code>configured_value</code> and <code>inherited_value</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>values</code></p></td>
<td><p>optional</p></td>
<td><p>The list of values. Only set if the <code>type</code> is <code>ARRAY</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>editable</code></p></td>
<td><p><code>false</code> if not set</p></td>
<td><p>Whether the value is editable.</p></td>
</tr>
<tr class="even">
<td><p><code>permitted_values</code></p></td>
<td><p>optional</p></td>
<td><p>The list of permitted values. Only set if the <code>type</code> is <code>LIST</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>inheritable</code></p></td>
<td><p><code>false</code> if not set</p></td>
<td><p>Whether the configuration parameter can be inherited.</p></td>
</tr>
<tr class="even">
<td><p><code>configured_value</code></p></td>
<td><p>optional</p></td>
<td><p>The value of the configuration parameter that is configured on this project, only set if <code>inheritable</code> is true.</p></td>
</tr>
<tr class="odd">
<td><p><code>inherited_value</code></p></td>
<td><p>optional</p></td>
<td><p>The inherited value of the configuration parameter, only set if <code>inheritable</code> is true.</p></td>
</tr>
<tr class="even">
<td><p><code>permitted_values</code></p></td>
<td><p>optional</p></td>
<td><p>The list of permitted values, only set if the <code>type</code> is <code>LIST</code>.</p></td>
</tr>
</tbody>
</table>

### DashboardInfo

The `DashboardInfo` entity contains information about a project
dashboard.

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
<td><p>The ID of the dashboard. The ID has the format <em>&lt;ref&gt;:&lt;path&gt;</em>, where ref and path are URL encoded.</p></td>
</tr>
<tr class="even">
<td><p><code>project</code></p></td>
<td></td>
<td><p>The name of the project for which this dashboard is returned.</p></td>
</tr>
<tr class="odd">
<td><p><code>defining_project</code></p></td>
<td></td>
<td><p>The name of the project in which this dashboard is defined. This is different from <code>project</code> if the dashboard is inherited from a parent project.</p></td>
</tr>
<tr class="even">
<td><p><code>ref</code></p></td>
<td></td>
<td><p>The name of the ref in which the dashboard is defined, without the <code>refs/meta/dashboards/</code> prefix, which is common for all dashboard refs.</p></td>
</tr>
<tr class="odd">
<td><p><code>path</code></p></td>
<td></td>
<td><p>The path of the file in which the dashboard is defined.</p></td>
</tr>
<tr class="even">
<td><p><code>description</code></p></td>
<td><p>optional</p></td>
<td><p>The description of the dashboard.</p></td>
</tr>
<tr class="odd">
<td><p><code>foreach</code></p></td>
<td><p>optional</p></td>
<td><p>Subquery that applies to all sections in the dashboard.<br />
Tokens such as <code>${project}</code> are not resolved.</p></td>
</tr>
<tr class="even">
<td><p><code>url</code></p></td>
<td></td>
<td><p>The URL under which the dashboard can be opened in the Gerrit Web UI.<br />
The URL is relative to the canonical web URL.<br />
Tokens in the queries such as <code>${project}</code> are resolved.</p></td>
</tr>
<tr class="odd">
<td><p><code>is_default</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether this is the default dashboard of the project.</p></td>
</tr>
<tr class="even">
<td><p><code>title</code></p></td>
<td><p>optional</p></td>
<td><p>The title of the dashboard.</p></td>
</tr>
<tr class="odd">
<td><p><code>sections</code></p></td>
<td></td>
<td><p>The list of <a href="#dashboard-section-info">sections</a> in the dashboard.</p></td>
</tr>
</tbody>
</table>

### DashboardInput

The `DashboardInput` entity contains information to create/update a
project dashboard.

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
<td><p>optional</p></td>
<td><p>URL encoded ID of a dashboard to which this dashboard should link to.</p></td>
</tr>
<tr class="even">
<td><p><code>commit_message</code></p></td>
<td><p>optional</p></td>
<td><p>Message that should be used to commit the change of the dashboard.</p></td>
</tr>
</tbody>
</table>

### DashboardSectionInfo

The `DashboardSectionInfo` entity contains information about a section
in a dashboard.

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
<td><p><code>name</code></p></td>
<td><p>The title of the section.</p></td>
</tr>
<tr class="even">
<td><p><code>query</code></p></td>
<td><p>The query of the section.<br />
Tokens such as <code>${project}</code> are not resolved.</p></td>
</tr>
</tbody>
</table>

### DeleteBranchesInput

The `DeleteBranchesInput` entity contains information about branches
that should be deleted.

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
<td><p><code>branches</code></p></td>
<td><p>A list of branch names that identify the branches that should be deleted.</p></td>
</tr>
</tbody>
</table>

### DeleteTagsInput

The `DeleteTagsInput` entity contains information about tags that should
be deleted.

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
<td><p><code>tags</code></p></td>
<td><p>A list of tag names that identify the tags that should be deleted.</p></td>
</tr>
</tbody>
</table>

### GCInput

The `GCInput` entity contains information to run the Git garbage
collection.

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
<td><p><code>show_progress</code></p></td>
<td><p><code>false</code> if not set</p></td>
<td><p>Whether progress information should be shown.</p></td>
</tr>
<tr class="even">
<td><p><code>aggressive</code></p></td>
<td><p><code>false</code> if not set</p></td>
<td><p>Whether an aggressive garbage collection should be done.</p></td>
</tr>
<tr class="odd">
<td><p><code>async</code></p></td>
<td><p><code>false</code> if not set</p></td>
<td><p>Whether the garbage collection should run asynchronously.</p></td>
</tr>
</tbody>
</table>

### HeadInput

The `HeadInput` entity contains information for setting `HEAD` for a
project.

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
<td><p><code>ref</code></p></td>
<td><p>The ref to which <code>HEAD</code> should be set, the <code>refs/heads</code> prefix can be omitted.</p></td>
</tr>
</tbody>
</table>

### InheritedBooleanInfo

A boolean value that can also be inherited.

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
<td><p><code>value</code></p></td>
<td></td>
<td><p>The effective boolean value.</p></td>
</tr>
<tr class="even">
<td><p><code>configured_value</code></p></td>
<td></td>
<td><p>The configured value, can be <code>TRUE</code>, <code>FALSE</code> or <code>INHERITED</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>inherited_value</code></p></td>
<td><p>optional</p></td>
<td><p>The boolean value inherited from the parent.<br />
Not set if there is no parent.</p></td>
</tr>
</tbody>
</table>

### LabelTypeInfo

The `LabelTypeInfo` entity contains metadata about the labels that a
project has.

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
<td><p><code>values</code></p></td>
<td></td>
<td><p>Map of the available values to their description.</p></td>
</tr>
<tr class="even">
<td><p><code>default_value</code></p></td>
<td></td>
<td><p>The default value of this label.</p></td>
</tr>
</tbody>
</table>

### MaxObjectSizeLimitInfo

The `MaxObjectSizeLimitInfo` entity contains information about the [max
object size limit](config-gerrit.html#receive.maxObjectSizeLimit) of a
project.

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
<td><p><code>value</code></p></td>
<td><p>optional</p></td>
<td><p>The effective value of the max object size limit as a formatted string.<br />
Not set if there is no limit for the object size.</p></td>
</tr>
<tr class="even">
<td><p><code>configured_value</code></p></td>
<td><p>optional</p></td>
<td><p>The max object size limit that is configured on the project as a formatted string.<br />
Not set if there is no limit for the object size configured on project level.</p></td>
</tr>
<tr class="odd">
<td><p><code>inherited_value</code></p></td>
<td><p>optional</p></td>
<td><p>The max object size limit that is inherited as a formatted string.<br />
Not set if there is no global limit for the object size.</p></td>
</tr>
</tbody>
</table>

### ProjectAccessInput

The `ProjectAccessInput` describes changes that should be applied to a
project access config.

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
<td><p><code>remove</code></p></td>
<td><p>optional</p></td>
<td><p>A list of deductions to be applied to the project access as <a href="rest-api-access.html#project-access-info">ProjectAccessInfo</a> entities.</p></td>
</tr>
<tr class="even">
<td><p><code>add</code></p></td>
<td><p>optional</p></td>
<td><p>A list of additions to be applied to the project access as <a href="rest-api-access.html#project-access-info">ProjectAccessInfo</a> entities.</p></td>
</tr>
<tr class="odd">
<td><p><code>message</code></p></td>
<td><p>optional</p></td>
<td><p>A commit message for this change.</p></td>
</tr>
<tr class="even">
<td><p><code>parent</code></p></td>
<td><p>optional</p></td>
<td><p>A new parent for the project to inherit from. Changing the parent project requires administrative privileges.</p></td>
</tr>
</tbody>
</table>

### ProjectDescriptionInput

The `ProjectDescriptionInput` entity contains information for setting a
project description.

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
<td><p><code>description</code></p></td>
<td><p>optional</p></td>
<td><p>The project description.<br />
The project description will be deleted if not set.</p></td>
</tr>
<tr class="even">
<td><p><code>commit_message</code></p></td>
<td><p>optional</p></td>
<td><p>Message that should be used to commit the change of the project description in the <code>project.config</code> file to the <code>refs/meta/config</code> branch.</p></td>
</tr>
</tbody>
</table>

### ProjectInfo

The `ProjectInfo` entity contains information about a project.

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
<td><p>The URL encoded project name.</p></td>
</tr>
<tr class="even">
<td><p><code>name</code></p></td>
<td><p>not set if returned in a map where the project name is used as map key</p></td>
<td><p>The name of the project.</p></td>
</tr>
<tr class="odd">
<td><p><code>parent</code></p></td>
<td><p>optional</p></td>
<td><p>The name of the parent project.<br />
<code>?-&lt;n&gt;</code> if the parent project is not visible (<code>&lt;n&gt;</code> is a number which is increased for each non-visible project).</p></td>
</tr>
<tr class="even">
<td><p><code>description</code></p></td>
<td><p>optional</p></td>
<td><p>The description of the project.</p></td>
</tr>
<tr class="odd">
<td><p><code>state</code></p></td>
<td><p>optional</p></td>
<td><p><code>ACTIVE</code>, <code>READ_ONLY</code> or <code>HIDDEN</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>branches</code></p></td>
<td><p>optional</p></td>
<td><p>Map of branch names to HEAD revisions.</p></td>
</tr>
<tr class="odd">
<td><p><code>labels</code></p></td>
<td><p>optional</p></td>
<td><p>Map of label names to <a href="#label-type-info">LabelTypeInfo</a> entries. This field is filled for <a href="#create-project">Create Project</a> and <a href="#get-project">Get Project</a> calls.</p></td>
</tr>
<tr class="even">
<td><p><code>web_links</code></p></td>
<td><p>optional</p></td>
<td><p>Links to the project in external sites as a list of <a href="rest-api-changes.html#web-link-info">WebLinkInfo</a> entries.</p></td>
</tr>
</tbody>
</table>

### ProjectInput

The `ProjectInput` entity contains information for the creation of a new
project.

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
<td><p><code>name</code></p></td>
<td><p>optional</p></td>
<td><p>The name of the project (not encoded).<br />
If set, must match the project name in the URL.<br />
If name ends with <code>.git</code> the suffix will be automatically removed.</p></td>
</tr>
<tr class="even">
<td><p><code>parent</code></p></td>
<td><p>optional</p></td>
<td><p>The name of the parent project.<br />
If not set, the <code>All-Projects</code> project will be the parent project.</p></td>
</tr>
<tr class="odd">
<td><p><code>description</code></p></td>
<td><p>optional</p></td>
<td><p>The description of the project.</p></td>
</tr>
<tr class="even">
<td><p><code>permissions_only</code></p></td>
<td><p><code>false</code> if not set</p></td>
<td><p>Whether a permission-only project should be created.</p></td>
</tr>
<tr class="odd">
<td><p><code>create_empty_commit</code></p></td>
<td><p><code>false</code> if not set</p></td>
<td><p>Whether an empty initial commit should be created.</p></td>
</tr>
<tr class="even">
<td><p><code>submit_type</code></p></td>
<td><p>optional</p></td>
<td><p>The submit type that should be set for the project (<code>MERGE_IF_NECESSARY</code>, <code>REBASE_IF_NECESSARY</code>, <code>REBASE_ALWAYS</code>, <code>FAST_FORWARD_ONLY</code>, <code>MERGE_ALWAYS</code>, <code>CHERRY_PICK</code>).<br />
If not set, <code>MERGE_IF_NECESSARY</code> is set as submit type unless <a href="config-gerrit.html#repository.name.defaultSubmitType">repository.&lt;name&gt;.defaultSubmitType</a> is set to a different value.</p></td>
</tr>
<tr class="odd">
<td><p><code>branches</code></p></td>
<td><p>optional</p></td>
<td><p>A list of branches that should be initially created.<br />
For the branch names the <code>refs/heads/</code> prefix can be omitted.</p></td>
</tr>
<tr class="even">
<td><p><code>owners</code></p></td>
<td><p>optional</p></td>
<td><p>A list of groups that should be assigned as project owner.<br />
Each group in the list must be specified as <a href="rest-api-groups.html#group-id">group-id</a>.<br />
If not set, the <a href="config-gerrit.html#repository.name.ownerGroup">groups that are configured as default owners</a> are set as project owners.</p></td>
</tr>
<tr class="odd">
<td><p><code>use_contributor_agreements</code></p></td>
<td><p><code>INHERIT</code> if not set</p></td>
<td><p>Whether contributor agreements should be used for the project (<code>TRUE</code>, <code>FALSE</code>, <code>INHERIT</code>).</p></td>
</tr>
<tr class="even">
<td><p><code>use_signed_off_by</code></p></td>
<td><p><code>INHERIT</code> if not set</p></td>
<td><p>Whether the usage of <em>Signed-Off-By</em> footers is required for the project (<code>TRUE</code>, <code>FALSE</code>, <code>INHERIT</code>).</p></td>
</tr>
<tr class="odd">
<td><p><code>create_new_change_for_all_not_in_target</code></p></td>
<td><p><code>INHERIT</code> if not set</p></td>
<td><p>Whether a new change is created for every commit not in target branch for the project (<code>TRUE</code>, <code>FALSE</code>, <code>INHERIT</code>).</p></td>
</tr>
<tr class="even">
<td><p><code>use_content_merge</code></p></td>
<td><p><code>INHERIT</code> if not set</p></td>
<td><p>Whether content merge should be enabled for the project (<code>TRUE</code>, <code>FALSE</code>, <code>INHERIT</code>).<br />
<code>FALSE</code>, if the <code>submit_type</code> is <code>FAST_FORWARD_ONLY</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>require_change_id</code></p></td>
<td><p><code>INHERIT</code> if not set</p></td>
<td><p>Whether the usage of Change-Ids is required for the project (<code>TRUE</code>, <code>FALSE</code>, <code>INHERIT</code>).</p></td>
</tr>
<tr class="even">
<td><p><code>max_object_size_limit</code></p></td>
<td><p>optional</p></td>
<td><p>Max allowed Git object size for this project. Common unit suffixes of <em>k</em>, <em>m</em>, or <em>g</em> are supported.</p></td>
</tr>
<tr class="odd">
<td><p><code>plugin_config_values</code></p></td>
<td><p>optional</p></td>
<td><p>Plugin configuration values as map which maps the plugin name to a map of parameter names to values.</p></td>
</tr>
</tbody>
</table>

### ProjectParentInput

The `ProjectParentInput` entity contains information for setting a
project parent.

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
<td><p><code>parent</code></p></td>
<td></td>
<td><p>The name of the parent project.</p></td>
</tr>
<tr class="even">
<td><p><code>commit_message</code></p></td>
<td><p>optional</p></td>
<td><p>Message that should be used to commit the change of the project parent in the <code>project.config</code> file to the <code>refs/meta/config</code> branch.</p></td>
</tr>
</tbody>
</table>

### ReflogEntryInfo

The `ReflogEntryInfo` entity describes an entry in a reflog.

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
<td><p><code>old_id</code></p></td>
<td><p>The old commit ID.</p></td>
</tr>
<tr class="even">
<td><p><code>new_id</code></p></td>
<td><p>The new commit ID.</p></td>
</tr>
<tr class="odd">
<td><p><code>who</code></p></td>
<td><p>The user performing the change as a <a href="rest-api-changes.html#git-person-info">GitPersonInfo</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>comment</code></p></td>
<td><p>Comment of the reflog entry.</p></td>
</tr>
</tbody>
</table>

### RepositoryStatisticsInfo

The `RepositoryStatisticsInfo` entity contains information about
statistics of a Git repository.

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
<td><p><code>number_of_loose_objects</code></p></td>
<td><p>Number of loose objects.</p></td>
</tr>
<tr class="even">
<td><p><code>number_of_loose_refs</code></p></td>
<td><p>Number of loose refs.</p></td>
</tr>
<tr class="odd">
<td><p><code>number_of_pack_files</code></p></td>
<td><p>Number of pack files.</p></td>
</tr>
<tr class="even">
<td><p><code>number_of_packed_objects</code></p></td>
<td><p>Number of packed objects.</p></td>
</tr>
<tr class="odd">
<td><p><code>number_of_packed_refs</code></p></td>
<td><p>Number of packed refs.</p></td>
</tr>
<tr class="even">
<td><p><code>size_of_loose_objects</code></p></td>
<td><p>Size of loose objects in bytes.</p></td>
</tr>
<tr class="odd">
<td><p><code>size_of_packed_objects</code></p></td>
<td><p>Size of packed objects in bytes.</p></td>
</tr>
</tbody>
</table>

### TagInfo

The `TagInfo` entity contains information about a tag.

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
<td><p><code>ref</code></p></td>
<td></td>
<td><p>The ref of the tag.</p></td>
</tr>
<tr class="even">
<td><p><code>revision</code></p></td>
<td></td>
<td><p>For lightweight tags, the revision of the commit to which the tag points. For annotated tags, the revision of the tag object.</p></td>
</tr>
<tr class="odd">
<td><p><code>object</code></p></td>
<td><p>Only set for annotated tags.</p></td>
<td><p>The revision of the object to which the tag points.</p></td>
</tr>
<tr class="even">
<td><p><code>message</code></p></td>
<td><p>Only set for annotated tags.</p></td>
<td><p>The tag message. For signed tags, includes the signature.</p></td>
</tr>
<tr class="odd">
<td><p><code>tagger</code></p></td>
<td><p>Only set for annotated tags, if present in the tag.</p></td>
<td><p>The tagger as a <a href="rest-api-changes.html#git-person-info">GitPersonInfo</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>can_delete</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the calling user can delete this tag.</p></td>
</tr>
<tr class="odd">
<td><p><code>web_links</code></p></td>
<td><p>optional</p></td>
<td><p>Links to the tag in external sites as a list of <a href="rest-api-changes.html#web-link-info">WebLinkInfo</a> entries.</p></td>
</tr>
</tbody>
</table>

### TagInput

The `TagInput` entity contains information for creating a tag.

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
<td><p><code>ref</code></p></td>
<td></td>
<td><p>The name of the tag. The leading <code>refs/tags/</code> is optional.</p></td>
</tr>
<tr class="even">
<td><p><code>revision</code></p></td>
<td><p>optional</p></td>
<td><p>The revision to which the tag should point. If not specified, the project’s <code>HEAD</code> will be used.</p></td>
</tr>
<tr class="odd">
<td><p><code>message</code></p></td>
<td><p>optional</p></td>
<td><p>The tag message. When set, the tag will be created as an annotated tag.</p></td>
</tr>
</tbody>
</table>

### ThemeInfo

The `ThemeInfo` entity describes a theme.

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
<td><p><code>css</code></p></td>
<td><p>optional</p></td>
<td><p>The path to the <code>GerritSite.css</code> file.</p></td>
</tr>
<tr class="even">
<td><p><code>header</code></p></td>
<td><p>optional</p></td>
<td><p>The path to the <code>GerritSiteHeader.html</code> file.</p></td>
</tr>
<tr class="odd">
<td><p><code>footer</code></p></td>
<td><p>optional</p></td>
<td><p>The path to the <code>GerritSiteFooter.html</code> file.</p></td>
</tr>
</tbody>
</table>

    GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

