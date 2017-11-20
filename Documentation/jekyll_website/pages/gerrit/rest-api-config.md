---
title: " Gerrit Code Review - /config/ REST API"
sidebar: restapi_sidebar
permalink: rest-api-config.html
---
This page describes the config related REST endpoints. Please also take
note of the general information on the [REST API](rest-api.html).

## Config Endpoints

### Get Version

*GET /config/server/version*

Returns the version of the Gerrit server.

**Request.**

``` 
  GET /config/server/version HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Type: application/json; charset=UTF-8

  )]}'
  "2.7"
```

### Get Server Info

*GET /config/server/info*

Returns the information about the Gerrit server configuration.

**Request.**

``` 
  GET /config/server/info HTTP/1.0
```

As result a [ServerInfo](#server-info) entity is returned.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "accounts": {
      "visibility": "ALL"
    },
    "auth": {
      "auth_type": "LDAP",
      "use_contributor_agreements": true,
      "contributor_agreements": [
        {
          "name": "Individual",
          "description": "If you are going to be contributing code on your own, this is the one you want. You can sign this one online.",
          "url": "static/cla_individual.html"
        }
      ],
      "editable_account_fields": [
        "FULL_NAME",
        "REGISTER_NEW_EMAIL"
      ]
    },
    "download": {
      "schemes": {
        "anonymous http": {
          "url": "http://gerrithost:8080/${project}",
          "commands": {
            "Checkout": "git fetch http://gerrithost:8080/${project} ${ref} \u0026\u0026 git checkout FETCH_HEAD",
            "Format Patch": "git fetch http://gerrithost:8080/${project} ${ref} \u0026\u0026 git format-patch -1 --stdout FETCH_HEAD",
            "Pull": "git pull http://gerrithost:8080/${project} ${ref}",
            "Cherry Pick": "git fetch http://gerrithost:8080/${project} ${ref} \u0026\u0026 git cherry-pick FETCH_HEAD"
          },
          "clone_commands": {
            "Clone": "git clone http://gerrithost:8080/${project}",
            "Clone with commit-msg hook": "git clone http://gerrithost:8080/${project} \u0026\u0026 scp -p -P 29418 jdoe@gerrithost:hooks/commit-msg ${project}/.git/hooks/"
          }
        },
        "http": {
          "url": "http://jdoe@gerrithost:8080/${project}",
          "is_auth_required": true,
          "is_auth_supported": true,
          "commands": {
            "Checkout": "git fetch http://jdoe@gerrithost:8080/${project} ${ref} \u0026\u0026 git checkout FETCH_HEAD",
            "Format Patch": "git fetch http://jdoe@gerrithost:8080/${project} ${ref} \u0026\u0026 git format-patch -1 --stdout FETCH_HEAD",
            "Pull": "git pull http://jdoe@gerrithost:8080/${project} ${ref}",
            "Cherry Pick": "git fetch http://jdoe@gerrithost:8080/${project} ${ref} \u0026\u0026 git cherry-pick FETCH_HEAD"
          },
          "clone_commands": {
            "Clone": "git clone http://jdoe@gerrithost:8080/${project}",
            "Clone with commit-msg hook": "git clone http://jdoe@gerrithost:8080/${project} \u0026\u0026 scp -p -P 29418 jdoe@gerrithost:hooks/commit-msg ${project}/.git/hooks/"
          }
        },
        "ssh": {
          "url": "ssh://jdoe@gerrithost:29418/${project}",
          "is_auth_required": true,
          "is_auth_supported": true,
          "commands": {
            "Checkout": "git fetch ssh://jdoe@gerrithost:29418/${project} ${ref} \u0026\u0026 git checkout FETCH_HEAD",
            "Format Patch": "git fetch ssh://jdoe@gerrithost:29418/${project} ${ref} \u0026\u0026 git format-patch -1 --stdout FETCH_HEAD",
            "Pull": "git pull ssh://jdoe@gerrithost:29418/${project} ${ref}",
            "Cherry Pick": "git fetch ssh://jdoe@gerrithost:29418/${project} ${ref} \u0026\u0026 git cherry-pick FETCH_HEAD"
          },
          "clone_commands": {
            "Clone": "git clone ssh://jdoe@gerrithost:29418/${project}",
            "Clone with commit-msg hook": "git clone ssh://jdoe@gerrithost:29418/${project} \u0026\u0026 scp -p -P 29418 jdoe@gerrithost:hooks/commit-msg ${project}/.git/hooks/"
          }
        }
      },
      "archives": [
        "tgz",
        "tar",
        "tbz2",
        "txz"
      ]
    },
    "gerrit": {
      "all_projects": "All-Projects",
      "all_users": "All-Users"
      "doc_search": true,
      "web_uis": [
        "gwt"
      ]
    },
    "sshd": {},
    "suggest": {
      "from": 0
    },
    "user": {
      "anonymous_coward_name": "Anonymous Coward"
    }
  }
```

### Check Consistency

*POST /config/server/check.consistency*

Runs consistency checks and returns detected problems.

Input for the consistency checks that should be run must be provided in
the request body inside a
[ConsistencyCheckInput](#consistency-check-input) entity.

**Request.**

``` 
  POST /config/server/check.consistency HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "check_accounts": {},
    "check_account_external_ids": {}
  }
```

As result a [ConsistencyCheckInfo](#consistency-check-info) entity is
returned that contains detected consistency problems.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "check_accounts_result": {
      "problems": [
        {
          "status": "ERROR",
          "message": "Account \u00271000024\u0027 has no external ID for its preferred email \u0027foo.bar@example.com\u0027"
        }
      ]
    }
    "check_account_external_ids_result": {
      "problems": [
        {
          "status": "ERROR",
          "message": "External ID \u0027uuid:ccb8d323-1361-45aa-8874-41987a660c46\u0027 belongs to account that doesn\u0027t exist: 1000012"
        }
      ]
    }
  }
```

### Confirm Email

*PUT /config/server/email.confirm*

Confirms that the user owns an email address.

The email token must be provided in the request body inside an
[EmailConfirmationInput](#email-confirmation-input) entity.

**Request.**

``` 
  PUT /config/server/email.confirm HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "token": "Enim+QNbAo6TV8Hur8WwoUypI6apG7qBPvF+bw==$MTAwMDAwNDp0ZXN0QHRlc3QuZGU="
  }
```

The response is "`204 No Content`".

If the token is invalid or if it’s the token of another user the request
fails and the response is "`422 Unprocessable Entity`".

### List Caches

*GET /config/server/caches/*

Lists the caches of the server. Caches defined by plugins are included.

The caller must be a member of a group that is granted one of the
following capabilities:

  - [View Caches](access-control.html#capability_viewCaches)

  - [Maintain Server](access-control.html#capability_maintainServer)

  - [Administrate
    Server](access-control.html#capability_administrateServer)

As result a map of [CacheInfo](#cache-info) entities is returned.

The entries in the map are sorted by cache name.

**Request.**

``` 
  GET /config/server/caches/ HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "accounts": {
      "type": "MEM",
      "entries": {
        "mem": 4
      },
      "average_get": "2.5ms",
      "hit_ratio": {
        "mem": 94
      }
    },
    "adv_bases": {
      "type": "MEM",
      "entries": {},
      "hit_ratio": {}
    },
    "change_kind": {
      "type": "DISK",
      "entries": {
        "space": "0.00k"
      },
      "hit_ratio": {}
    },
    "changes": {
      "type": "MEM",
      "entries": {},
      "hit_ratio": {}
    },
    "conflicts": {
      "type": "DISK",
      "entries": {
        "mem": 2,
        "disk": 3,
        "space": "2.75k"
      },
      "hit_ratio": {
        "mem": 0,
        "disk": 100
      }
    },
    "diff": {
      "type": "DISK",
      "entries": {
        "mem": 177,
        "disk": 253,
        "space": "170.97k"
      },
      "average_get": "1.1ms",
      "hit_ratio": {
        "mem": 67,
        "disk": 100
      }
    },
    "diff_intraline": {
      "type": "DISK",
      "entries": {
        "mem": 1,
        "disk": 1,
        "space": "0.37k"
      },
      "average_get": "6.8ms",
      "hit_ratio": {
        "mem": 0
      }
    },
    "git_tags": {
      "type": "DISK",
      "entries": {
        "space": "0.00k"
      },
      "hit_ratio": {}
    },
    groups": {
      "type": "MEM",
      "entries": {
        "mem": 27
      },
      "average_get": "183.2us",
      "hit_ratio": {
        "mem": 12
      }
    },
    "groups_bymember": {
      "type": "MEM",
      "entries": {},
      "hit_ratio": {}
    },
    "groups_byname": {
      "type": "MEM",
      "entries": {},
      "hit_ratio": {}
    },
    "groups_bysubgroup": {
      "type": "MEM",
      "entries": {},
      "hit_ratio": {}
    },
    "groups_byuuid": {
      "type": "MEM",
      "entries": {
        "mem": 25
      },
      "average_get": "173.4us",
      "hit_ratio": {
        "mem": 13
      }
    },
    "groups_external": {
      "type": "MEM",
      "entries": {},
      "hit_ratio": {}
    },
    "permission_sort": {
      "type": "MEM",
      "entries": {
        "mem": 16
      },
      "hit_ratio": {
        "mem": 96
      }
    },
    "plugin_resources": {
      "type": "MEM",
      "entries": {
        "mem": 2
      },
      "hit_ratio": {
        "mem": 83
      }
    },
    "project_list": {
      "type": "MEM",
      "entries": {
        "mem": 1
      },
      "average_get": "18.6ms",
      "hit_ratio": {
        "mem": 0
      }
    },
    "projects": {
      "type": "MEM",
      "entries": {
        "mem": 35
      },
      "average_get": "8.6ms",
      "hit_ratio": {
        "mem": 99
      }
    },
    "quota-repo_size": {
      "type": "DISK",
      "entries": {
        "space": "0.00k"
      },
      "hit_ratio": {}
    },
    "sshkeys": {
      "type": "MEM",
      "entries": {
        "mem": 1
      },
      "average_get": "3.2ms",
      "hit_ratio": {
        "mem": 50
      }
    },
    "web_sessions": {
      "type": "DISK",
      "entries": {
        "mem": 1,
        "disk": 2,
        "space": "0.78k"
      },
      "hit_ratio": {
        "mem": 82
      }
    }
  }
```

It is possible to get different output formats by specifying the
`format` option:

  - `LIST`:
    
    Returns the cache names as JSON list.
    
    The cache names are lexicographically sorted.
    
    **Request.**
    
    ``` 
      GET /config/server/caches/?format=LIST HTTP/1.0
    ```
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      [
        "accounts",
        "adv_bases",
        "change_kind",
        "changes",
        "conflicts",
        "diff",
        "diff_intraline",
        "git_tags",
        "groups",
        "groups_bymember",
        "groups_byname",
        "groups_bysubgroup",
        "groups_byuuid",
        "groups_external",
        "permission_sort",
        "plugin_resources",
        "project_list",
        "projects",
        "quota-repo_size",
        "sshkeys",
        "web_sessions"
      ]
    ```

  - `TEXT_LIST`:
    
    Returns the cache names as a UTF-8 list that is base64 encoded. The
    cache names are delimited by *\\n*.
    
    The cache names are lexicographically sorted.
    
    **Request.**
    
    ``` 
      GET /config/server/caches/?format=TEXT_LIST HTTP/1.0
    ```
    
    **Response.**
    
    ``` 
      HTTP/1.1 200 OK
      Content-Type: text/plain; charset=UTF-8
    
      YWNjb3VudHMKYW...ViX3Nlc3Npb25z
    ```
    
    E.g. this could be used to flush all
    caches:
    
    ``` 
      for c in $(curl --user jdoe:TNAuLkXsIV7w http://gerrit/a/config/server/caches/?format=TEXT_LIST | base64 -D)
      do
        curl --user jdoe:TNAuLkXsIV7w -X POST http://gerrit/a/config/server/caches/$c/flush
      done
    ```

### Cache Operations

*POST /config/server/caches/*

Executes a cache operation that is specified in the request body in a
[CacheOperationInput](#cache-operation-input) entity.

#### Flush All Caches

**Request.**

``` 
  POST /config/server/caches/ HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "operation": "FLUSH_ALL"
  }
```

**Response.**

``` 
  HTTP/1.1 200 OK
```

#### Flush Several Caches At Once

**Request.**

``` 
  POST /config/server/caches/ HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "operation": "FLUSH",
    "caches": [
      "projects",
      "project_list"
    ]
  }
```

**Response.**

``` 
  HTTP/1.1 200 OK
```

### Get Cache

*GET /config/server/caches/[{cache-name}](#cache-name)*

Retrieves information about a cache.

The caller must be a member of a group that is granted one of the
following capabilities:

  - [View Caches](access-control.html#capability_viewCaches)

  - [Maintain Server](access-control.html#capability_maintainServer)

  - [Administrate
    Server](access-control.html#capability_administrateServer)

As result a [CacheInfo](#cache-info) entity is returned.

**Request.**

``` 
  GET /config/server/caches/projects HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "name": "projects",
    "type": "MEM",
    "entries": {
      "mem": 35
    },
    "average_get": " 8.6ms",
    "hit_ratio": {
      "mem": 99
    }
  }
```

### Flush Cache

*POST /config/server/caches/[{cache-name}](#cache-name)/flush*

Flushes a cache.

The caller must be a member of a group that is granted one of the
following capabilities:

  - [Flush Caches](access-control.html#capability_flushCaches) (any
    cache except "web\_sessions")

  - [Maintain Server](access-control.html#capability_maintainServer)
    (any cache including "web\_sessions")

  - [Administrate
    Server](access-control.html#capability_administrateServer) (any
    cache including "web\_sessions")

**Request.**

``` 
  POST /config/server/caches/projects/flush HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
```

### Get Summary

*GET /config/server/summary*

Retrieves a summary of the current server state.

The caller must be a member of a group that is granted the [Administrate
Server](access-control.html#capability_administrateServer) capability.

The following options are supported:

  - `jvm`:
    
    Includes a JVM summary.

  - `gc`:
    
    Requests a Java garbage collection before computing the information
    about the Java memory heap.

**Request.**

``` 
  GET /config/server/summary?jvm HTTP/1.0
```

As result a [SummaryInfo](#summary-info) entity is returned.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "task_summary": {
      "total": 2,
      "sleeping": 2
    },
    "mem_summary": {
      "total": "341.06m",
      "used": "57.16m",
      "free": "283.90m",
      "buffers": "0.00k",
      "max": "1.67g",
    }
    "thread_summary": {
      "cpus": 8,
      "threads": 44,
      "counts": {
        "HTTP": {
          "RUNNABLE": 3,
          "TIMED_WAITING": 2
        },
        "SSH-Interactive-Worker": {
          "WAITING": 1
        },
        "Other": {
          "WAITING": 10,
          "RUNNABLE": 2,
          "TIMED_WAITING": 25
        },
        "SshCommandStart": {
          "WAITING": 1
        }
      }
    },
    "jvm_summary": {
      "vm_vendor": "Oracle Corporation",
      "vm_name": "Java HotSpot(TM) 64-Bit Server VM",
      "vm_version": "23.25-b01",
      "os_name": "Mac OS X",
      "os_version": "10.8.5",
      "os_arch": "x86_64",
      "user": "gerrit",
      "host": "GERRIT",
      "current_working_directory": "/Users/gerrit/site",
      "site": "/Users/gerrit/site"
    }
  }
```

### List Capabilities

*GET /config/server/capabilities*

Lists the capabilities that are available in the system. There are two
kinds of capabilities: core and plugin-owned capabilities.

As result a map of [CapabilityInfo](#capability-info) entities is
returned.

The entries in the map are sorted by capability ID.

**Request.**

``` 
  GET /config/server/capabilities/ HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "accessDatabase": {
      "id": "accessDatabase",
      "name": "Access Database"
    },
    "administrateServer": {
      "id": "administrateServer",
      "name": "Administrate Server"
    },
    "createAccount": {
      "id": "createAccount",
      "name": "Create Account"
    },
    "createGroup": {
      "id": "createGroup",
      "name": "Create Group"
    },
    "createProject": {
      "id": "createProject",
      "name": "Create Project"
    },
    "emailReviewers": {
      "id": "emailReviewers",
      "name": "Email Reviewers"
    },
    "flushCaches": {
      "id": "flushCaches",
      "name": "Flush Caches"
    },
    "killTask": {
      "id": "killTask",
      "name": "Kill Task"
    },
    "priority": {
      "id": "priority",
      "name": "Priority"
    },
    "queryLimit": {
      "id": "queryLimit",
      "name": "Query Limit"
    },
    "runGC": {
      "id": "runGC",
      "name": "Run Garbage Collection"
    },
    "streamEvents": {
      "id": "streamEvents",
      "name": "Stream Events"
    },
    "viewCaches": {
      "id": "viewCaches",
      "name": "View Caches"
    },
    "viewConnections": {
      "id": "viewConnections",
      "name": "View Connections"
    },
    "viewPlugins": {
      "id": "viewPlugins",
      "name": "View Plugins"
    },
    "viewQueue": {
      "id": "viewQueue",
      "name": "View Queue"
    }
  }
```

### List Tasks

*GET /config/server/tasks/*

Lists the tasks from the background work queues that the Gerrit daemon
is currently performing, or will perform in the near future.

Gerrit contains an internal scheduler, similar to cron, that it uses to
queue and dispatch both short and long term tasks.

Tasks that are completed or canceled exit the queue very quickly once
they enter this state, but it can be possible to observe tasks in these
states.

End-users may see a task only if they can also see the project the task
is associated with. Tasks operating on other projects, or that do not
have a specific project, are hidden.

The caller must be a member of a group that is granted one of the
following capabilities:

  - [View Queue](access-control.html#capability_viewQueue)

  - [Maintain Server](access-control.html#capability_maintainServer)

  - [Administrate
    Server](access-control.html#capability_administrateServer)

As result a list of [TaskInfo](#task-info) entities is returned.

The entries in the list are sorted by task state, remaining delay and
command.

**Request.**

``` 
  GET /config/server/tasks/ HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "id": "1e688bea",
      "state": "SLEEPING",
      "start_time": "2014-06-11 12:58:51.991000000",
      "delay": 3453,
      "command": "Reload Submit Queue"
    },
    {
      "id": "3e6d4ffa",
      "state": "SLEEPING",
      "start_time": "2014-06-11 12:58:51.508000000",
      "delay": 3287966,
      "command": "Log File Compressor"
    }
  ]
```

### Get Task

*GET /config/server/tasks/[{task-id}](#task-id)*

Retrieves a task from the background work queue that the Gerrit daemon
is currently performing, or will perform in the near future.

End-users may see a task only if they can also see the project the task
is associated with. Tasks operating on other projects, or that do not
have a specific project, are hidden.

The caller must be a member of a group that is granted one of the
following capabilities:

  - [View Queue](access-control.html#capability_viewQueue)

  - [Maintain Server](access-control.html#capability_maintainServer)

  - [Administrate
    Server](access-control.html#capability_administrateServer)

As result a [TaskInfo](#task-info) entity is returned.

**Request.**

``` 
  GET /config/server/tasks/1e688bea HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "1e688bea",
    "state": "SLEEPING",
    "start_time": "2014-06-11 12:58:51.991000000",
    "delay": 3453,
    "command": "Reload Submit Queue"
  }
```

### Delete Task

*DELETE /config/server/tasks/[{task-id}](#task-id)*

Kills a task from the background work queue that the Gerrit daemon is
currently performing, or will perform in the near future.

The caller must be a member of a group that is granted one of the
following capabilities:

  - [Kill Task](access-control.html#capability_kill)

  - [Maintain Server](access-control.html#capability_maintainServer)

  - [Administrate
    Server](access-control.html#capability_administrateServer)

End-users may see a task only if they can also see the project the task
is associated with. Tasks operating on other projects, or that do not
have a specific project, are hidden.

Members of a group granted one of the following capabilities may view
all tasks:

  - [View Queue](access-control.html#capability_viewQueue)

  - [Maintain Server](access-control.html#capability_maintainServer)

  - [Administrate
    Server](access-control.html#capability_administrateServer)

**Request.**

``` 
  DELETE /config/server/tasks/1e688bea HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

### Get Top Menus

*GET /config/server/top-menus*

Returns the list of additional top menu entries.

**Request.**

``` 
  GET /config/server/top-menus HTTP/1.0
```

As response a list of the additional top menu entries as
[TopMenuEntryInfo](#top-menu-entry-info) entities is returned.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "name": "Top Menu Entry",
      "items": [
        {
          "url": "http://gerrit.googlecode.com/",
          "name": "Gerrit",
          "target": "_blank"
        }
      ]
    }
  ]
```

### Get Default User Preferences

*GET /config/server/preferences*

Returns the default user preferences for the server.

**Request.**

``` 
  GET /a/config/server/preferences HTTP/1.0
```

As response a [PreferencesInfo](rest-api-accounts.html#preferences-info)
is returned.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "changes_per_page": 25,
    "show_site_header": true,
    "use_flash_clipboard": true,
    "download_command": "CHECKOUT",
    "date_format": "STD",
    "time_format": "HHMM_12",
    "diff_view": "SIDE_BY_SIDE",
    "size_bar_in_change_table": true,
    "review_category_strategy": "NONE",
    "mute_common_path_prefixes": true,
    "publish_comments_on_push": true,
    "my": [
      {
        "url": "#/dashboard/self",
        "name": "Changes"
      },
      {
        "url": "#/q/has:draft",
        "name": "Draft Comments"
      },
      {
        "url": "#/q/has:edit",
        "name": "Edits"
      },
      {
        "url": "#/q/is:watched+is:open",
        "name": "Watched Changes"
      },
      {
        "url": "#/q/is:starred",
        "name": "Starred Changes"
      },
      {
        "url": "#/groups/self",
        "name": "Groups"
      }
    ],
    "email_strategy": "ENABLED"
  }
```

### Set Default User Preferences

*PUT /config/server/preferences*

Sets the default user preferences for the server.

The new user preferences must be provided in the request body as a
[PreferencesInput](rest-api-accounts.html#preferences-input) entity.

To be allowed to set default preferences, a user must be a member of a
group that is granted the [Administrate
Server](access-control.html#capability_administrateServer) capability.

**Request.**

``` 
  PUT /a/config/server/preferences HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "changes_per_page": 50
  }
```

As response a [PreferencesInfo](rest-api-accounts.html#preferences-info)
is returned.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "changes_per_page": 50,
    "show_site_header": true,
    "use_flash_clipboard": true,
    "download_command": "CHECKOUT",
    "date_format": "STD",
    "time_format": "HHMM_12",
    "diff_view": "SIDE_BY_SIDE",
    "size_bar_in_change_table": true,
    "review_category_strategy": "NONE",
    "mute_common_path_prefixes": true,
    "publish_comments_on_push": true,
    "my": [
      {
        "url": "#/dashboard/self",
        "name": "Changes"
      },
      {
        "url": "#/q/has:draft",
        "name": "Draft Comments"
      },
      {
        "url": "#/q/has:edit",
        "name": "Edits"
      },
      {
        "url": "#/q/is:watched+is:open",
        "name": "Watched Changes"
      },
      {
        "url": "#/q/is:starred",
        "name": "Starred Changes"
      },
      {
        "url": "#/groups/self",
        "name": "Groups"
      }
    ],
    "email_strategy": "ENABLED"
  }
```

### Get Default Diff Preferences

*GET /config/server/preferences.diff*

Returns the default diff preferences for the server.

**Request.**

``` 
  GET /a/config/server/preferences.diff HTTP/1.0
```

As response a
[DiffPreferencesInfo](rest-api-accounts.html#diff-preferences-info) is
returned.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "context": 10,
    "tab_size": 8,
    "line_length": 100,
    "cursor_blink_rate": 0,
    "intraline_difference": true,
    "show_line_endings": true,
    "show_tabs": true,
    "show_whitespace_errors": true,
    "syntax_highlighting": true,
    "auto_hide_diff_table_header": true,
    "theme": "DEFAULT",
    "ignore_whitespace": "IGNORE_NONE"
  }
```

### Set Default Diff Preferences

*PUT /config/server/preferences.diff*

Sets the default diff preferences for the server.

The new diff preferences must be provided in the request body as a
[DiffPreferencesInput](rest-api-accounts.html#diff-preferences-input)
entity.

To be allowed to set default diff preferences, a user must be a member
of a group that is granted the [Administrate
Server](access-control.html#capability_administrateServer) capability.

**Request.**

``` 
  PUT /a/config/server/preferences.diff HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "context": 10,
    "tab_size": 8,
    "line_length": 80,
    "cursor_blink_rate": 0,
    "intraline_difference": true,
    "show_line_endings": true,
    "show_tabs": true,
    "show_whitespace_errors": true,
    "syntax_highlighting": true,
    "auto_hide_diff_table_header": true,
    "theme": "DEFAULT",
    "ignore_whitespace": "IGNORE_NONE"
  }
```

As response a
[DiffPreferencesInfo](rest-api-accounts.html#diff-preferences-info) is
returned.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "context": 10,
    "tab_size": 8,
    "line_length": 80,
    "cursor_blink_rate": 0,
    "intraline_difference": true,
    "show_line_endings": true,
    "show_tabs": true,
    "show_whitespace_errors": true,
    "syntax_highlighting": true,
    "auto_hide_diff_table_header": true,
    "theme": "DEFAULT",
    "ignore_whitespace": "IGNORE_NONE"
  }
```

## IDs

### {cache-name}

The name of the cache.

If the cache is defined by a plugin the cache name must include the
plugin name: "\<plugin-name\>-\<cache-name\>".

Gerrit core caches can optionally be prefixed with "gerrit":
"gerrit-\<cache-name\>".

### {task-id}

The ID of the task (hex string).

## JSON Entities

### AccountsConfigInfo

The `AccountsConfigInfo` entity contains information about Gerrit
configuration from the [accounts](config-gerrit.html#accounts) section.

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
<td><p><code>visibility</code></p></td>
<td><p><a href="config-gerrit.html#accounts.visibility">Visibility setting for accounts</a>.</p></td>
</tr>
</tbody>
</table>

### AuthInfo

The `AuthInfo` entity contains information about the authentication
configuration of the Gerrit server.

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
<td><p><code>type</code></p></td>
<td></td>
<td><p>The <a href="config-gerrit.html#auth.type">authentication type</a> that is configured on the server. Can be <code>OPENID</code>, <code>OPENID_SSO</code>, <code>OAUTH</code>, <code>HTTP</code>, <code>HTTP_LDAP</code>, <code>CLIENT_SSL_CERT_LDAP</code>, <code>LDAP</code>, <code>LDAP_BIND</code>, <code>CUSTOM_EXTENSION</code> or <code>DEVELOPMENT_BECOME_ANY_ACCOUNT</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>use_contributor_agreements</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether <a href="config-gerrit.html#auth.contributorAgreements">contributor agreements</a> are required.</p></td>
</tr>
<tr class="odd">
<td><p><code>contributor_agreements</code></p></td>
<td><p>not set if <code>use_contributor_agreements</code> is <code>false</code></p></td>
<td><p>List of contributor agreements as <a href="rest-api-accounts.html#contributor-agreement-info">ContributorAgreementInfo</a> entities.</p></td>
</tr>
<tr class="even">
<td><p><code>editable_account_fields</code></p></td>
<td></td>
<td><p>List of account fields that are editable. Possible values are <code>FULL_NAME</code>, <code>USER_NAME</code> and <code>REGISTER_NEW_EMAIL</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>login_url</code></p></td>
<td><p>optional</p></td>
<td><p>The <a href="config-gerrit.html#auth.loginUrl">login URL</a>. Only set if <a href="config-gerrit.html#auth.type">authentication type</a> is <code>HTTP</code> or <code>HTTP_LDAP</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>login_text</code></p></td>
<td><p>optional</p></td>
<td><p>The <a href="config-gerrit.html#auth.loginText">login text</a>. Only set if <a href="config-gerrit.html#auth.type">authentication type</a> is <code>HTTP</code> or <code>HTTP_LDAP</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>switch_account_url</code></p></td>
<td><p>optional</p></td>
<td><p>The <a href="config-gerrit.html#auth.switchAccountUrl">URL to switch accounts</a>.</p></td>
</tr>
<tr class="even">
<td><p><code>register_url</code></p></td>
<td><p>optional</p></td>
<td><p>The <a href="config-gerrit.html#auth.registerUrl">register URL</a>. Only set if <a href="config-gerrit.html#auth.type">authentication type</a> is <code>LDAP</code>, <code>LDAP_BIND</code> or <code>CUSTOM_EXTENSION</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>register_text</code></p></td>
<td><p>optional</p></td>
<td><p>The <a href="config-gerrit.html#auth.registerText">register text</a>. Only set if <a href="config-gerrit.html#auth.type">authentication type</a> is <code>LDAP</code>, <code>LDAP_BIND</code> or <code>CUSTOM_EXTENSION</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>edit_full_name_url</code></p></td>
<td><p>optional</p></td>
<td><p>The <a href="config-gerrit.html#auth.editFullNameUrl">URL to edit the full name</a>. Only set if <a href="config-gerrit.html#auth.type">authentication type</a> is <code>LDAP</code>, <code>LDAP_BIND</code> or <code>CUSTOM_EXTENSION</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>http_password_url</code></p></td>
<td><p>optional</p></td>
<td><p>The <a href="config-gerrit.html#auth.httpPasswordUrl">URL to obtain an HTTP password</a>. Only set if <a href="config-gerrit.html#auth.type">authentication type</a> is <code>CUSTOM_EXTENSION</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>git_basic_auth_policy</code></p></td>
<td><p>optional</p></td>
<td><p>The <a href="config-gerrit.html#auth.gitBasicAuthPolicy">policy</a> to authenticate Git over HTTP and REST API requests when <a href="config-gerrit.html#auth.type">authentication type</a> is <code>LDAP</code>. Can be <code>HTTP</code>, <code>LDAP</code> or <code>HTTP_LDAP</code>.</p></td>
</tr>
</tbody>
</table>

### CacheInfo

The `CacheInfo` entity contains information about a cache.

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
<td><p>not set if returned in a map where the cache name is used as map key</p></td>
<td><p>The cache name. If the cache is defined by a plugin the cache name includes the plugin name: &quot;&lt;plugin-name&gt;-&lt;cache-name&gt;&quot;.</p></td>
</tr>
<tr class="even">
<td><p><code>type</code></p></td>
<td></td>
<td><p>The type of the cache (<code>MEM</code>: in memory cache, <code>DISK</code>: disk cache).</p></td>
</tr>
<tr class="odd">
<td><p><code>entries</code></p></td>
<td></td>
<td><p>Information about the entries in the cache as a <a href="#entries-info">EntriesInfo</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>average_get</code></p></td>
<td><p>optional</p></td>
<td><p>The average duration of getting one entry from the cache. The value is returned with a standard time unit abbreviation (<code>ns</code>: nanoseconds, <code>us</code>: microseconds, <code>ms</code>: milliseconds, <code>s</code>: seconds).</p></td>
</tr>
<tr class="odd">
<td><p><code>hit_ratio</code></p></td>
<td></td>
<td><p>Information about the hit ratio as a <a href="#hit-ration-info">HitRatioInfo</a> entity.</p></td>
</tr>
</tbody>
</table>

### CacheOperationInput

The `CacheOperationInput` entity contains information about an operation
that should be executed on caches.

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
<td><p><code>operation</code></p></td>
<td></td>
<td><p>The cache operation that should be executed:</p>
<p><code>FLUSH_ALL</code>: Flushes all caches, except the <code>web_sessions</code> cache.</p>
<p><code>FLUSH</code>: Flushes the specified caches.</p></td>
</tr>
<tr class="even">
<td><p><code>caches</code></p></td>
<td><p>optional</p></td>
<td><p>A list of cache names. This list defines the caches on which the specified operation should be executed. Whether this list must be specified depends on the operation being executed.</p></td>
</tr>
</tbody>
</table>

### CapabilityInfo

The `CapabilityInfo` entity contains information about a capability.

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
<td><p><code>id</code></p></td>
<td><p>capability ID</p></td>
</tr>
<tr class="even">
<td><p><code>name</code></p></td>
<td><p>capability name</p></td>
</tr>
</tbody>
</table>

### ChangeConfigInfo

The `ChangeConfigInfo` entity contains information about Gerrit
configuration from the [change](config-gerrit.html#change) section.

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
<td><p><code>allow_blame</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p><a href="config-gerrit.html#change.allowBlame">Whether blame on side by side diff is allowed</a>.</p></td>
</tr>
<tr class="even">
<td><p><code>large_change</code></p></td>
<td></td>
<td><p><a href="config-gerrit.html#change.largeChange">Number of changed lines from which on a change is considered as a large change</a>.</p></td>
</tr>
<tr class="odd">
<td><p><code>private_by_default</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Returns true if changes are by default created as private. See <a href="config-gerrit.html#change.privateByDefault">privateByDefault</a></p></td>
</tr>
<tr class="even">
<td><p><code>reply_label</code></p></td>
<td></td>
<td><p><a href="config-gerrit.html#change.replyTooltip">Label name for the reply button</a>.</p></td>
</tr>
<tr class="odd">
<td><p><code>reply_tooltip</code></p></td>
<td></td>
<td><p><a href="config-gerrit.html#change.replyTooltip">Tooltip for the reply button</a>.</p></td>
</tr>
<tr class="even">
<td><p><code>update_delay</code></p></td>
<td></td>
<td><p><a href="config-gerrit.html#change.updateDelay">How often in seconds the web interface should poll for updates to the currently open change</a>.</p></td>
</tr>
<tr class="odd">
<td><p><code>submit_whole_topic</code></p></td>
<td></td>
<td><p><a href="config-gerrit.html#change.submitWholeTopic">A configuration if the whole topic is submitted</a>.</p></td>
</tr>
</tbody>
</table>

### CheckAccountExternalIdsInput

The `CheckAccountExternalIdsInput` entity contains input for the account
external IDs consistency check.

Currently this entity contains no fields.

### CheckAccountExternalIdsResultInfo

The `CheckAccountExternalIdsResultInfo` entity contains the result of
running the account external IDs consistency check.

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
<td><p><code>problems</code></p></td>
<td><p>A list of <a href="#consistency-problem-info">ConsistencyProblemInfo</a> entities.</p></td>
</tr>
</tbody>
</table>

### ConsistencyCheckInfo

The `ConsistencyCheckInfo` entity contains the results of running
consistency checks.

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
<td><p><code>check_accounts_result</code></p></td>
<td><p>optional</p></td>
<td><p>The result of running the account consistency check as a <a href="#check-accounts-result-info">CheckAccountsResultInfo</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>check_account_external_ids_result</code></p></td>
<td><p>optional</p></td>
<td><p>The result of running the account external ID consistency check as a <a href="#check-account-external-ids-result-info">CheckAccountExternalIdsResultInfo</a> entity.</p></td>
</tr>
</tbody>
</table>

### ConsistencyCheckInput

The `ConsistencyCheckInput` entity contains information about which
consistency checks should be run.

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
<td><p><code>check_accounts</code></p></td>
<td><p>optional</p></td>
<td><p>Input for the account consistency check as <a href="#check-accounts-input">CheckAccountsInput</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>check_account_external_ids</code></p></td>
<td><p>optional</p></td>
<td><p>Input for the account external ID consistency check as <a href="#check-account-external-ids-input">CheckAccountExternalIdsInput</a> entity.</p></td>
</tr>
</tbody>
</table>

### ConsistencyProblemInfo

The `ConsistencyProblemInfo` entity contains information about a
consistency problem.

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
<td><p><code>status</code></p></td>
<td><p>The status of the consistency problem.<br />
Possible values are <code>ERROR</code> and <code>WARNING</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>message</code></p></td>
<td><p>Message describing the consistency problem.</p></td>
</tr>
</tbody>
</table>

### DownloadInfo

The `DownloadInfo` entity contains information about supported download
options.

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
<td><p><code>schemes</code></p></td>
<td><p>The supported download schemes as a map which maps the scheme name to a of <a href="#download-scheme-info">DownloadSchemeInfo</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>archives</code></p></td>
<td><p>List of supported archive formats. Possible values are <code>tgz</code>, <code>tar</code>, <code>tbz2</code> and <code>txz</code>.</p></td>
</tr>
</tbody>
</table>

### DownloadSchemeInfo

The `DownloadSchemeInfo` entity contains information about a supported
download scheme and its commands.

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
<td><p><code>url</code></p></td>
<td></td>
<td><p>placeholder for the project name.</p></td>
</tr>
<tr class="even">
<td><p><code>is_auth_required</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether this download scheme requires authentication.</p></td>
</tr>
<tr class="odd">
<td><p><code>is_auth_supported</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether this download scheme supports authentication.</p></td>
</tr>
<tr class="even">
<td><p><code>commands</code></p></td>
<td></td>
<td><p>Download commands as a map which maps the command name to the download placeholder for the (change) ref.</p>
<p>Empty, if accessed anonymously and the download scheme requires authentication.</p></td>
</tr>
<tr class="odd">
<td><p><code>clone_commands</code></p></td>
<td></td>
<td><p>Clone commands as a map which maps the command name to the clone placeholder for <em>bar</em>).</p>
<p>Empty, if accessed anonymously and the download scheme requires authentication.</p></td>
</tr>
</tbody>
</table>

### EmailConfirmationInput

The `EmailConfirmationInput` entity contains information for confirming
an email address.

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
<td><p><code>token</code></p></td>
<td><p>The token that was sent by mail to a newly registered email address.</p></td>
</tr>
</tbody>
</table>

### EntriesInfo

The `EntriesInfo` entity contains information about the entries in a
cache.

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
<td><p><code>mem</code></p></td>
<td><p>optional</p></td>
<td><p>Number of cache entries that are held in memory.</p></td>
</tr>
<tr class="even">
<td><p><code>disk</code></p></td>
<td><p>optional</p></td>
<td><p>Number of cache entries on the disk. For non-disk caches this value is not set; for disk caches it is only set if there are entries in the cache.</p></td>
</tr>
<tr class="odd">
<td><p><code>space</code></p></td>
<td><p>optional</p></td>
<td><p>The space that is consumed by the cache on disk. The value is returned with a unit abbreviation (<code>k</code>: kilobytes, <code>m</code>: megabytes, <code>g</code>: gigabytes). Only set for disk caches.</p></td>
</tr>
</tbody>
</table>

### GerritInfo

The `GerritInfo` entity contains information about Gerrit configuration
from the [gerrit](config-gerrit.html#gerrit) section.

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
<td><p><code>all_projects_name</code></p></td>
<td></td>
<td><p>Name of the <a href="config-gerrit.html#gerrit.allProjects">root project</a>.</p></td>
</tr>
<tr class="even">
<td><p><code>all_users_name</code></p></td>
<td></td>
<td><p>Name of the <a href="config-gerrit.html#gerrit.allUsers">project in which meta data of all users is stored</a>.</p></td>
</tr>
<tr class="odd">
<td><p><code>doc_search</code></p></td>
<td></td>
<td><p>Whether documentation search is available.</p></td>
</tr>
<tr class="even">
<td><p><code>doc_url</code></p></td>
<td><p>optional</p></td>
<td><p>Custom base URL where Gerrit server documentation is located. (Documentation may still be available at /Documentation relative to the Gerrit base path even if this value is unset.)</p></td>
</tr>
<tr class="odd">
<td><p><code>edit_gpg_keys</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether to enable the web UI for editing GPG keys.</p></td>
</tr>
<tr class="even">
<td><p><code>report_bug_url</code></p></td>
<td><p>optional</p></td>
<td><p><a href="config-gerrit.html#gerrit.reportBugUrl">URL to report bugs</a>.</p></td>
</tr>
<tr class="odd">
<td><p><code>report_bug_text</code></p></td>
<td><p>optional, not set if default</p></td>
<td><p><a href="config-gerrit.html#gerrit.reportBugText">Display text for report bugs link</a>.</p></td>
</tr>
<tr class="even">
<td><p><code>web_uis</code></p></td>
<td></td>
<td><p>List of web UIs supported by the HTTP server. Possible values are <code>GWT</code> and <code>POLYGERRIT</code>.</p></td>
</tr>
</tbody>
</table>

### HitRatioInfo

The `HitRatioInfo` entity contains information about the hit ratio of a
cache.

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
<td><p><code>mem</code></p></td>
<td></td>
<td><p>Hit ratio for cache entries that are held in memory (0 &lt;= value &lt;= 100).</p></td>
</tr>
<tr class="even">
<td><p><code>disk</code></p></td>
<td><p>optional</p></td>
<td><p>Hit ratio for cache entries that are held on disk (0 &lt;= value &lt;= 100). Only set for disk caches.</p></td>
</tr>
</tbody>
</table>

### JvmSummaryInfo

The `JvmSummaryInfo` entity contains information about the JVM.

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
<td><p><code>vm_vendor</code></p></td>
<td></td>
<td><p>The vendor of the virtual machine.</p></td>
</tr>
<tr class="even">
<td><p><code>vm_name</code></p></td>
<td></td>
<td><p>The name of the virtual machine.</p></td>
</tr>
<tr class="odd">
<td><p><code>vm_version</code></p></td>
<td></td>
<td><p>The version of the virtual machine.</p></td>
</tr>
<tr class="even">
<td><p><code>os_name</code></p></td>
<td></td>
<td><p>The name of the operating system.</p></td>
</tr>
<tr class="odd">
<td><p><code>os_version</code></p></td>
<td></td>
<td><p>The version of the operating system.</p></td>
</tr>
<tr class="even">
<td><p><code>os_arch</code></p></td>
<td></td>
<td><p>The architecture of the operating system.</p></td>
</tr>
<tr class="odd">
<td><p><code>user</code></p></td>
<td></td>
<td><p>The user that is running Gerrit.</p></td>
</tr>
<tr class="even">
<td><p><code>host</code></p></td>
<td><p>optional</p></td>
<td><p>The host on which Gerrit is running.</p></td>
</tr>
<tr class="odd">
<td><p><code>current_working_directory</code></p></td>
<td></td>
<td><p>The current working directory.</p></td>
</tr>
<tr class="even">
<td><p><code>site</code></p></td>
<td></td>
<td><p>The path to the review site.</p></td>
</tr>
</tbody>
</table>

### MemSummaryInfo

The `MemSummaryInfo` entity contains information about the current
memory usage.

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
<td><p><code>total</code></p></td>
<td></td>
<td><p>The total size of the memory. The value is returned with a unit abbreviation (<code>k</code>: kilobytes, <code>m</code>: megabytes, <code>g</code>: gigabytes).</p></td>
</tr>
<tr class="even">
<td><p><code>used</code></p></td>
<td></td>
<td><p>The size of used memory. The value is returned with a unit abbreviation (<code>k</code>: kilobytes, <code>m</code>: megabytes, <code>g</code>: gigabytes).</p></td>
</tr>
<tr class="odd">
<td><p><code>free</code></p></td>
<td></td>
<td><p>The size of free memory. The value is returned with a unit abbreviation (<code>k</code>: kilobytes, <code>m</code>: megabytes, <code>g</code>: gigabytes).</p></td>
</tr>
<tr class="even">
<td><p><code>buffers</code></p></td>
<td></td>
<td><p>The size of memory used for JGit buffers. The value is returned with a unit abbreviation (<code>k</code>: kilobytes, <code>m</code>: megabytes, <code>g</code>: gigabytes).</p></td>
</tr>
<tr class="odd">
<td><p><code>max</code></p></td>
<td></td>
<td><p>The maximal memory size. The value is returned with a unit abbreviation (<code>k</code>: kilobytes, <code>m</code>: megabytes, <code>g</code>: gigabytes).</p></td>
</tr>
<tr class="even">
<td><p><code>open_files</code></p></td>
<td><p>optional</p></td>
<td><p>The number of open files.</p></td>
</tr>
</tbody>
</table>

### PluginConfigInfo

The `PluginConfigInfo` entity contains information about Gerrit
extensions by plugins.

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
<td><p><code>has_avatars</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether an avatar provider is registered.</p></td>
</tr>
</tbody>
</table>

### ReceiveInfo

The `ReceiveInfo` entity contains information about the configuration of
git-receive-pack behavior on the server.

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
<td><p><code>enableSignedPush</code></p></td>
<td><p>optional</p></td>
<td><p>Whether signed push validation support is enabled on the server; see the <a href="config-gerrit.html#receive.certNonceSeed">global configuration</a> for details.</p></td>
</tr>
</tbody>
</table>

### ServerInfo

The `ServerInfo` entity contains information about the configuration of
the Gerrit server.

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
<td><p><code>accounts</code></p></td>
<td></td>
<td><p>Information about the configuration from the <a href="config-gerrit.html#accounts">accounts</a> section as <a href="#accounts-config-info">AccountsConfigInfo</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>auth</code></p></td>
<td></td>
<td><p>Information about the authentication configuration as <a href="#auth-info">AuthInfo</a> entity.</p></td>
</tr>
<tr class="odd">
<td><p><code>change</code></p></td>
<td></td>
<td><p>Information about the configuration from the <a href="config-gerrit.html#change">change</a> section as <a href="#change-config-info">ChangeConfigInfo</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>download</code></p></td>
<td></td>
<td><p>Information about the configured download options as <a href="#download-info">DownloadInfo</a> entity. information about Gerrit</p></td>
</tr>
<tr class="odd">
<td><p><code>gerrit</code></p></td>
<td></td>
<td><p>Information about the configuration from the <a href="config-gerrit.html#gerrit">gerrit</a> section as <a href="#gerrit-info">GerritInfo</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>note_db_enabled</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the NoteDb storage backend is fully enabled.</p></td>
</tr>
<tr class="odd">
<td><p>`plugin `</p></td>
<td></td>
<td><p>Information about Gerrit extensions by plugins as <a href="#plugin-config-info">PluginConfigInfo</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>receive</code></p></td>
<td><p>optional</p></td>
<td><p>Information about the receive-pack configuration as a <a href="#receive-info">ReceiveInfo</a> entity.</p></td>
</tr>
<tr class="odd">
<td><p><code>sshd</code></p></td>
<td><p>optional</p></td>
<td><p>Information about the configuration from the <a href="config-gerrit.html#sshd">sshd</a> section as <a href="#sshd-info">SshdInfo</a> entity. Not set if SSHD is disabled.</p></td>
</tr>
<tr class="even">
<td><p><code>suggest</code></p></td>
<td></td>
<td><p>Information about the configuration from the <a href="config-gerrit.html#suggest">suggest</a> section as <a href="#suggest-info">SuggestInfo</a> entity.</p></td>
</tr>
<tr class="odd">
<td><p><code>url_aliases</code></p></td>
<td><p>optional</p></td>
<td><p>A map of URL aliases, where a regular expression for an URL token is mapped to a target URL token. The target URL token can contain placeholders for the groups matched by the regular expression: <code>$1</code> for the first matched group, <code>$2</code> for the second matched group, etc.</p></td>
</tr>
<tr class="even">
<td><p><code>user</code></p></td>
<td></td>
<td><p>Information about the configuration from the <a href="config-gerrit.html#user">user</a> section as <a href="#user-config-info">UserConfigInfo</a> entity.</p></td>
</tr>
<tr class="odd">
<td><p><code>default_theme</code></p></td>
<td><p>optional</p></td>
<td><p>URL to a default PolyGerrit UI theme plugin, if available. Located in <code>/static/gerrit-theme.html</code> by default.</p></td>
</tr>
</tbody>
</table>

### SshdInfo

The `SshdInfo` entity contains information about Gerrit configuration
from the [sshd](config-gerrit.html#sshd) section.

This entity doesn’t contain any data, but the presence of this (empty)
entity in the [ServerInfo](#server-info) entity means that SSHD is
enabled on the server.

### SuggestInfo

The `SuggestInfo` entity contains information about Gerrit configuration
from the [suggest](config-gerrit.html#suggest) section.

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
<td><p><code>from</code></p></td>
<td><p>The <a href="config-gerrit.html#suggest.from">number of characters</a> that a user must have typed before suggestions are provided.</p></td>
</tr>
</tbody>
</table>

### SummaryInfo

The `SummaryInfo` entity contains information about the current state of
the server.

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
<td><p><code>task_summary</code></p></td>
<td></td>
<td><p>Summary about current tasks as a <a href="#task-summary-info">TaskSummaryInfo</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>mem_summary</code></p></td>
<td></td>
<td><p>Summary about current memory usage as a <a href="#mem-summary-info">MemSummaryInfo</a> entity.</p></td>
</tr>
<tr class="odd">
<td><p><code>thread_summary</code></p></td>
<td></td>
<td><p>Summary about current threads as a <a href="#thread-summary-info">ThreadSummaryInfo</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>jvm_summary</code></p></td>
<td><p>optional</p></td>
<td><p>Summary about the JVM <a href="#jvm-summary-info">JvmSummaryInfo</a> entity. Only set if the <code>jvm</code> option was set.</p></td>
</tr>
</tbody>
</table>

### TaskInfo

The `TaskInfo` entity contains information about a task in a background
work queue.

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
<td><p>The ID of the task.</p></td>
</tr>
<tr class="even">
<td><p><code>state</code></p></td>
<td></td>
<td><p>The state of the task, can be <code>DONE</code>, <code>CANCELLED</code>, <code>RUNNING</code>, <code>READY</code>, <code>SLEEPING</code> and <code>OTHER</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>start_time</code></p></td>
<td></td>
<td><p>The start time of the task.</p></td>
</tr>
<tr class="even">
<td><p><code>delay</code></p></td>
<td></td>
<td><p>The remaining delay of the task.</p></td>
</tr>
<tr class="odd">
<td><p><code>command</code></p></td>
<td></td>
<td><p>The command of the task.</p></td>
</tr>
<tr class="even">
<td><p><code>remote_name</code></p></td>
<td><p>optional</p></td>
<td><p>The remote name. May only be set for tasks that are associated with a project.</p></td>
</tr>
<tr class="odd">
<td><p><code>project</code></p></td>
<td><p>optional</p></td>
<td><p>The project the task is associated with.</p></td>
</tr>
</tbody>
</table>

### TaskSummaryInfo

The `TaskSummaryInfo` entity contains information about the current
tasks.

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
<td><p><code>total</code></p></td>
<td><p>optional</p></td>
<td><p>Total number of current tasks.</p></td>
</tr>
<tr class="even">
<td><p><code>running</code></p></td>
<td><p>optional</p></td>
<td><p>Number of currently running tasks.</p></td>
</tr>
<tr class="odd">
<td><p><code>ready</code></p></td>
<td><p>optional</p></td>
<td><p>Number of currently ready tasks.</p></td>
</tr>
<tr class="even">
<td><p><code>sleeping</code></p></td>
<td><p>optional</p></td>
<td><p>Number of currently sleeping tasks.</p></td>
</tr>
</tbody>
</table>

### ThreadSummaryInfo

The `ThreadSummaryInfo` entity contains information about the current
threads.

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
<td><p><code>cpus</code></p></td>
<td><p>The number of available processors.</p></td>
</tr>
<tr class="even">
<td><p><code>threads</code></p></td>
<td><p>The total number of current threads.</p></td>
</tr>
<tr class="odd">
<td><p><code>counts</code></p></td>
<td><p>Detailed thread counts as a map that maps a thread kind to a map that maps a thread state to the thread count. The thread kinds group the counts by threads that have the same name prefix (<code>H2</code>, <code>HTTP</code>, <code>IntraLineDiff</code>, <code>ReceiveCommits</code>, <code>SSH git-receive-pack</code>, <code>SSH git-upload-pack</code>, <code>SSH-Interactive-Worker</code>, <code>SSH-Stream-Worker</code>, <code>SshCommandStart</code>, <code>sshd-SshServer</code>). The counts for other threads are available under the thread kind <code>Other</code>. Counts for the following thread states can be included: <code>NEW</code>, <code>RUNNABLE</code>, <code>BLOCKED</code>, <code>WAITING</code>, <code>TIMED_WAITING</code> and <code>TERMINATED</code>.</p></td>
</tr>
</tbody>
</table>

### TopMenuEntryInfo

The `TopMenuEntryInfo` entity contains information about a top menu
entry.

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
<td><p>Name of the top menu entry.</p></td>
</tr>
<tr class="even">
<td><p><code>items</code></p></td>
<td><p>List of <a href="#top-menu-item-info">menu items</a>.</p></td>
</tr>
</tbody>
</table>

### TopMenuItemInfo

The `TopMenuItemInfo` entity contains information about a menu item in a
top menu entry.

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
<td><p><code>url</code></p></td>
<td></td>
<td><p>The URL of the menu item link.</p></td>
</tr>
<tr class="even">
<td><p><code>name</code></p></td>
<td></td>
<td><p>The name of the menu item.</p></td>
</tr>
<tr class="odd">
<td><p><code>target</code></p></td>
<td></td>
<td><p>Target attribute of the menu item link.</p></td>
</tr>
<tr class="even">
<td><p><code>id</code></p></td>
<td><p>optional</p></td>
<td><p>The <code>id</code> attribute of the menu item link.</p></td>
</tr>
</tbody>
</table>

### UserConfigInfo

The `UserConfigInfo` entity contains information about Gerrit
configuration from the [user](config-gerrit.html#user) section.

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
<td><p><code>anonymous_coward_name</code></p></td>
<td><p><a href="config-gerrit.html#user.anonymousCoward">Username</a> that is displayed in the Gerrit Web UI and in e-mail notifications if the full name of the user is not set.</p></td>
</tr>
</tbody>
</table>

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

