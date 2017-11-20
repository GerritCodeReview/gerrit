---
title: " Gerrit Code Review - /access/ REST API"
sidebar: restapi_sidebar
permalink: rest-api-access.html
---
This page describes the access rights related REST endpoints. Please
also take note of the general information on the [REST
API](rest-api.html).

## Access Rights Endpoints

### List Access Rights

*GET
/access/?project=[{project-name}](rest-api-projects.html#project-name)*

Lists the access rights for projects. The projects for which the access
rights should be returned must be specified as `project` options. The
`project` can be specified multiple times.

As result a map is returned that maps the project name to
[ProjectAccessInfo](#project-access-info) entities.

The entries in the map are sorted by project name.

**Request.**

``` 
  GET /access/?project=MyProject&project=All-Projects HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "All-Projects": {
      "revision": "edd453d18e08640e67a8c9a150cec998ed0ac9aa",
      "local": {
        "GLOBAL_CAPABILITIES": {
          "permissions": {
            "priority": {
              "rules": {
                "15bfcd8a6de1a69c50b30cedcdcc951c15703152": {
                  "action": "BATCH"
                }
              }
            },
            "streamEvents": {
              "rules": {
                "15bfcd8a6de1a69c50b30cedcdcc951c15703152": {
                  "action": "ALLOW"
                }
              }
            },
            "administrateServer": {
              "rules": {
                "53a4f647a89ea57992571187d8025f830625192a": {
                  "action": "ALLOW"
                }
              }
            }
          }
        },
        "refs/meta/config": {
          "permissions": {
            "submit": {
              "rules": {
                "53a4f647a89ea57992571187d8025f830625192a": {
                  "action": "ALLOW"
                },
                "global:Project-Owners": {
                  "action": "ALLOW"
                }
              }
            },
            "label-Code-Review": {
              "label": "Code-Review",
              "rules": {
                "53a4f647a89ea57992571187d8025f830625192a": {
                  "action": "ALLOW",
                  "min": -2,
                  "max": 2
                },
                "global:Project-Owners": {
                  "action": "ALLOW",
                  "min": -2,
                  "max": 2
                }
              }
            },
            "read": {
              "exclusive": true,
              "rules": {
                "53a4f647a89ea57992571187d8025f830625192a": {
                  "action": "ALLOW"
                },
                "global:Project-Owners": {
                  "action": "ALLOW"
                }
              }
            },
            "push": {
              "rules": {
                "53a4f647a89ea57992571187d8025f830625192a": {
                  "action": "ALLOW"
                },
                "global:Project-Owners": {
                  "action": "ALLOW"
                }
              }
            }
          }
        },
        "refs/for/refs/*": {
          "permissions": {
            "pushMerge": {
              "rules": {
                "global:Registered-Users": {
                  "action": "ALLOW"
                }
              }
            },
            "push": {
              "rules": {
                "global:Registered-Users": {
                  "action": "ALLOW"
                }
              }
            }
          }
        },
        "refs/tags/*": {
          "permissions": {
            "createSignedTag": {
              "rules": {
                "53a4f647a89ea57992571187d8025f830625192a": {
                  "action": "ALLOW"
                },
                "global:Project-Owners": {
                  "action": "ALLOW"
                }
              }
            },
            "createTag": {
              "rules": {
                "53a4f647a89ea57992571187d8025f830625192a": {
                  "action": "ALLOW"
                },
                "global:Project-Owners": {
                  "action": "ALLOW"
                }
              }
            }
          }
        },
        "refs/heads/*": {
          "permissions": {
            "forgeCommitter": {
              "rules": {
                "53a4f647a89ea57992571187d8025f830625192a": {
                  "action": "ALLOW"
                },
                "global:Project-Owners": {
                  "action": "ALLOW"
                }
              }
            },
            "forgeAuthor": {
              "rules": {
                "global:Registered-Users": {
                  "action": "ALLOW"
                }
              }
            },
            "submit": {
              "rules": {
                "53a4f647a89ea57992571187d8025f830625192a": {
                  "action": "ALLOW"
                },
                "global:Project-Owners": {
                  "action": "ALLOW"
                }
              }
            },
            "editTopicName": {
              "rules": {
                "53a4f647a89ea57992571187d8025f830625192a": {
                  "action": "ALLOW",
                  "force": true
                },
                "global:Project-Owners": {
                  "action": "ALLOW",
                  "force": true
                }
              }
            },
            "label-Code-Review": {
              "label": "Code-Review",
              "rules": {
                "global:Registered-Users": {
                  "action": "ALLOW",
                  "min": -1,
                  "max": 1
                },
                "53a4f647a89ea57992571187d8025f830625192a": {
                  "action": "ALLOW",
                  "min": -2,
                  "max": 2
                },
                "global:Project-Owners": {
                  "action": "ALLOW",
                  "min": -2,
                  "max": 2
                }
              }
            },
            "create": {
              "rules": {
                "53a4f647a89ea57992571187d8025f830625192a": {
                  "action": "ALLOW"
                },
                "global:Project-Owners": {
                  "action": "ALLOW"
                }
              }
            },
            "push": {
              "rules": {
                "53a4f647a89ea57992571187d8025f830625192a": {
                  "action": "ALLOW"
                },
                "global:Project-Owners": {
                  "action": "ALLOW"
                }
              }
            }
          }
        },
        "refs/*": {
          "permissions": {
            "read": {
              "rules": {
                "global:Anonymous-Users": {
                  "action": "ALLOW"
                },
                "53a4f647a89ea57992571187d8025f830625192a": {
                  "action": "ALLOW"
                }
              }
            }
          }
        }
      },
      "is_owner": true,
      "owner_of": [
        "GLOBAL_CAPABILITIES",
        "refs/meta/config",
        "refs/for/refs/*",
        "refs/tags/*",
        "refs/heads/*",
        "refs/*"
      ],
      "can_upload": true,
      "can_add": true,
      "config_visible": true,
      "groups": {
         "53a4f647a89ea57992571187d8025f830625192a": {
           "url": "#/admin/groups/uuid-53a4f647a89ea57992571187d8025f830625192a",
           "options": {},
           "description": "Gerrit Site Administrators",
           "group_id": 1,
           "owner": "Administrators",
           "owner_id": "53a4f647a89ea57992571187d8025f830625192a",
           "created_on": "2009-06-08 23:31:00.000000000",
           "name": "Administrators"
         },
         "global:Registered-Users": {
           "options": {},
           "name": "Registered Users"
         },
         "global:Project-Owners": {
           "options": {},
           "name": "Project Owners"
         },
         "15bfcd8a6de1a69c50b30cedcdcc951c15703152": {
           "url": "#/admin/groups/uuid-15bfcd8a6de1a69c50b30cedcdcc951c15703152",
           "options": {},
           "description": "Users who perform batch actions on Gerrit",
           "group_id": 2,
           "owner": "Administrators",
           "owner_id": "53a4f647a89ea57992571187d8025f830625192a",
           "created_on": "2009-06-08 23:31:00.000000000",
           "name": "Non-Interactive Users"
         },
         "global:Anonymous-Users": {
           "options": {},
           "name": "Anonymous Users"
         }
      }
    },
    "MyProject": {
      "revision": "61157ed63e14d261b6dca40650472a9b0bd88474",
      "inherits_from": {
        "id": "All-Projects",
        "name": "All-Projects",
        "description": "Access inherited by all other projects."
      },
      "local": {},
      "is_owner": true,
      "owner_of": [
        "refs/*"
      ],
      "can_upload": true,
      "can_add": true,
      "config_visible": true
    }
  }
```

## JSON Entities

### AccessSectionInfo

The `AccessSectionInfo` describes the access rights that are assigned on
a ref.

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
<td><p><code>permissions</code></p></td>
<td></td>
<td><p>The permissions assigned on the ref of this access section as a map that maps the permission names to <a href="#permission-info">PermissionInfo</a> entities.</p></td>
</tr>
</tbody>
</table>

### PermissionInfo

The `PermissionInfo` entity contains information about an assigned
permission.

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
<td><p><code>label</code></p></td>
<td><p>optional</p></td>
<td><p>The name of the label. Not set if it’s not a label permission.</p></td>
</tr>
<tr class="even">
<td><p><code>exclusive</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether this permission is assigned exclusively.</p></td>
</tr>
<tr class="odd">
<td><p><code>rules</code></p></td>
<td></td>
<td><p>The rules assigned for this permission as a map that maps the UUIDs of the groups for which the permission are assigned to <a href="#permission-info">PermissionRuleInfo</a> entities.</p></td>
</tr>
</tbody>
</table>

### PermissionRuleInfo

The `PermissionRuleInfo` entity contains information about a permission
rule that is assigned to group.

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
<td><p><code>action</code></p></td>
<td></td>
<td><p>The action of this rule. For normal permissions this can be <code>ALLOW</code>, <code>DENY</code> or <code>BLOCK</code>. Special values for global capabilities are <code>INTERACTIVE</code> and <code>BATCH</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>force</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the force flag is set.</p></td>
</tr>
<tr class="odd">
<td><p><code>min</code></p></td>
<td><p>not set if range is empty (from <code>0</code> to <code>0</code>) or not set</p></td>
<td><p>The min value of the permission range.</p></td>
</tr>
<tr class="even">
<td><p><code>max</code></p></td>
<td><p>not set if range is empty (from <code>0</code> to <code>0</code>) or not set</p></td>
<td><p>The max value of the permission range.</p></td>
</tr>
</tbody>
</table>

### ProjectAccessInfo

The `ProjectAccessInfo` entity contains information about the access
rights for a project.

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
<td><p><code>revision</code></p></td>
<td></td>
<td><p>The revision of the <code>refs/meta/config</code> branch from which the access rights were loaded.</p></td>
</tr>
<tr class="even">
<td><p><code>inherits_from</code></p></td>
<td><p>not set for the <code>All-Project</code> project</p></td>
<td><p>The parent project from which permissions are inherited as a <a href="rest-api-projects.html#project-info">ProjectInfo</a> entity.</p></td>
</tr>
<tr class="odd">
<td><p><code>local</code></p></td>
<td></td>
<td><p>The local access rights of the project as a map that maps the refs to <a href="#access-section-info">AccessSectionInfo</a> entities.</p></td>
</tr>
<tr class="even">
<td><p><code>is_owner</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the calling user owns this project.</p></td>
</tr>
<tr class="odd">
<td><p><code>owner_of</code></p></td>
<td></td>
<td><p>The list of refs owned by the calling user.</p></td>
</tr>
<tr class="even">
<td><p><code>can_upload</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the calling user can upload to any ref.</p></td>
</tr>
<tr class="odd">
<td><p><code>can_add</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the calling user can add any ref.</p></td>
</tr>
<tr class="even">
<td><p><code>config_visible</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the calling user can see the <code>refs/meta/config</code> branch of the project.</p></td>
</tr>
</tbody>
</table>

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

