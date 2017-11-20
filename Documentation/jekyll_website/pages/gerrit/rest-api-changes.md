---
title: " Gerrit Code Review - /changes/ REST API"
sidebar: restapi_sidebar
permalink: rest-api-changes.html
---
This page describes the change related REST endpoints. Please also take
note of the general information on the [REST API](rest-api.html).

## Change Endpoints

### Create Change

*POST /changes/*

The change input [ChangeInput](#change-input) entity must be provided in
the request body.

**Request.**

``` 
  POST /changes/ HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "project" : "myProject",
    "subject" : "Let's support 100% Gerrit workflow direct in browser",
    "branch" : "master",
    "topic" : "create-change-in-browser",
    "status" : "NEW"
  }
```

As response a [ChangeInfo](#change-info) entity is returned that
describes the resulting change.

**Response.**

``` 
  HTTP/1.1 201 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9941",
    "project": "myProject",
    "branch": "master",
    "topic": "create-change-in-browser",
    "change_id": "I8473b95934b5732ac55d26311a706c9c2bde9941",
    "subject": "Let's support 100% Gerrit workflow direct in browser",
    "status": "NEW",
    "created": "2014-05-05 07:15:44.639000000",
    "updated": "2014-05-05 07:15:44.639000000",
    "mergeable": true,
    "insertions": 0,
    "deletions": 0,
    "_number": 4711,
    "owner": {
      "name": "John Doe"
    }
  }
```

### Query Changes

*GET /changes/*

Queries changes visible to the caller. The [query
string](user-search.html#_search_operators) must be provided by the `q`
parameter. The `n` parameter can be used to limit the returned results.

As result a list of [ChangeInfo](#change-info) entries is returned. The
change output is sorted by the last update time, most recently updated
to oldest updated.

Query for open changes of watched projects:

**Request.**

``` 
  GET /changes/?q=status:open+is:watched&n=2 HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "id": "demo~master~Idaf5e098d70898b7119f6f4af5a6c13343d64b57",
      "project": "demo",
      "branch": "master",
      "change_id": "Idaf5e098d70898b7119f6f4af5a6c13343d64b57",
      "subject": "One change",
      "status": "NEW",
      "created": "2012-07-17 07:18:30.854000000",
      "updated": "2012-07-17 07:19:27.766000000",
      "mergeable": true,
      "insertions": 26,
      "deletions": 10,
      "_number": 1756,
      "owner": {
        "name": "John Doe"
      },
    },
    {
      "id": "demo~master~I09c8041b5867d5b33170316e2abc34b79bbb8501",
      "project": "demo",
      "branch": "master",
      "change_id": "I09c8041b5867d5b33170316e2abc34b79bbb8501",
      "subject": "Another change",
      "status": "NEW",
      "created": "2012-07-17 07:18:30.884000000",
      "updated": "2012-07-17 07:18:30.885000000",
      "mergeable": true,
      "insertions": 12,
      "deletions": 18,
      "_number": 1757,
      "owner": {
        "name": "John Doe"
      },
      "_more_changes": true
    }
  ]
```

If the number of changes matching the query exceeds either the internal
limit or a supplied `n` query parameter, the last change object has a
`_more_changes: true` JSON field set.

The `S` or `start` query parameter can be supplied to skip a number of
changes from the list.

Clients are allowed to specify more than one query by setting the `q`
parameter multiple times. In this case the result is an array of arrays,
one per query in the same order the queries were given in.

get::/changes/?q=status:open+is:watched\&n=25

Query that retrieves changes for a user’s
dashboard:

**Request.**

``` 
  GET /changes/?q=is:open+owner:self&q=is:open+reviewer:self+-owner:self&q=is:closed+owner:self+limit:5&o=LABELS HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    [
      {
        "id": "demo~master~Idaf5e098d70898b7119f6f4af5a6c13343d64b57",
        "project": "demo",
        "branch": "master",
        "change_id": "Idaf5e098d70898b7119f6f4af5a6c13343d64b57",
        "subject": "One change",
        "status": "NEW",
        "created": "2012-07-17 07:18:30.854000000",
        "updated": "2012-07-17 07:19:27.766000000",
        "mergeable": true,
        "insertions": 4,
        "deletions": 7,
        "_number": 1756,
        "owner": {
          "name": "John Doe"
        },
        "labels": {
          "Verified": {},
          "Code-Review": {}
        }
      }
    ],
    [],
    []
  ]
```

get::/changes/?q=is:open+owner:self\&q=is:open+reviewer:self+-owner:self\&q=is:closed+owner:self+limit:5\&o=LABELS

Additional fields can be obtained by adding `o` parameters, each option
requires more database lookups and slows down the query response time to
the client so they are generally disabled by default. Optional fields
are:

  - `LABELS`: a summary of each label required for submit, and approvers
    that have granted (or rejected) with that label.

<!-- end list -->

  - `DETAILED_LABELS`: detailed label information, including numeric
    values of all existing approvals, recognized label values, values
    permitted to be set by the current user, all reviewers by state, and
    reviewers that may be removed by the current user.

<!-- end list -->

  - `CURRENT_REVISION`: describe the current revision (patch set) of the
    change, including the commit SHA-1 and URLs to fetch from.

<!-- end list -->

  - `ALL_REVISIONS`: describe all revisions, not just current.

<!-- end list -->

  - `DOWNLOAD_COMMANDS`: include the `commands` field in the
    [FetchInfo](#fetch-info) for revisions. Only valid when the
    `CURRENT_REVISION` or `ALL_REVISIONS` option is selected.

<!-- end list -->

  - `CURRENT_COMMIT`: parse and output all header fields from the commit
    object, including message. Only valid when the `CURRENT_REVISION` or
    `ALL_REVISIONS` option is selected.

<!-- end list -->

  - `ALL_COMMITS`: parse and output all header fields from the output
    revisions. If only `CURRENT_REVISION` was requested then only the
    current revision’s commit data will be output.

<!-- end list -->

  - `CURRENT_FILES`: list files modified by the commit and magic files,
    including basic line counts inserted/deleted per file. Only valid
    when the `CURRENT_REVISION` or `ALL_REVISIONS` option is selected.

<!-- end list -->

  - `ALL_FILES`: list files modified by the commit and magic files,
    including basic line counts inserted/deleted per file. If only the
    `CURRENT_REVISION` was requested then only that commit’s modified
    files will be output.

<!-- end list -->

  - `DETAILED_ACCOUNTS`: include `_account_id`, `email` and `username`
    fields when referencing accounts.

<!-- end list -->

  - `REVIEWER_UPDATES`: include updates to reviewers set as
    [ReviewerUpdateInfo](#review-update-info) entities.

<!-- end list -->

  - `MESSAGES`: include messages associated with the change.

<!-- end list -->

  - `CURRENT_ACTIONS`: include information on available actions for the
    change and its current revision. Ignored if the caller is not
    authenticated.

<!-- end list -->

  - `CHANGE_ACTIONS`: include information on available change actions
    for the change. Ignored if the caller is not authenticated.

<!-- end list -->

  - `REVIEWED`: include the `reviewed` field if all of the following are
    true:
    
      - the change is open
    
      - the caller is authenticated
    
      - the caller has commented on the change more recently than the
        last update from the change owner, i.e. this change would show
        up in the results of
        [reviewedby:self](user-search.html#reviewedby).

<!-- end list -->

  - `SUBMITTABLE`: include the `submittable` field in
    [ChangeInfo](#change-info), which can be used to tell if the change
    is reviewed and ready for submit.

<!-- end list -->

  - `WEB_LINKS`: include the `web_links` field in
    [CommitInfo](#commit-info), therefore only valid in combination with
    `CURRENT_COMMIT` or `ALL_COMMITS`.

<!-- end list -->

  - `CHECK`: include potential problems with the change.

<!-- end list -->

  - `COMMIT_FOOTERS`: include the full commit message with
    Gerrit-specific commit footers in the
    [RevisionInfo](#revision-info).

<!-- end list -->

  - `PUSH_CERTIFICATES`: include push certificate information in the
    [RevisionInfo](#revision-info). Ignored if signed push is not
    [enabled](config-gerrit.html#receive.enableSignedPush) on the
    server.

<!-- end list -->

  - `TRACKING_IDS`: include references to external tracking systems as
    [TrackingIdInfo](#tracking-id-info).

**Request.**

``` 
  GET /changes/?q=97&o=CURRENT_REVISION&o=CURRENT_COMMIT&o=CURRENT_FILES&o=DOWNLOAD_COMMANDS HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "id": "gerrit~master~I7ea46d2e2ee5c64c0d807677859cfb7d90b8966a",
      "project": "gerrit",
      "branch": "master",
      "change_id": "I7ea46d2e2ee5c64c0d807677859cfb7d90b8966a",
      "subject": "Use an EventBus to manage star icons",
      "status": "NEW",
      "created": "2012-04-25 00:52:25.580000000",
      "updated": "2012-04-25 00:52:25.586000000",
      "mergeable": true,
      "insertions": 16,
      "deletions": 7,
      "_number": 97,
      "owner": {
        "name": "Shawn Pearce"
      },
      "current_revision": "184ebe53805e102605d11f6b143486d15c23a09c",
      "revisions": {
        "184ebe53805e102605d11f6b143486d15c23a09c": {
          "kind": "REWORK",
          "_number": 1,
          "ref": "refs/changes/97/97/1",
          "fetch": {
            "git": {
              "url": "git://localhost/gerrit",
              "ref": "refs/changes/97/97/1",
              "commands": {
                "Checkout": "git fetch git://localhost/gerrit refs/changes/97/97/1 \u0026\u0026 git checkout FETCH_HEAD",
                "Cherry-Pick": "git fetch git://localhost/gerrit refs/changes/97/97/1 \u0026\u0026 git cherry-pick FETCH_HEAD",
                "Format-Patch": "git fetch git://localhost/gerrit refs/changes/97/97/1 \u0026\u0026 git format-patch -1 --stdout FETCH_HEAD",
                "Pull": "git pull git://localhost/gerrit refs/changes/97/97/1"
              }
            },
            "http": {
              "url": "http://myuser@127.0.0.1:8080/gerrit",
              "ref": "refs/changes/97/97/1",
              "commands": {
                "Checkout": "git fetch http://myuser@127.0.0.1:8080/gerrit refs/changes/97/97/1 \u0026\u0026 git checkout FETCH_HEAD",
                "Cherry-Pick": "git fetch http://myuser@127.0.0.1:8080/gerrit refs/changes/97/97/1 \u0026\u0026 git cherry-pick FETCH_HEAD",
                "Format-Patch": "git fetch http://myuser@127.0.0.1:8080/gerrit refs/changes/97/97/1 \u0026\u0026 git format-patch -1 --stdout FETCH_HEAD",
                "Pull": "git pull http://myuser@127.0.0.1:8080/gerrit refs/changes/97/97/1"
              }
            },
            "ssh": {
              "url": "ssh://myuser@*:29418/gerrit",
              "ref": "refs/changes/97/97/1",
              "commands": {
                "Checkout": "git fetch ssh://myuser@*:29418/gerrit refs/changes/97/97/1 \u0026\u0026 git checkout FETCH_HEAD",
                "Cherry-Pick": "git fetch ssh://myuser@*:29418/gerrit refs/changes/97/97/1 \u0026\u0026 git cherry-pick FETCH_HEAD",
                "Format-Patch": "git fetch ssh://myuser@*:29418/gerrit refs/changes/97/97/1 \u0026\u0026 git format-patch -1 --stdout FETCH_HEAD",
                "Pull": "git pull ssh://myuser@*:29418/gerrit refs/changes/97/97/1"
              }
            }
          },
          "commit": {
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
          },
          "files": {
            "gerrit-gwtui/src/main/java/com/google/gerrit/client/changes/ChangeCache.java": {
              "lines_deleted": 8,
              "size_delta": -412,
              "size": 7782
            },
            "gerrit-gwtui/src/main/java/com/google/gerrit/client/changes/ChangeDetailCache.java": {
              "lines_inserted": 1,
              "size_delta": 23,
              "size": 6762
            },
            "gerrit-gwtui/src/main/java/com/google/gerrit/client/changes/ChangeScreen.java": {
              "lines_inserted": 11,
              "lines_deleted": 19,
              "size_delta": -298,
              "size": 47023
            },
            "gerrit-gwtui/src/main/java/com/google/gerrit/client/changes/ChangeTable.java": {
              "lines_inserted": 23,
              "lines_deleted": 20,
              "size_delta": 132,
              "size": 17727
            },
            "gerrit-gwtui/src/main/java/com/google/gerrit/client/changes/StarCache.java": {
              "status": "D",
              "lines_deleted": 139,
              "size_delta": -5512,
              "size": 13098
            },
            "gerrit-gwtui/src/main/java/com/google/gerrit/client/changes/StarredChanges.java": {
              "status": "A",
              "lines_inserted": 204,
              "size_delta": 8345,
              "size": 8345
            },
            "gerrit-gwtui/src/main/java/com/google/gerrit/client/ui/Screen.java": {
              "lines_deleted": 9,
              "size_delta": -343,
              "size": 5385
            }
          }
        }
      }
    }
  ]
```

### Get Change

*GET /changes/[{change-id}](#change-id)*

Retrieves a change.

Additional fields can be obtained by adding `o` parameters, each option
requires more database lookups and slows down the query response time to
the client so they are generally disabled by default. Fields are
described in [Query
Changes](#list-changes).

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940 HTTP/1.0
```

As response a [ChangeInfo](#change-info) entity is returned that
describes the change.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940",
    "project": "myProject",
    "branch": "master",
    "change_id": "I8473b95934b5732ac55d26311a706c9c2bde9940",
    "subject": "Implementing Feature X",
    "status": "NEW",
    "created": "2013-02-01 09:59:32.126000000",
    "updated": "2013-02-21 11:16:36.775000000",
    "mergeable": true,
    "insertions": 34,
    "deletions": 101,
    "_number": 3965,
    "owner": {
      "name": "John Doe"
    }
  }
```

### Get Change Detail

*GET /changes/[{change-id}](#change-id)/detail*

Retrieves a change with [labels](#labels), [detailed
labels](#detailed-labels), [detailed accounts](#detailed-accounts),
[reviewer updates](#reviewer-updates), and [messages](#messages).

Additional fields can be obtained by adding `o` parameters, each option
requires more database lookups and slows down the query response time to
the client so they are generally disabled by default. Fields are
described in [Query
Changes](#list-changes).

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/detail HTTP/1.0
```

As response a [ChangeInfo](#change-info) entity is returned that
describes the change. This response will contain all votes for each
label and include one combined vote. The combined label vote is
calculated in the following order (from highest to lowest): REJECTED \>
APPROVED \> DISLIKED \> RECOMMENDED.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940",
    "project": "myProject",
    "branch": "master",
    "change_id": "I8473b95934b5732ac55d26311a706c9c2bde9940",
    "subject": "Implementing Feature X",
    "status": "NEW",
    "created": "2013-02-01 09:59:32.126000000",
    "updated": "2013-02-21 11:16:36.775000000",
    "mergeable": true,
    "insertions": 126,
    "deletions": 11,
    "_number": 3965,
    "owner": {
      "_account_id": 1000096,
      "name": "John Doe",
      "email": "john.doe@example.com",
      "username": "jdoe"
    },
    "labels": {
      "Verified": {
        "all": [
          {
            "value": 0,
            "_account_id": 1000096,
            "name": "John Doe",
            "email": "john.doe@example.com",
            "username": "jdoe"
          },
          {
            "value": 0,
            "_account_id": 1000097,
            "name": "Jane Roe",
            "email": "jane.roe@example.com",
            "username": "jroe"
          }
        ],
        "values": {
          "-1": "Fails",
          " 0": "No score",
          "+1": "Verified"
        }
      },
      "Code-Review": {
        "disliked": {
          "_account_id": 1000096,
          "name": "John Doe",
          "email": "john.doe@example.com",
          "username": "jdoe"
        },
        "all": [
          {
            "value": -1,
            "_account_id": 1000096,
            "name": "John Doe",
            "email": "john.doe@example.com",
            "username": "jdoe"
          },
          {
            "value": 1,
            "_account_id": 1000097,
            "name": "Jane Roe",
            "email": "jane.roe@example.com",
            "username": "jroe"
          }
        ]
        "values": {
          "-2": "This shall not be merged",
          "-1": "I would prefer this is not merged as is",
          " 0": "No score",
          "+1": "Looks good to me, but someone else must approve",
          "+2": "Looks good to me, approved"
        }
      }
    },
    "permitted_labels": {
      "Verified": [
        "-1",
        " 0",
        "+1"
      ],
      "Code-Review": [
        "-2",
        "-1",
        " 0",
        "+1",
        "+2"
      ]
    },
    "removable_reviewers": [
      {
        "_account_id": 1000096,
        "name": "John Doe",
        "email": "john.doe@example.com",
        "username": "jdoe"
      },
      {
        "_account_id": 1000097,
        "name": "Jane Roe",
        "email": "jane.roe@example.com",
        "username": "jroe"
      }
    ],
    "reviewers": {
      "REVIEWER": [
        {
          "_account_id": 1000096,
          "name": "John Doe",
          "email": "john.doe@example.com",
          "username": "jdoe"
        },
        {
          "_account_id": 1000097,
          "name": "Jane Roe",
          "email": "jane.roe@example.com",
          "username": "jroe"
        }
      ]
    },
    "reviewer_updates": [
      {
        "state": "REVIEWER",
        "reviewer": {
          "_account_id": 1000096,
          "name": "John Doe",
          "email": "john.doe@example.com",
          "username": "jdoe"
        },
        "updated_by": {
          "_account_id": 1000096,
          "name": "John Doe",
          "email": "john.doe@example.com",
          "username": "jdoe"
        },
        "updated": "2016-07-21 20:12:39.000000000"
      },
      {
        "state": "REMOVED",
        "reviewer": {
          "_account_id": 1000096,
          "name": "John Doe",
          "email": "john.doe@example.com",
          "username": "jdoe"
        },
        "updated_by": {
          "_account_id": 1000096,
          "name": "John Doe",
          "email": "john.doe@example.com",
          "username": "jdoe"
        },
        "updated": "2016-07-21 20:12:33.000000000"
      },
      {
        "state": "CC",
        "reviewer": {
          "_account_id": 1000096,
          "name": "John Doe",
          "email": "john.doe@example.com",
          "username": "jdoe"
        },
        "updated_by": {
          "_account_id": 1000096,
          "name": "John Doe",
          "email": "john.doe@example.com",
          "username": "jdoe"
        },
        "updated": "2016-03-23 21:34:02.419000000",
      },
    ],
    "messages": [
      {
        "id": "YH-egE",
        "author": {
          "_account_id": 1000096,
          "name": "John Doe",
          "email": "john.doe@example.com",
          "username": "jdoe"
        },
        "date": "2013-03-23 21:34:02.419000000",
        "message": "Patch Set 1:\n\nThis is the first message.",
        "_revision_number": 1
      },
      {
        "id": "WEEdhU",
        "author": {
          "_account_id": 1000097,
          "name": "Jane Roe",
          "email": "jane.roe@example.com",
          "username": "jroe"
        },
        "date": "2013-03-23 21:36:52.332000000",
        "message": "Patch Set 1:\n\nThis is the second message.\n\nWith a line break.",
        "_revision_number": 1
      }
    ]
  }
```

### Create Merge Patch Set For Change

*POST /changes/[{change-id}](#change-id)/merge*

Update an existing change by using a
[MergePatchSetInput](#merge-patch-set-input) entity.

Gerrit will create a merge commit based on the information of
MergePatchSetInput and add a new patch set to the change corresponding
to the new merge
commit.

**Request.**

``` 
  POST /changes/test~master~Ic5466d107c5294414710935a8ef3b0180fb848dc/merge  HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "subject": "Merge dev_branch into master",
    "merge": {
      "source": "refs/12/1234/1"
    }
  }
```

As response a [ChangeInfo](#change-info) entity with current revision is
returned that describes the resulting change.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "test~master~Ic5466d107c5294414710935a8ef3b0180fb848dc",
    "project": "test",
    "branch": "master",
    "hashtags": [],
    "change_id": "Ic5466d107c5294414710935a8ef3b0180fb848dc",
    "subject": "Merge dev_branch into master",
    "status": "NEW",
    "created": "2016-09-23 18:08:53.238000000",
    "updated": "2016-09-23 18:09:25.934000000",
    "submit_type": "MERGE_IF_NECESSARY",
    "mergeable": true,
    "insertions": 5,
    "deletions": 0,
    "_number": 72,
    "owner": {
      "_account_id": 1000000
    },
    "current_revision": "27cc4558b5a3d3387dd11ee2df7a117e7e581822"
  }
```

### Set Commit Message

*PUT /changes/[{change-id}](#change-id)/message*

Creates a new patch set with a new commit message.

The new commit message must be provided in the request body inside a
[CommitMessageInput](#commit-message-input) entity and contain the
change ID footer if [Require
Change-Id](project-configuration.html#require-change-id) was
specified.

**Request.**

``` 
  PUT /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/message HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "message": "New Commit message \n\nChange-Id: I10394472cbd17dd12454f229e4f6de00b143a444\n"
  }
```

**Notifications.**

An email will be sent using the "newpatchset" template.

<table>
<colgroup>
<col width="50%" />
<col width="50%" />
</colgroup>
<thead>
<tr class="header">
<th>WIP State</th>
<th>Default</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p>Ready for review</p></td>
<td><p>owner, reviewers, CCs, stars, NEW_PATCHSETS watchers</p></td>
</tr>
<tr class="even">
<td><p>Work in progress</p></td>
<td><p>owner</p></td>
</tr>
</tbody>
</table>

### Get Topic

*GET /changes/[{change-id}](#change-id)/topic*

Retrieves the topic of a
change.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/topic HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  "Documentation"
```

If the change does not have a topic an empty string is returned.

### Set Topic

*PUT /changes/[{change-id}](#change-id)/topic*

Sets the topic of a change.

The new topic must be provided in the request body inside a
[TopicInput](#topic-input)
entity.

**Request.**

``` 
  PUT /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/topic HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "topic": "Documentation"
  }
```

As response the new topic is returned.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  "Documentation"
```

If the topic was deleted the response is "`204 No Content`".

### Delete Topic

*DELETE /changes/[{change-id}](#change-id)/topic*

Deletes the topic of a change.

Please note that some proxies prohibit request bodies for DELETE
requests. In this case, if you want to specify a commit message, use
[PUT](#set-topic) to delete the
topic.

**Request.**

``` 
  DELETE /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/topic HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

### Get Assignee

*GET /changes/[{change-id}](#change-id)/assignee*

Retrieves the account of the user assigned to a
change.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/assignee HTTP/1.0
```

As a response an [AccountInfo](rest-api-accounts.html#account-info)
entity describing the assigned account is returned.

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
    "username": "jdoe"
  }
```

If the change has no assignee the response is "`204 No Content`".

### Get Past Assignees

*GET /changes/[{change-id}](#change-id)/past\_assignees*

Returns a list of every user ever assigned to a change, in the order in
which they were first assigned.

\[NOTE\] Past assignees are only available when NoteDb is
enabled.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/past_assignees HTTP/1.0
```

As a response a list of
[AccountInfo](rest-api-accounts.html#account-info) entities is returned.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "_account_id": 1000051,
      "name": "Jane Doe",
      "email": "jane.doe@example.com",
      "username": "janed"
    },
    {
      "_account_id": 1000096,
      "name": "John Doe",
      "email": "john.doe@example.com",
      "username": "jdoe"
    }
  ]
```

### Set Assignee

*PUT /changes/[{change-id}](#change-id)/assignee*

Sets the assignee of a change.

The new assignee must be provided in the request body inside a
[AssigneeInput](#assignee-input)
entity.

**Request.**

``` 
  PUT /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/assignee HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "assignee": "jdoe"
  }
```

As a response an [AccountInfo](rest-api-accounts.html#account-info)
entity describing the assigned account is returned.

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
    "username": "jdoe"
  }
```

### Delete Assignee

*DELETE /changes/[{change-id}](#change-id)/assignee*

Deletes the assignee of a
change.

**Request.**

``` 
  DELETE /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/assignee HTTP/1.0
```

As a response an [AccountInfo](rest-api-accounts.html#account-info)
entity describing the account of the deleted assignee is returned.

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
    "username": "jdoe"
  }
```

If the change had no assignee the response is "`204 No Content`".

### Get Pure Revert

*GET /changes/[{change-id}](#change-id)/pure\_revert*

Check if the given change is a pure revert of the change it references
in `revertOf`. Optionally, the query parameter `o` can be passed in to
specify a commit (SHA1 in 40 digit hex representation) to check against.
It takes precedence over `revertOf`. If the change has no reference in
`revertOf`, the parameter is mandatory.

As response a [PureRevertInfo](#pure-revert-info) entity is
returned.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/pure_revert?o=247bccf56ae47634650bcc08b8aa784c3580ccas HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "is_pure_revert" : false
  }
```

### Abandon Change

*POST /changes/[{change-id}](#change-id)/abandon*

Abandons a change.

The request body does not need to include a
[AbandonInput](#abandon-input) entity if no review comment is
added.

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/abandon HTTP/1.0
```

As response a [ChangeInfo](#change-info) entity is returned that
describes the abandoned change.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940",
    "project": "myProject",
    "branch": "master",
    "change_id": "I8473b95934b5732ac55d26311a706c9c2bde9940",
    "subject": "Implementing Feature X",
    "status": "ABANDONED",
    "created": "2013-02-01 09:59:32.126000000",
    "updated": "2013-02-21 11:16:36.775000000",
    "mergeable": true,
    "insertions": 3,
    "deletions": 310,
    "_number": 3965,
    "owner": {
      "name": "John Doe"
    }
  }
```

If the change cannot be abandoned because the change state doesn’t allow
abandoning of the change, the response is "`409 Conflict`" and the error
message is contained in the response body.

**Response.**

``` 
  HTTP/1.1 409 Conflict
  Content-Disposition: attachment
  Content-Type: text/plain; charset=UTF-8

  change is merged
```

**Notifications.**

An email will be sent using the "abandon" template. The notify handling
is ALL. Notifications are suppressed on WIP changes that have never
started review.

<table>
<colgroup>
<col width="33%" />
<col width="66%" />
</colgroup>
<thead>
<tr class="header">
<th>WIP State</th>
<th>notify=ALL</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p>Ready for review</p></td>
<td><p>owner, reviewers, CCs, stars, ABANDONED_CHANGES watchers</p></td>
</tr>
<tr class="even">
<td><p>Work in progress</p></td>
<td><p>not sent</p></td>
</tr>
<tr class="odd">
<td><p>Reviewable WIP</p></td>
<td><p>owner, reviewers, CCs, stars, ABANDONED_CHANGES watchers</p></td>
</tr>
</tbody>
</table>

### Restore Change

*POST /changes/[{change-id}](#change-id)/restore*

Restores a change.

The request body does not need to include a
[RestoreInput](#restore-input) entity if no review comment is
added.

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/restore HTTP/1.0
```

As response a [ChangeInfo](#change-info) entity is returned that
describes the restored change.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940",
    "project": "myProject",
    "branch": "master",
    "change_id": "I8473b95934b5732ac55d26311a706c9c2bde9940",
    "subject": "Implementing Feature X",
    "status": "NEW",
    "created": "2013-02-01 09:59:32.126000000",
    "updated": "2013-02-21 11:16:36.775000000",
    "mergeable": true,
    "insertions": 2,
    "deletions": 13,
    "_number": 3965,
    "owner": {
      "name": "John Doe"
    }
  }
```

If the change cannot be restored because the change state doesn’t allow
restoring the change, the response is "`409 Conflict`" and the error
message is contained in the response body.

**Response.**

``` 
  HTTP/1.1 409 Conflict
  Content-Disposition: attachment
  Content-Type: text/plain; charset=UTF-8

  change is new
```

### Rebase Change

*POST /changes/[{change-id}](#change-id)/rebase*

Rebases a change.

Optionally, the parent revision can be changed to another patch set
through the [RebaseInput](#rebase-input)
entity.

**Request.**

``` 
  POST /changes/myProject~master~I3ea943139cb62e86071996f2480e58bf3eeb9dd2/rebase HTTP/1.0
  Content-Type: application/json;charset=UTF-8

  {
    "base" : "1234",
  }
```

As response a [ChangeInfo](#change-info) entity is returned that
describes the rebased change. Information about the current patch set is
included.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "myProject~master~I3ea943139cb62e86071996f2480e58bf3eeb9dd2",
    "project": "myProject",
    "branch": "master",
    "change_id": "I3ea943139cb62e86071996f2480e58bf3eeb9dd2",
    "subject": "Implement Feature X",
    "status": "NEW",
    "created": "2013-02-01 09:59:32.126000000",
    "updated": "2013-02-21 11:16:36.775000000",
    "mergeable": false,
    "insertions": 33,
    "deletions": 9,
    "_number": 4799,
    "owner": {
      "name": "John Doe"
    },
    "current_revision": "27cc4558b5a3d3387dd11ee2df7a117e7e581822",
    "revisions": {
      "27cc4558b5a3d3387dd11ee2df7a117e7e581822": {
        "kind": "REWORK",
        "_number": 2,
        "ref": "refs/changes/99/4799/2",
        "fetch": {
          "http": {
            "url": "http://gerrit:8080/myProject",
            "ref": "refs/changes/99/4799/2"
          }
        },
        "commit": {
          "parents": [
            {
              "commit": "b4003890dadd406d80222bf1ad8aca09a4876b70",
              "subject": "Implement Feature A"
            }
        ],
        "author": {
          "name": "John Doe",
          "email": "john.doe@example.com",
          "date": "2013-05-07 15:21:27.000000000",
          "tz": 120
        },
        "committer": {
          "name": "Gerrit Code Review",
          "email": "gerrit-server@example.com",
          "date": "2013-05-07 15:35:43.000000000",
          "tz": 120
        },
        "subject": "Implement Feature X",
        "message": "Implement Feature X\n\nAdded feature X."
      }
    }
  }
```

If the change cannot be rebased, e.g. due to conflicts, the response is
"`409 Conflict`" and the error message is contained in the response
body.

**Response.**

``` 
  HTTP/1.1 409 Conflict
  Content-Disposition: attachment
  Content-Type: text/plain; charset=UTF-8

  The change could not be rebased due to a path conflict during merge.
```

### Move Change

*POST /changes/[{change-id}](#change-id)/move*

Move a change.

The destination branch must be provided in the request body inside a
[MoveInput](#move-input)
entity.

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/move HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "destination_branch" : "release-branch"
  }
```

As response a [ChangeInfo](#change-info) entity is returned that
describes the moved change.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "myProject~release-branch~I8473b95934b5732ac55d26311a706c9c2bde9940",
    "project": "myProject",
    "branch": "release-branch",
    "change_id": "I8473b95934b5732ac55d26311a706c9c2bde9940",
    "subject": "Implementing Feature X",
    "status": "NEW",
    "created": "2013-02-01 09:59:32.126000000",
    "updated": "2013-02-21 11:16:36.775000000",
    "mergeable": true,
    "insertions": 2,
    "deletions": 13,
    "_number": 3965,
    "owner": {
      "name": "John Doe"
    }
  }
```

Note that this endpoint will not update the change’s parents, which is
different from the [cherry-pick](#cherry-pick) endpoint.

If the change cannot be moved because the change state doesn’t allow
moving the change, the response is "`409 Conflict`" and the error
message is contained in the response body.

**Response.**

``` 
  HTTP/1.1 409 Conflict
  Content-Disposition: attachment
  Content-Type: text/plain; charset=UTF-8

  change is merged
```

If the change cannot be moved because the user doesn’t have abandon
permission on the change or upload permission on the destination, the
response is "`409 Conflict`" and the error message is contained in the
response body.

**Response.**

``` 
  HTTP/1.1 409 Conflict
  Content-Disposition: attachment
  Content-Type: text/plain; charset=UTF-8

  move not permitted
```

### Revert Change

*POST /changes/[{change-id}](#change-id)/revert*

Reverts a change.

The request body does not need to include a [RevertInput](#revert-input)
entity if no review comment is
added.

**Request.**

``` 
  POST /changes/myProject~master~I1ffe09a505e25f15ce1521bcfb222e51e62c2a14/revert HTTP/1.0
```

As response a [ChangeInfo](#change-info) entity is returned that
describes the reverting change.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940",
    "project": "myProject",
    "branch": "master",
    "change_id": "I8473b95934b5732ac55d26311a706c9c2bde9940",
    "subject": "Revert \"Implementing Feature X\"",
    "status": "NEW",
    "created": "2013-02-01 09:59:32.126000000",
    "updated": "2013-02-21 11:16:36.775000000",
    "mergeable": true,
    "insertions": 6,
    "deletions": 4,
    "_number": 3965,
    "owner": {
      "name": "John Doe"
    }
  }
```

If the change cannot be reverted because the change state doesn’t allow
reverting the change, the response is "`409 Conflict`" and the error
message is contained in the response body.

**Response.**

``` 
  HTTP/1.1 409 Conflict
  Content-Disposition: attachment
  Content-Type: text/plain; charset=UTF-8

  change is new
```

### Submit Change

*POST /changes/[{change-id}](#change-id)/submit*

Submits a change.

The request body only needs to include a [SubmitInput](#submit-input)
entity if submitting on behalf of another
user.

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/submit HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "on_behalf_of": 1001439
  }
```

As response a [ChangeInfo](#change-info) entity is returned that
describes the submitted/merged change.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940",
    "project": "myProject",
    "branch": "master",
    "change_id": "I8473b95934b5732ac55d26311a706c9c2bde9940",
    "subject": "Implementing Feature X",
    "status": "MERGED",
    "created": "2013-02-01 09:59:32.126000000",
    "updated": "2013-02-21 11:16:36.775000000",
    "submitted": "2013-02-21 11:16:36.615000000",
    "_number": 3965,
    "owner": {
      "name": "John Doe"
    }
  }
```

If the change cannot be submitted because the submit rule doesn’t allow
submitting the change, the response is "`409 Conflict`" and the error
message is contained in the response body.

**Response.**

``` 
  HTTP/1.1 409 Conflict
  Content-Disposition: attachment
  Content-Type: text/plain; charset=UTF-8

  blocked by Verified
```

### Changes Submitted Together

*GET
/changes/[{change-id}](#change-id)/submitted\_together?o=NON\_VISIBLE\_CHANGES*

Computes list of all changes which are submitted when
[Submit](#submit-change) is called for this change, including the
current change itself.

The list consists of:

  - The given change.

  - If
    [`change.submitWholeTopic`](config-gerrit.html#change.submitWholeTopic)
    is enabled, include all open changes with the same topic.

  - For each change whose submit type is not CHERRY\_PICK, include
    unmerged ancestors targeting the same branch.

As a special case, the list is empty if this change would be submitted
by itself (without other
changes).

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/submitted_together?o=NON_VISIBLE_CHANGES HTTP/1.0
  Content-Type: application/json; charset=UTF-8
```

As a response a [SubmittedTogetherInfo](#submitted-together-info) entity
is returned that describes what would happen if the change were
submitted. This response contains a list of changes and a count of
changes that are not visible to the caller that are part of the set of
changes to be merged.

The listed changes use the same format as in [Query
Changes](#list-changes) with the [`LABELS`](#labels),
[`DETAILED_LABELS`](#detailed-labels),
[`CURRENT_REVISION`](#current-revision),
[`CURRENT_COMMIT`](#current-commit), and [`SUBMITTABLE`](#submittable)
options set.

Standard [formatting options](#query-options) can be specified with the
`o` parameter, as well as the `submitted_together` specific option
`NON_VISIBLE_CHANGES`.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

)]}'
{
  "changes": [
    {
      "id": "gerrit~master~I1ffe09a505e25f15ce1521bcfb222e51e62c2a14",
      "project": "gerrit",
      "branch": "master",
      "hashtags": [],
      "change_id": "I1ffe09a505e25f15ce1521bcfb222e51e62c2a14",
      "subject": "ChangeMergeQueue: Rewrite such that it works on set of changes",
      "status": "NEW",
      "created": "2015-05-01 15:39:57.979000000",
      "updated": "2015-05-20 19:25:21.592000000",
      "mergeable": true,
      "insertions": 303,
      "deletions": 210,
      "_number": 1779,
      "owner": {
        "_account_id": 1000000
      },
      "labels": {
        "Code-Review": {
          "approved": {
            "_account_id": 1000000
          },
          "all": [
            {
              "value": 2,
              "date": "2015-05-20 19:25:21.592000000",
              "_account_id": 1000000
            }
          ],
          "values": {
            "-2": "This shall not be merged",
            "-1": "I would prefer this is not merged as is",
            " 0": "No score",
            "+1": "Looks good to me, but someone else must approve",
            "+2": "Looks good to me, approved"
          },
          "default_value": 0
        },
        "Verified": {
          "approved": {
            "_account_id": 1000000
          },
          "all": [
            {
              "value": 1,
              "date": "2015-05-20 19:25:21.592000000",
              "_account_id": 1000000
            }
          ],
          "values": {
            "-1": "Fails",
            " 0": "No score",
            "+1": "Verified"
          },
          "default_value": 0
        }
      },
      "permitted_labels": {
        "Code-Review": [
          "-2",
          "-1",
          " 0",
          "+1",
          "+2"
        ],
        "Verified": [
          "-1",
          " 0",
          "+1"
        ]
      },
      "removable_reviewers": [
        {
          "_account_id": 1000000
        }
      ],
      "reviewers": {
        "REVIEWER": [
          {
            "_account_id": 1000000
          }
        ]
      },
      "current_revision": "9adb9f4c7b40eeee0646e235de818d09164d7379",
      "revisions": {
        "9adb9f4c7b40eeee0646e235de818d09164d7379": {
          "kind": "REWORK",
          "_number": 1,
          "created": "2015-05-01 15:39:57.979000000",
          "uploader": {
            "_account_id": 1000000
          },
          "ref": "refs/changes/79/1779/1",
          "fetch": {},
          "commit": {
            "parents": [
              {
                "commit": "2d3176497a2747faed075f163707e57d9f961a1c",
                "subject": "Merge changes from topic \u0027submodule-subscription-tests-and-fixes-3\u0027"
              }
            ],
            "author": {
              "name": "Stefan Beller",
              "email": "sbeller@google.com",
              "date": "2015-04-29 21:36:52.000000000",
              "tz": -420
            },
            "committer": {
              "name": "Stefan Beller",
              "email": "sbeller@google.com",
              "date": "2015-05-01 00:11:16.000000000",
              "tz": -420
            },
            "subject": "ChangeMergeQueue: Rewrite such that it works on set of changes",
            "message": "ChangeMergeQueue: Rewrite such that it works on set of changes\n\nChangeMergeQueue used to work on branches rather than sets of changes.\nThis change is a first step to merge sets of changes (e.g. grouped by a\ntopic and `changes.submitWholeTopic` enabled) in an atomic fashion.\nThis change doesn\u0027t aim to implement these changes, but only as a step\ntowards it.\n\nMergeOp keeps its functionality and behavior as is. A new class\nMergeOpMapper is introduced which will map the set of changes to\nthe set of branches. Additionally the MergeOpMapper is also\nresponsible for the threading done right now, which was part of\nthe ChangeMergeQueue before.\n\nChange-Id: I1ffe09a505e25f15ce1521bcfb222e51e62c2a14\n"
          }
        }
      }
    },
    {
      "id": "gerrit~master~I7fe807e63792b3d26776fd1422e5e790a5697e22",
      "project": "gerrit",
      "branch": "master",
      "hashtags": [],
      "change_id": "I7fe807e63792b3d26776fd1422e5e790a5697e22",
      "subject": "AbstractSubmoduleSubscription: Split up createSubscription",
      "status": "NEW",
      "created": "2015-05-01 15:39:57.979000000",
      "updated": "2015-05-20 19:25:21.546000000",
      "mergeable": true,
      "insertions": 15,
      "deletions": 6,
      "_number": 1780,
      "owner": {
        "_account_id": 1000000
      },
      "labels": {
        "Code-Review": {
          "approved": {
            "_account_id": 1000000
          },
          "all": [
            {
              "value": 2,
              "date": "2015-05-20 19:25:21.546000000",
              "_account_id": 1000000
            }
          ],
          "values": {
            "-2": "This shall not be merged",
            "-1": "I would prefer this is not merged as is",
            " 0": "No score",
            "+1": "Looks good to me, but someone else must approve",
            "+2": "Looks good to me, approved"
          },
          "default_value": 0
        },
        "Verified": {
          "approved": {
            "_account_id": 1000000
          },
          "all": [
            {
              "value": 1,
              "date": "2015-05-20 19:25:21.546000000",
              "_account_id": 1000000
            }
          ],
          "values": {
            "-1": "Fails",
            " 0": "No score",
            "+1": "Verified"
          },
          "default_value": 0
        }
      },
      "permitted_labels": {
        "Code-Review": [
          "-2",
          "-1",
          " 0",
          "+1",
          "+2"
        ],
        "Verified": [
          "-1",
          " 0",
          "+1"
        ]
      },
      "removable_reviewers": [
        {
          "_account_id": 1000000
        }
      ],
      "reviewers": {
        "REVIEWER": [
          {
            "_account_id": 1000000
          }
        ]
      },
      "current_revision": "1bd7c12a38854a2c6de426feec28800623f492c4",
      "revisions": {
        "1bd7c12a38854a2c6de426feec28800623f492c4": {
          "kind": "REWORK",
          "_number": 1,
          "created": "2015-05-01 15:39:57.979000000",
          "uploader": {
            "_account_id": 1000000
          },
          "ref": "refs/changes/80/1780/1",
          "fetch": {},
          "commit": {
            "parents": [
              {
                "commit": "9adb9f4c7b40eeee0646e235de818d09164d7379",
                "subject": "ChangeMergeQueue: Rewrite such that it works on set of changes"
              }
            ],
            "author": {
              "name": "Stefan Beller",
              "email": "sbeller@google.com",
              "date": "2015-04-25 00:11:59.000000000",
              "tz": -420
            },
            "committer": {
              "name": "Stefan Beller",
              "email": "sbeller@google.com",
              "date": "2015-05-01 00:11:16.000000000",
              "tz": -420
            },
            "subject": "AbstractSubmoduleSubscription: Split up createSubscription",
            "message": "AbstractSubmoduleSubscription: Split up createSubscription\n\nLater we want to have subscriptions to more submodules, so we need to\nfind a way to add more submodule entries into the file. By splitting up\nthe createSubscription() method, that is very easy by using the\naddSubmoduleSubscription method multiple times.\n\nChange-Id: I7fe807e63792b3d26776fd1422e5e790a5697e22\n"
          }
        }
      }
    }
  ],
  "non_visible_changes": 0
}
```

If the `o=NON_VISIBLE_CHANGES` query parameter is not passed, then
instead of a [SubmittedTogetherInfo](#submitted-together-info) entity,
the response is a list of changes, or a 403 response with a message if
the set of changes to be submitted with this change includes changes the
caller cannot read.

### Delete Change

*DELETE /changes/[{change-id}](#change-id)*

Deletes a change.

New or abandoned changes can be deleted by their owner if the user is
granted the [Delete Own
Changes](access-control.html#category_delete_own_changes) permission,
otherwise only by
administrators.

**Request.**

``` 
  DELETE /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940 HTTP/1.0
  Content-Type: application/json; charset=UTF-8
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

### Get Included In

*GET /changes/[{change-id}](#change-id)/in*

Retrieves the branches and tags in which a change is included. As result
an [IncludedInInfo](#included-in-info) entity is
returned.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/in HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "branches": [
      "master"
    ],
    "tags": []
  }
```

### Index Change

*POST /changes/[{change-id}](#change-id)/index*

Adds or updates the change in the secondary
index.

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/index HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

### List Change Comments

*GET /changes/[{change-id}](#change-id)/comments*

Lists the published comments of all revisions of the change.

Returns a map of file paths to lists of [CommentInfo](#comment-info)
entries. The entries in the map are sorted by file path, and the
comments for each path are sorted by patch set number. Each comment has
the `patch_set` and `author` fields
set.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/comments HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java": [
      {
        "patch_set": 1,
        "id": "TvcXrmjM",
        "line": 23,
        "message": "[nit] trailing whitespace",
        "updated": "2013-02-26 15:40:43.986000000"
        "author": {
          "_account_id": 1000096,
          "name": "John Doe",
          "email": "john.doe@example.com"
        }
      },
      {
        "patch_set": 2,
        "id": "TveXwFiA",
        "line": 49,
        "in_reply_to": "TfYX-Iuo",
        "message": "Done",
        "updated": "2013-02-26 15:40:45.328000000"
        "author": {
          "_account_id": 1000097,
          "name": "Jane Roe",
          "email": "jane.roe@example.com"
        }
      }
    ]
  }
```

### List Change Robot Comments

*GET /changes/[{change-id}](#change-id)/robotcomments*

Lists the robot comments of all revisions of the change.

Return a map that maps the file path to a list of
[RobotCommentInfo](#robot-comment-info) entries. The entries in the map
are sorted by file
path.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/robotcomments/ HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java": [
      {
        "id": "TvcXrmjM",
        "line": 23,
        "message": "unused import",
        "updated": "2016-02-26 15:40:43.986000000",
        "author": {
          "_account_id": 1000110,
          "name": "Code Analyzer",
          "email": "code.analyzer@example.com"
        },
        "robotId": "importChecker",
        "robotRunId": "76b1375aa8626ea7149792831fe2ed85e80d9e04"
      },
      {
        "id": "TveXwFiA",
        "line": 49,
        "message": "wrong indention",
        "updated": "2016-02-26 15:40:45.328000000",
        "author": {
          "_account_id": 1000110,
          "name": "Code Analyzer",
          "email": "code.analyzer@example.com"
        },
        "robotId": "styleChecker",
        "robotRunId": "5c606c425dd45184484f9d0a2ffd725a7607839b"
      }
    ]
  }
```

### List Change Drafts

*GET /changes/[{change-id}](#change-id)/drafts*

Lists the draft comments of all revisions of the change that belong to
the calling user.

Returns a map of file paths to lists of [CommentInfo](#comment-info)
entries. The entries in the map are sorted by file path, and the
comments for each path are sorted by patch set number. Each comment has
the `patch_set` field set, and no
`author`.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/drafts HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java": [
      {
        "patch_set": 1,
        "id": "TvcXrmjM",
        "line": 23,
        "message": "[nit] trailing whitespace",
        "updated": "2013-02-26 15:40:43.986000000"
      },
      {
        "patch_set": 2,
        "id": "TveXwFiA",
        "line": 49,
        "in_reply_to": "TfYX-Iuo",
        "message": "Done",
        "updated": "2013-02-26 15:40:45.328000000"
      }
    ]
  }
```

### Check Change

*GET /changes/[{change-id}](#change-id)/check*

Performs consistency checks on the change, and returns a
[ChangeInfo](#change-info) entity with the `problems` field set to a
list of [ProblemInfo](#problem-info) entities.

Depending on the type of problem, some fields not marked optional may be
missing from the result. At least `id`, `project`, `branch`, and
`_number` will be
present.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/check HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940",
    "project": "myProject",
    "branch": "master",
    "change_id": "I8473b95934b5732ac55d26311a706c9c2bde9940",
    "subject": "Implementing Feature X",
    "status": "NEW",
    "created": "2013-02-01 09:59:32.126000000",
    "updated": "2013-02-21 11:16:36.775000000",
    "mergeable": true,
    "insertions": 34,
    "deletions": 101,
    "_number": 3965,
    "owner": {
      "name": "John Doe"
    },
    "problems": [
      {
        "message": "Current patch set 1 not found"
      }
    ]
  }
```

### Fix Change

*POST /changes/[{change-id}](#change-id)/check*

Performs consistency checks on the change as with [GET
/check](#check-change), and additionally fixes any problems that can be
fixed automatically. The returned field values reflect any fixes.

Some fixes have options controlling their behavior, which can be set in
the [FixInput](#fix-input) entity body.

Only the change owner, a project owner, or an administrator may fix
changes.

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/check HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940",
    "project": "myProject",
    "branch": "master",
    "change_id": "I8473b95934b5732ac55d26311a706c9c2bde9940",
    "subject": "Implementing Feature X",
    "status": "MERGED",
    "created": "2013-02-01 09:59:32.126000000",
    "updated": "2013-02-21 11:16:36.775000000",
    "submitted": "2013-02-21 11:16:36.615000000",
    "mergeable": true,
    "insertions": 34,
    "deletions": 101,
    "_number": 3965,
    "owner": {
      "name": "John Doe"
    },
    "problems": [
      {
        "message": "Current patch set 2 not found"
      },
      {
        "message": "Patch set 1 (1eee2c9d8f352483781e772f35dc586a69ff5646) is merged into destination ref master (1eee2c9d8f352483781e772f35dc586a69ff5646), but change status is NEW",
        "status": FIXED,
        "outcome": "Marked change as merged"
      }
    ]
  }
```

### Set Work-In-Progress

*POST /changes/[{change-id}](#change-id)/wip*

Marks the change as not ready for review yet.

The request body does not need to include a
[WorkInProgressInput](#work-in-progress-input) entity if no review
comment is added. Actions that create a new patch set in a WIP change
default to notifying **OWNER** instead of
**ALL**.

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/wip HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "message": "Refactoring needs to be done before we can proceed here."
  }
```

**Response.**

``` 
  HTTP/1.1 200 OK
```

### Set Ready-For-Review

*POST /changes/[{change-id}](#change-id)/ready*

Marks the change as ready for review (set WIP property to false).

Activates notifications of reviewer. The request body does not need to
include a [WorkInProgressInput](#work-in-progress-input) entity if no
review comment is
added.

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/ready HTTP/1.0
  Content-Type: application/json;charset=UTF-8

  {
    "message": "Refactoring is done."
  }
```

**Response.**

``` 
  HTTP/1.1 200 OK
```

### Mark Private

*POST /changes/[{change-id}](#change-id)/private*

Marks the change to be private. Changes may only be marked private by
the owner or site administrators.

A message can be specified in the request body inside a
[PrivateInput](#private-input)
entity.

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/private HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "message": "After this security fix has been released we can make it public now."
  }
```

**Response.**

``` 
  HTTP/1.1 201 Created
```

If the change was already private the response is "`200 OK`".

### Unmark Private

*DELETE /changes/[{change-id}](#change-id)/private*

Marks the change to be non-private. Note users can only unmark own
private changes.

A message can be specified in the request body inside a
[PrivateInput](#private-input)
entity.

**Request.**

``` 
  DELETE /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/private HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "message": "This is a security fix that must not be public."
  }
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

If the change was already not private, the response is "`409 Conflict`".

Please note that some proxies prohibit request bodies for DELETE
requests. In this case, if you want to set a message options, use a POST
request:

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/private.delete HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "message": "This is a security fix that must not be public."
  }
```

### Ignore

*PUT /changes/[{change-id}](#change-id)/ignore*

Marks a change as ignored. The change will not be shown in the incoming
reviews dashboard, and email notifications will be
suppressed.

**Request.**

``` 
  PUT /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/ignore HTTP/1.0
```

### Unignore

*PUT /changes/[{change-id}](#change-id)/unignore*

Un-marks a change as
ignored.

**Request.**

``` 
  PUT /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/unignore HTTP/1.0
```

### Mark as Reviewed

*PUT /changes/[{change-id}](#change-id)/reviewed*

Marks a change as reviewed.

This allows users to "de-highlight" changes in their dashboard until a
new patch set is uploaded.

This differs from the [ignore](#ignore) endpoint, which will mute emails
and hide the change from dashboard completely until it is
[unignored](#unignore)
again.

**Request.**

``` 
  PUT /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/reviewed HTTP/1.0
```

### Mark as Unreviewed

*PUT /changes/[{change-id}](#change-id)/unreviewed*

Marks a change as unreviewed.

This allows users to "highlight" changes in their
dashboard

**Request.**

``` 
  PUT /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/unreviewed HTTP/1.0
```

### Get Hashtags

*GET /changes/[{change-id}](#change-id)/hashtags*

Gets the hashtags associated with a change.

\[NOTE\] Hashtags are only available when NoteDb is
enabled.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/hashtags HTTP/1.0
```

As response the change’s hashtags are returned as a list of strings.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    "hashtag1",
    "hashtag2"
  ]
```

### Set Hashtags

*POST /changes/[{change-id}](#change-id)/hashtags*

Adds and/or removes hashtags from a change.

\[NOTE\] Hashtags are only available when NoteDb is enabled.

The hashtags to add or remove must be provided in the request body
inside a [HashtagsInput](#hashtags-input)
entity.

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/hashtags HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "add" : [
      "hashtag3"
    ],
    "remove" : [
      "hashtag2"
    ]
  }
```

As response the change’s hashtags are returned as a list of strings.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    "hashtag1",
    "hashtag3"
  ]
```

## Change Edit Endpoints

### Get Change Edit Details

'GET /changes/[{change-id}](#change-id)/edit

Retrieves a change edit
details.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/edit HTTP/1.0
```

As response an [EditInfo](#edit-info) entity is returned that describes
the change edit, or "`204 No Content`" when change edit doesn’t exist
for this change. Change edits are stored on special branches and there
can be max one edit per user per change. Edits aren’t tracked in the
database. When request parameter `list` is provided the response also
includes the file list. When `base` request parameter is provided the
file list is computed against this base revision. When request parameter
`download-commands` is provided fetch info map is also included.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "commit":{
      "parents":[
        {
          "commit":"1eee2c9d8f352483781e772f35dc586a69ff5646",
        }
      ],
      "author":{
        "name":"Shawn O. Pearce",
        "email":"sop@google.com",
        "date":"2012-04-24 18:08:08.000000000",
        "tz":-420
       },
       "committer":{
         "name":"Shawn O. Pearce",
         "email":"sop@google.com",
         "date":"2012-04-24 18:08:08.000000000",
         "tz":-420
       },
       "subject":"Use an EventBus to manage star icons",
       "message":"Use an EventBus to manage star icons\n\nImage widgets that need to ..."
    },
    "base_revision":"c35558e0925e6985c91f3a16921537d5e572b7a3"
  }
```

### Change file content in Change Edit

'PUT /changes/[{change-id}](#change-id)/edit/path%2fto%2ffile

Put content of a file to a change
edit.

**Request.**

``` 
  PUT /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/edit/foo HTTP/1.0
```

When change edit doesn’t exist for this change yet it is created. When
file content isn’t provided, it is wiped out for that file. As response
"`204 No Content`" is returned.

**Response.**

``` 
  HTTP/1.1 204 No Content
```

### Restore file content or rename files in Change Edit

'POST /changes/[{change-id}](#change-id)/edit

Creates empty change edit, restores file content or renames files in
change edit. The request body needs to include a
[ChangeEditInput](#change-edit-input) entity when a file within change
edit should be restored or old and new file names to rename a
file.

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/edit HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "restore_path": "foo"
  }
```

or for
rename:

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/edit HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "old_path": "foo",
    "new_path": "bar"
  }
```

When change edit doesn’t exist for this change yet it is created. When
path and restore flag are provided in request body, this file is
restored. When old and new file names are provided, the file is renamed.
As response "`204 No Content`" is returned.

**Response.**

``` 
  HTTP/1.1 204 No Content
```

### Change commit message in Change Edit

'PUT /changes/[{change-id}](#change-id)/edit:message

Modify commit message. The request body needs to include a
[ChangeEditMessageInput](#change-edit-message-input)
entity.

**Request.**

``` 
  PUT /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/edit:message HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "message": "New commit message\n\nChange-Id: I10394472cbd17dd12454f229e4f6de00b143a444"
  }
```

If a change edit doesn’t exist for this change yet, it is created. As
response "`204 No Content`" is returned.

**Response.**

``` 
  HTTP/1.1 204 No Content
```

### Delete file in Change Edit

*DELETE /changes/[{change-id}](#change-id)/edit/path%2fto%2ffile*

Deletes a file from a change edit. This deletes the file from the
repository completely. This is not the same as reverting or restoring a
file to its previous
contents.

**Request.**

``` 
  DELETE /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/edit/foo HTTP/1.0
```

When change edit doesn’t exist for this change yet it is created.

**Response.**

``` 
  HTTP/1.1 204 No Content
```

### Retrieve file content from Change Edit

'GET /changes/[{change-id}](#change-id)/edit/path%2fto%2ffile

Retrieves content of a file from a change
edit.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/edit/foo HTTP/1.0
```

The content of the file is returned as text encoded inside base64. The
Content-Type header will always be `text/plain` reflecting the outer
base64 encoding. A Gerrit-specific `X-FYI-Content-Type` header can be
examined to find the server detected content type of the file.

When the specified file was deleted in the change edit "`204 No
Content`" is returned.

If only the content type is required, callers should use HEAD to avoid
downloading the encoded file contents.

If the `base` parameter is set to true, the returned content is from the
revision that the edit is based on.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: text/plain; charset=ISO-8859-1
  X-FYI-Content-Encoding: base64
  X-FYI-Content-Type: text/xml

  RnJvbSA3ZGFkY2MxNTNmZGVhMTdhYTg0ZmYzMmE2ZTI0NWRiYjY...
```

Alternatively, if the only value of the Accept request header is
`application/json` the content is returned as JSON string and
`X-FYI-Content-Encoding` is set to `json`.

### Retrieve meta data of a file from Change Edit

'GET /changes/[{change-id}](#change-id)/edit/path%2fto%2ffile/meta

Retrieves meta data of a file from a change edit. Currently only web
links are
returned.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/edit/foo/meta HTTP/1.0
```

This REST endpoint retrieves additional information for a file in a
change edit. As result an [EditFileInfo](#edit-file-info) entity is
returned.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
  "web_links":[
    {
      "show_on_side_by_side_diff_view": true,
      "name": "side-by-side preview diff",
      "image_url": "plugins/xdocs/static/sideBySideDiffPreview.png",
      "url": "#/x/xdocs/c/42/1..0/README.md",
      "target": "_self"
    },
    {
      "show_on_unified_diff_view": true,
      "name": "unified preview diff",
      "image_url": "plugins/xdocs/static/unifiedDiffPreview.png",
      "url": "#/x/xdocs/c/42/1..0/README.md,unified",
      "target": "_self"
    }
  ]}
```

### Retrieve commit message from Change Edit or current patch set of the change

'GET /changes/[{change-id}](#change-id)/edit:message

Retrieves commit message from change edit.

If the `base` parameter is set to true, the returned message is from the
revision that the edit is based
on.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/edit:message HTTP/1.0
```

The commit message is returned as base64 encoded string.

**Response.**

``` 
  HTTP/1.1 200 OK

  VGhpcyBpcyBhIGNvbW1pdCBtZXNzYWdlCgpDaGFuZ2UtSWQ6IElhYzhmZGM1MGRlZjFiYWUzYjAz
M2JhNjcxZTk0OTBmNzUxNDU5ZGUzCg==
```

Alternatively, if the only value of the Accept request header is
`application/json` the commit message is returned as JSON string:

**Response.**

``` 
  HTTP/1.1 200 OK

)]}'
"Subject of the commit message\n\nThis is the body of the commit message.\n\nChange-Id: Iaf1ba916bf843c175673d675bf7f52862f452db9\n"
```

### Publish Change Edit

'POST /changes/[{change-id}](#change-id)/edit:publish

Promotes change edit to a regular patch set.

Options can be provided in the request body as a
[PublishChangeEditInput](#publish-change-edit-input)
entity.

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/edit:publish HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "notify": "NONE"
  }
```

As response "`204 No Content`" is returned.

**Response.**

``` 
  HTTP/1.1 204 No Content
```

### Rebase Change Edit

'POST /changes/[{change-id}](#change-id)/edit:rebase

Rebases change edit on top of latest patch
set.

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/edit:rebase HTTP/1.0
```

When change was rebased on top of latest patch set, response "`204 No
Content`" is returned. When change edit is already based on top of the
latest patch set, the response "`409 Conflict`" is returned.

**Response.**

``` 
  HTTP/1.1 204 No Content
```

### Delete Change Edit

*DELETE /changes/[{change-id}](#change-id)/edit*

Deletes change
edit.

**Request.**

``` 
  DELETE /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/edit HTTP/1.0
```

As response "`204 No Content`" is returned.

**Response.**

``` 
  HTTP/1.1 204 No Content
```

## Reviewer Endpoints

### List Reviewers

*GET /changes/[{change-id}](#change-id)/reviewers/*

Lists the reviewers of a change.

As result a list of [ReviewerInfo](#reviewer-info) entries is
returned.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/reviewers/ HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "approvals": {
        "Verified": "+1",
        "Code-Review": "+2"
      },
      "_account_id": 1000096,
      "name": "John Doe",
      "email": "john.doe@example.com"
    },
    {
      "approvals": {
        "Verified": " 0",
        "Code-Review": "-1"
      },
      "_account_id": 1000097,
      "name": "Jane Roe",
      "email": "jane.roe@example.com"
    }
  ]
```

### Suggest Reviewers

*GET /changes/[{change-id}](#change-id)/suggest\_reviewers?q=J\&n=5*

Suggest the reviewers for a given query `q` and result limit `n`. If
result limit is not passed, then the default 10 is used.

Groups can be excluded from the results by specifying *e=f*.

As result a list of [SuggestedReviewerInfo](#suggested-reviewer-info)
entries is
returned.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/suggest_reviewers?q=J HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "account": {
        "_account_id": 1000097,
        "name": "Jane Roe",
        "email": "jane.roe@example.com"
      },
      "count": 1
    },
    {
      "group": {
        "id": "4fd581c0657268f2bdcc26699fbf9ddb76e3a279",
        "name": "Joiner"
      },
      "count": 5
    }
  ]
```

### Get Reviewer

*GET
/changes/[{change-id}](#change-id)/reviewers/[{account-id}](rest-api-accounts.html#account-id)*

Retrieves a reviewer of a change.

As response a [ReviewerInfo](#reviewer-info) entity is returned that
describes the
reviewer.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/reviewers/john.doe@example.com HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "approvals": {
      "Verified": "+1",
      "Code-Review": "+2"
    },
    "_account_id": 1000096,
    "name": "John Doe",
    "email": "john.doe@example.com"
  }
```

### Add Reviewer

*POST /changes/[{change-id}](#change-id)/reviewers*

Adds one user or all members of one group as reviewer to the change.

The reviewer to be added to the change must be provided in the request
body as a [ReviewerInput](#reviewer-input)
entity.

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/reviewers HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "reviewer": "john.doe@example.com"
  }
```

As response an [AddReviewerResult](#add-reviewer-result) entity is
returned that describes the newly added reviewers.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "input": "john.doe@example.com",
    "reviewers": [
      {
        "_account_id": 1000096,
        "name": "John Doe",
        "email": "john.doe@example.com"
        "approvals": {
          "Verified": " 0",
          "Code-Review": " 0"
        },
      }
    ]
  }
```

If a group is specified, adding the group members as reviewers is an
atomic operation. This means if an error is returned, none of the
members are added as reviewer.

If a group with many members is added as reviewer a confirmation may be
required.

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/reviewers HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "reviewer": "MyProjectVerifiers"
  }
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "input": "MyProjectVerifiers",
    "error": "The group My Group has 15 members. Do you want to add them all as reviewers?",
    "confirm": true
  }
```

To confirm the addition of the reviewers, resend the request with the
`confirmed` flag being
set.

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/reviewers HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "input": "MyProjectVerifiers",
    "confirmed": true
  }
```

If
[reviewer.enableByEmail](config-project-config.html#reviewer.enableByEmail)
is set for the project, reviewers and CCs are not required to have a
Gerrit account. If you POST an email address of a reviewer or CC then,
they will be added to the change even if they don’t have a Gerrit
account.

If this option is disabled, the request would fail with `400 Bad
Request` if the email address can’t be resolved to an active Gerrit
account.

Note that the name is optional so both "un.registered@reviewer.com" and
"John Doe \<<un.registered@reviewer.com>\>" are valid inputs.

Reviewers without Gerrit accounts can only be added on changes visible
to anonymous
users.

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/reviewers HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "reviewer": "John Doe <un.registered@reviewer.com>"
  }
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "input": "John Doe <un.registered@reviewer.com>"
  }
```

**Notifications.**

An email will be sent using the "newchange" template.

<table>
<colgroup>
<col width="33%" />
<col width="33%" />
<col width="33%" />
</colgroup>
<thead>
<tr class="header">
<th>WIP State</th>
<th>Default</th>
<th>notify=ALL</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p>Ready for review</p></td>
<td><p>owner, reviewers, CCs</p></td>
<td><p>owner, reviewers, CCs</p></td>
</tr>
<tr class="even">
<td><p>Work in progress</p></td>
<td><p>not sent</p></td>
<td><p>owner, reviewers, CCs</p></td>
</tr>
</tbody>
</table>

### Delete Reviewer

*DELETE
/changes/[{change-id}](#change-id)/reviewers/[{account-id}](rest-api-accounts.html#account-id)*  
*POST
/changes/[{change-id}](#change-id)/reviewers/[{account-id}](rest-api-accounts.html#account-id)/delete*

Deletes a reviewer from a change.

Options can be provided in the request body as a
[DeleteReviewerInput](#delete-reviewer-input)
entity.

**Request.**

``` 
  DELETE /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/reviewers/John%20Doe HTTP/1.0
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/reviewers/John%20Doe/delete HTTP/1.0
```

Please note that some proxies prohibit request bodies for DELETE
requests. In this case, if you want to specify options, use a POST
request:

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/reviewers/John%20Doe/delete HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "notify": "NONE"
  }
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

**Notifications.**

An email will be sent using the "deleteReviewer" template. If deleting
the reviewer resulted in one or more approvals being removed, then the
deleted reviewer will also receive a notification (unless notify=NONE).

<table>
<colgroup>
<col width="16%" />
<col width="83%" />
</colgroup>
<thead>
<tr class="header">
<th>WIP State</th>
<th>Default Recipients</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p>Ready for review</p></td>
<td><p>notify=ALL: deleted reviewer (if voted), owner, reviewers, CCs, stars, ALL_COMMENTS watchers</p></td>
</tr>
<tr class="even">
<td><p>Work in progress</p></td>
<td><p>notify=NONE: deleted reviewer (if voted)</p></td>
</tr>
</tbody>
</table>

### List Votes

*GET
/changes/[{change-id}](#change-id)/reviewers/[{account-id}](rest-api-accounts.html#account-id)/votes/*

Lists the votes for a specific reviewer of the
change.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/reviewers/John%20Doe/votes/ HTTP/1.0
```

As result a map is returned that maps the label name to the label value.
The entries in the map are sorted by label name.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json;charset=UTF-8

  )]}'
  {
    "Code-Review": -1,
    "Verified": 1
    "Work-In-Progress": 1,
  }
```

### Delete Vote

*DELETE
/changes/[{change-id}](#change-id)/reviewers/[{account-id}](rest-api-accounts.html#account-id)/votes/[{label-id}](#label-id)*  
*POST
/changes/[{change-id}](#change-id)/reviewers/[{account-id}](rest-api-accounts.html#account-id)/votes/[{label-id}](#label-id)/delete*

Deletes a single vote from a change. Note, that even when the last vote
of a reviewer is removed the reviewer itself is still listed on the
change.

Options can be provided in the request body as a
[DeleteVoteInput](#delete-vote-input)
entity.

**Request.**

``` 
  DELETE /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/reviewers/John%20Doe/votes/Code-Review HTTP/1.0
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/reviewers/John%20Doe/votes/Code-Review/delete HTTP/1.0
```

Please note that some proxies prohibit request bodies for DELETE
requests. In this case, if you want to specify options, use a POST
request:

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/reviewers/John%20Doe/votes/Code-Review/delete HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "notify": "NONE"
  }
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

## Revision Endpoints

### Get Commit

*GET
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/commit*

Retrieves a parsed commit of a
revision.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/commit HTTP/1.0
```

As response a [CommitInfo](#commit-info) entity is returned that
describes the revision.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "commit": "674ac754f91e64a0efb8087e59a176484bd534d1",
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

Adding query parameter `links` (for example `/changes/.../commit?links`)
returns a [CommitInfo](#commit-info) with the additional field
`web_links`.

### Get Description

*GET
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/description*

Retrieves the description of a patch
set.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/description HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  "Added Documentation"
```

If the patch set does not have a description an empty string is
returned.

### Set Description

*PUT
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/description*

Sets the description of a patch set.

The new description must be provided in the request body inside a
[DescriptionInput](#description-input)
entity.

**Request.**

``` 
  PUT /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/description HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "description": "Added Documentation"
  }
```

As response the new description is returned.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  "Added Documentation"
```

### Get Merge List

*GET
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/mergelist*

Returns the list of commits that are being integrated into a target
branch by a merge commit. By default the first parent is assumed to be
uninteresting. By using the `parent` option another parent can be set as
uninteresting (parents are 1-based).

The list of commits is returned as a list of [CommitInfo](#commit-info)
entities. Web links are only included if the `links` option was
set.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/7e30d802b890ec8d0be45b1cc2a8ef092bcfc858/mergelist HTTP/1.0
```

**Response.**

    HTTP/1.1 200 OK
      Content-Disposition: attachment
      Content-Type: application/json; charset=UTF-8
    
      )]}'
      [
        {
          "commit": "674ac754f91e64a0efb8087e59a176484bd534d1",
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
      ]

### Get Revision Actions

*GET
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/actions*

Retrieves revision [actions](#action-info) of the revision of a
change.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/actions' HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'

{
  "submit": {
    "method": "POST",
    "label": "Submit",
    "title": "Submit patch set 1 into master",
    "enabled": true
  },
  "cherrypick": {
    "method": "POST",
    "label": "Cherry Pick",
    "title": "Cherry pick change to a different branch",
    "enabled": true
  }
}
```

The response is a flat map of possible revision actions mapped to their
[ActionInfo](#action-info).

### Get Review

*GET
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/review*

Retrieves a review of a
revision.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/review HTTP/1.0
```

As response a [ChangeInfo](#change-info) entity with [detailed
labels](#detailed-labels) and [detailed accounts](#detailed-accounts) is
returned that describes the review of the revision. The revision for
which the review is retrieved is contained in the `revisions` field. In
addition the `current_revision` field is set if the revision for which
the review is retrieved is the current revision of the change. Please
note that the returned labels are always for the current patch set.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940",
    "project": "myProject",
    "branch": "master",
    "change_id": "I8473b95934b5732ac55d26311a706c9c2bde9940",
    "subject": "Implementing Feature X",
    "status": "NEW",
    "created": "2013-02-01 09:59:32.126000000",
    "updated": "2013-02-21 11:16:36.775000000",
    "mergeable": true,
    "insertions": 34,
    "deletions": 45,
    "_number": 3965,
    "owner": {
      "_account_id": 1000096,
      "name": "John Doe",
      "email": "john.doe@example.com"
    },
    "labels": {
      "Verified": {
        "all": [
          {
            "value": 0,
            "_account_id": 1000096,
            "name": "John Doe",
            "email": "john.doe@example.com"
          },
          {
            "value": 0,
            "_account_id": 1000097,
            "name": "Jane Roe",
            "email": "jane.roe@example.com"
          }
        ],
        "values": {
          "-1": "Fails",
          " 0": "No score",
          "+1": "Verified"
        }
      },
      "Code-Review": {
        "all": [
          {
            "value": -1,
            "_account_id": 1000096,
            "name": "John Doe",
            "email": "john.doe@example.com"
          },
          {
            "value": 1,
            "_account_id": 1000097,
            "name": "Jane Roe",
            "email": "jane.roe@example.com"
          }
        ]
        "values": {
          "-2": "This shall not be merged",
          "-1": "I would prefer this is not merged as is",
          " 0": "No score",
          "+1": "Looks good to me, but someone else must approve",
          "+2": "Looks good to me, approved"
        }
      }
    },
    "permitted_labels": {
      "Verified": [
        "-1",
        " 0",
        "+1"
      ],
      "Code-Review": [
        "-2",
        "-1",
        " 0",
        "+1",
        "+2"
      ]
    },
    "removable_reviewers": [
      {
        "_account_id": 1000096,
        "name": "John Doe",
        "email": "john.doe@example.com"
      },
      {
        "_account_id": 1000097,
        "name": "Jane Roe",
        "email": "jane.roe@example.com"
      }
    ],
    "reviewers": {
      "REVIEWER": [
        {
          "_account_id": 1000096,
          "name": "John Doe",
          "email": "john.doe@example.com"
        },
        {
          "_account_id": 1000097,
          "name": "Jane Roe",
          "email": "jane.roe@example.com"
        }
      ]
    },
    "current_revision": "674ac754f91e64a0efb8087e59a176484bd534d1",
    "revisions": {
      "674ac754f91e64a0efb8087e59a176484bd534d1": {
        "kind": "REWORK",
        "_number": 2,
        "ref": "refs/changes/65/3965/2",
        "fetch": {
          "http": {
            "url": "http://gerrit/myProject",
            "ref": "refs/changes/65/3965/2"
          }
        }
      }
    }
  }
```

### Get Related Changes

*GET
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/related*

Retrieves related changes of a revision. Related changes are changes
that either depend on, or are dependencies of the
revision.

**Request.**

``` 
  GET /changes/gerrit~master~I5e4fc08ce34d33c090c9e0bf320de1b17309f774/revisions/b1cb4caa6be46d12b94c25aa68aebabcbb3f53fe/related HTTP/1.0
```

As result a [RelatedChangesInfo](#related-changes-info) entity is
returned describing the related changes.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "changes": [
      {
        "project": "gerrit",
        "change_id": "Ic62ae3103fca2214904dbf2faf4c861b5f0ae9b5",
        "commit": {
          "commit": "78847477532e386f5a2185a4e8c90b2509e354e3",
          "parents": [
            {
              "commit": "bb499510bbcdbc9164d96b0dbabb4aa45f59a87e"
            }
          ],
          "author": {
            "name": "David Ostrovsky",
            "email": "david@ostrovsky.org",
            "date": "2014-07-12 15:04:24.000000000",
            "tz": 120
          },
          "subject": "Remove Solr"
        },
        "_change_number": 58478,
        "_revision_number": 2,
        "_current_revision_number": 2
        "status": "NEW"
      },
      {
        "project": "gerrit",
        "change_id": "I5e4fc08ce34d33c090c9e0bf320de1b17309f774",
        "commit": {
          "commit": "b1cb4caa6be46d12b94c25aa68aebabcbb3f53fe",
          "parents": [
            {
              "commit": "d898f12a9b7a92eb37e7a80636195a1b06417aad"
            }
          ],
          "author": {
            "name": "David Pursehouse",
            "email": "david.pursehouse@sonymobile.com",
            "date": "2014-06-24 02:01:28.000000000",
            "tz": 540
          },
          "subject": "Add support for secondary index with Elasticsearch"
        },
        "_change_number": 58081,
        "_revision_number": 10,
        "_current_revision_number": 10
        "status": "NEW"
      }
    ]
  }
```

### Set Review

*POST
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/review*

Sets a review on a revision, optionally also publishing draft comments,
setting labels, adding reviewers or CCs, and modifying the work in
progress property.

The review must be provided in the request body as a
[ReviewInput](#review-input) entity.

A review cannot be set on a change edit. Trying to post a review for a
change edit fails with `409 Conflict`.

Here is an example of using this method to set
labels:

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/review HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "tag": "jenkins",
    "message": "Some nits need to be fixed.",
    "labels": {
      "Code-Review": -1
    },
    "comments": {
      "gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java": [
        {
          "line": 23,
          "message": "[nit] trailing whitespace"
        },
        {
          "line": 49,
          "message": "[nit] s/conrtol/control"
        },
        {
          "range": {
            "start_line": 50,
            "start_character": 0,
            "end_line": 55,
            "end_character": 20
          },
          "message": "Incorrect indentation"
        }
      ]
    }
  }
```

As response a [ReviewResult](#review-result) entity is returned that
describes the applied labels and any added reviewers (e.g. yourself, if
you set a label but weren’t previously a reviewer on this CL).

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "labels": {
      "Code-Review": -1
    }
  }
```

It is also possible to add one or more reviewers or CCs to a change
simultaneously with a
review:

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/review HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "message": "I don't have context here. Jane and maybe John and the project leads should take a look.",
    "reviewers": [
      {
        "reviewer": "jane.roe@example.com"
      },
      {
        "reviewer": "john.doe@example.com",
        "state": "CC"
      }
      {
        "reviewer": "MyProjectVerifiers",
      }
    ]
  }
```

Each element of the `reviewers` list is an instance of
[ReviewerInput](#reviewer-input). The corresponding result of adding
each reviewer will be returned in a map of inputs to
[AddReviewerResult](#add-reviewer-result)s.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "reviewers": {
      "jane.roe@example.com": {
        "input": "jane.roe@example.com",
        "reviewers": [
          {
            "_account_id": 1000097,
            "name": "Jane Roe",
            "email": "jane.roe@example.com"
            "approvals": {
              "Verified": " 0",
              "Code-Review": " 0"
            },
          },
        ]
      },
      "john.doe@example.com": {
        "input": "john.doe@example.com",
        "ccs": [
          {
            "_account_id": 1000096,
            "name": "John Doe",
            "email": "john.doe@example.com"
            "approvals": {
              "Verified": " 0",
              "Code-Review": " 0"
            },
          }
        ]
      },
      "MyProjectVerifiers": {
        "input": "MyProjectVerifiers",
        "reviewers": [
          {
            "_account_id": 1000098,
            "name": "Alice Ansel",
            "email": "alice.ansel@example.com"
            "approvals": {
              "Verified": " 0",
              "Code-Review": " 0"
            },
          },
          {
            "_account_id": 1000099,
            "name": "Bob Bollard",
            "email": "bob.bollard@example.com"
            "approvals": {
              "Verified": " 0",
              "Code-Review": " 0"
            },
          },
        ]
      }
    }
  }
```

If there are any errors returned for reviewers, the entire review
request will be rejected with `400 Bad Request`. None of the entries
will have the `reviewers` or `ccs` field set, and those which
specifically failed will have the `errors` field set containing details
of why they failed.

**Error Response.**

``` 
  HTTP/1.1 400 Bad Request
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "reviewers": {
      "jane.roe@example.com": {
        "input": "jane.roe@example.com",
        "error": "Account of jane.roe@example.com is inactive."
      },
      "john.doe@example.com": {
        "input": "john.doe@example.com"
      },
      "MyProjectVerifiers": {
        "input": "MyProjectVerifiers",
        "error": "The group My Group has 15 members. Do you want to add them all as reviewers?",
        "confirm": true
      }
    }
  }
```

**Notifications.**

An email will be sent using the "comment" template.

If the top-level notify property is null or not set, then notification
behavior depends on whether the change is WIP, whether it has started
review, and whether the tag property is null.

> **Note**
> 
> If adding reviewers, the notify property of each ReviewerInput is
> **ignored**. Use the notify property of the top-level
> [ReviewInput](#review-input) instead.

For the purposes of this table, **everyone** means **owner, reviewers,
CCs, stars, and ALL\_COMMENTS watchers**.

<table>
<colgroup>
<col width="25%" />
<col width="12%" />
<col width="12%" />
<col width="25%" />
<col width="25%" />
</colgroup>
<thead>
<tr class="header">
<th>WIP State</th>
<th>Review Started</th>
<th>Tag Given</th>
<th>Default</th>
<th>notify=ALL</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p>Ready for review</p></td>
<td><p>N/A</p></td>
<td><p>N/A</p></td>
<td><p>everyone</p></td>
<td><p>everyone</p></td>
</tr>
<tr class="even">
<td><p>Work in progress</p></td>
<td><p>no</p></td>
<td><p>no</p></td>
<td><p>not sent</p></td>
<td><p>everyone</p></td>
</tr>
<tr class="odd">
<td><p>Work in progress</p></td>
<td><p>no</p></td>
<td><p>yes</p></td>
<td><p>owner</p></td>
<td><p>everyone</p></td>
</tr>
<tr class="even">
<td><p>Work in progress</p></td>
<td><p>yes</p></td>
<td><p>no</p></td>
<td><p>everyone</p></td>
<td><p>everyone</p></td>
</tr>
<tr class="odd">
<td><p>Work in progress</p></td>
<td><p>yes</p></td>
<td><p>yes</p></td>
<td><p>owner</p></td>
<td><p>everyone</p></td>
</tr>
</tbody>
</table>

If reviewers are added, then a second email will be sent using the
"newchange" template. The notification logic for this email is the same
as for [Add Reviewer](#add-reviewer).

<table>
<colgroup>
<col width="33%" />
<col width="33%" />
<col width="33%" />
</colgroup>
<thead>
<tr class="header">
<th>WIP State</th>
<th>Default</th>
<th>notify=ALL</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><p>Ready for review</p></td>
<td><p>owner, reviewers, CCs</p></td>
<td><p>owner, reviewers, CCs</p></td>
</tr>
<tr class="even">
<td><p>Work in progress</p></td>
<td><p>not sent</p></td>
<td><p>owner, reviewers, CCs</p></td>
</tr>
</tbody>
</table>

### Rebase Revision

*POST
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/rebase*

Rebases a revision.

Optionally, the parent revision can be changed to another patch set
through the [RebaseInput](#rebase-input)
entity.

**Request.**

``` 
  POST /changes/myProject~master~I3ea943139cb62e86071996f2480e58bf3eeb9dd2/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/rebase HTTP/1.0
  Content-Type: application/json;charset=UTF-8

  {
    "base" : "1234",
  }
```

As response a [ChangeInfo](#change-info) entity is returned that
describes the rebased change. Information about the current patch set is
included.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "myProject~master~I3ea943139cb62e86071996f2480e58bf3eeb9dd2",
    "project": "myProject",
    "branch": "master",
    "change_id": "I3ea943139cb62e86071996f2480e58bf3eeb9dd2",
    "subject": "Implement Feature X",
    "status": "NEW",
    "created": "2013-02-01 09:59:32.126000000",
    "updated": "2013-02-21 11:16:36.775000000",
    "mergeable": false,
    "insertions": 21,
    "deletions": 21,
    "_number": 4799,
    "owner": {
      "name": "John Doe"
    },
    "current_revision": "27cc4558b5a3d3387dd11ee2df7a117e7e581822",
    "revisions": {
      "27cc4558b5a3d3387dd11ee2df7a117e7e581822": {
        "kind": "REWORK",
        "_number": 2,
        "ref": "refs/changes/99/4799/2",
        "fetch": {
          "http": {
            "url": "http://gerrit:8080/myProject",
            "ref": "refs/changes/99/4799/2"
          }
        },
        "commit": {
          "parents": [
            {
              "commit": "b4003890dadd406d80222bf1ad8aca09a4876b70",
              "subject": "Implement Feature A"
            }
        ],
        "author": {
          "name": "John Doe",
          "email": "john.doe@example.com",
          "date": "2013-05-07 15:21:27.000000000",
          "tz": 120
        },
        "committer": {
          "name": "Gerrit Code Review",
          "email": "gerrit-server@example.com",
          "date": "2013-05-07 15:35:43.000000000",
          "tz": 120
        },
        "subject": "Implement Feature X",
        "message": "Implement Feature X\n\nAdded feature X."
      }
    }
  }
```

If the revision cannot be rebased, e.g. due to conflicts, the response
is "`409 Conflict`" and the error message is contained in the response
body.

**Response.**

``` 
  HTTP/1.1 409 Conflict
  Content-Disposition: attachment
  Content-Type: text/plain; charset=UTF-8

  The change could not be rebased due to a path conflict during merge.
```

### Submit Revision

*POST
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/submit*

Submits a
revision.

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/submit HTTP/1.0
  Content-Type: application/json; charset=UTF-8
```

As response a [SubmitInfo](#submit-info) entity is returned that
describes the status of the submitted change.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "status": "MERGED"
  }
```

If the revision cannot be submitted, e.g. because the submit rule
doesn’t allow submitting the revision or the revision is not the
current revision, the response is "`409 Conflict`" and the error message
is contained in the response body.

**Response.**

``` 
  HTTP/1.1 409 Conflict
  Content-Type: text/plain; charset=UTF-8

  "revision 674ac754f91e64a0efb8087e59a176484bd534d1 is not current revision"
```

### Get Patch

*GET
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/patch*

Gets the formatted patch for one
revision.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/current/patch HTTP/1.0
```

The formatted patch is returned as text encoded inside base64:

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: text/plain; charset=ISO-8859-1
  X-FYI-Content-Encoding: base64
  X-FYI-Content-Type: application/mbox

  RnJvbSA3ZGFkY2MxNTNmZGVhMTdhYTg0ZmYzMmE2ZTI0NWRiYjY...
```

Adding query parameter `zip` (for example `/changes/.../patch?zip`)
returns the patch as a single file inside of a ZIP archive. Clients can
expand the ZIP to obtain the plain text patch, avoiding the need for a
base64 decoding step. This option implies `download`.

Query parameter `download` (e.g. `/changes/.../patch?download`) will
suggest the browser save the patch as `commitsha1.diff.base64`, for
later processing by command line tools.

If the `path` parameter is set, the returned content is a diff of the
single file that the path refers to.

### Submit Preview

*GET
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/preview\_submit*

Gets a file containing thin bundles of all modified projects if this
change was submitted. The bundles are named `${ProjectName}.git`. Each
thin bundle contains enough to construct the state in which a project
would be in if this change were submitted. The base of the thin bundles
are the current target branches, so to make use of this call in a
non-racy way, first get the bundles and then fetch all projects
contained in the bundle. (This assumes no non-fastforward pushes).

You need to give a parameter *?format=zip* or *?format=tar* to specify
the format for the outer container. It is always possible to use tgz,
even if tgz is not in the list of allowed archive formats.

To make good use of this call, you would roughly need code as found
at:

``` 
 $ curl -Lo preview_submit_test.sh http://review.example.com:8080/tools/scripts/preview_submit_test.sh
```

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/current/preview_submit?zip HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Date: Tue, 13 Sep 2016 19:13:46 GMT
  Content-Disposition: attachment; filename="submit-preview-147.zip"
  X-Content-Type-Options: nosniff
  Cache-Control: no-cache, no-store, max-age=0, must-revalidate
  Pragma: no-cache
  Expires: Mon, 01 Jan 1990 00:00:00 GMT
  Content-Type: application/x-zip
  Transfer-Encoding: chunked

  [binary stuff]
```

In case of an error, the response is not a zip file but a regular json
response, containing only the error message:

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  "Anonymous users cannot submit"
```

### Get Mergeable

*GET
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/mergeable*

Gets the method the server will use to submit (merge) the change and an
indicator if the change is currently
mergeable.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/current/mergeable HTTP/1.0
```

As response a [MergeableInfo](#mergeable-info) entity is returned.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    submit_type: "MERGE_IF_NECESSARY",
    strategy: "recursive",
    mergeable: true
  }
```

If the `other-branches` parameter is specified, the mergeability will
also be checked for all other branches which are listed in the
[branchOrder](config-project-config.html#branchOrder-section) section in
the project.config
file.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/current/mergeable?other-branches HTTP/1.0
```

The response will then contain a list of all other branches where this
changes could merge cleanly.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    submit_type: "MERGE_IF_NECESSARY",
    mergeable: true,
    mergeable_into: [
        "refs/heads/stable-2.7",
        "refs/heads/stable-2.8",
    ]
  }
```

### Get Submit Type

*GET
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/submit\_type*

Gets the method the server will use to submit (merge) the
change.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/current/submit_type HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  "MERGE_IF_NECESSARY"
```

### Test Submit Type

*POST
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/test.submit\_type*

Tests the submit\_type Prolog rule in the project, or the one given.

Request body may be either the Prolog code as `text/plain` or a
[RuleInput](#rule-input) object. The query parameter `filters` may be
set to `SKIP` to bypass parent project filters while testing a
project-specific
rule.

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/current/test.submit_type HTTP/1.0
  Content-Type: text/plain; charset-UTF-8

  submit_type(cherry_pick).
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  "CHERRY_PICK"
```

### Test Submit Rule

*POST
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/test.submit\_rule*

Tests the submit\_rule Prolog rule in the project, or the one given.

Request body may be either the Prolog code as `text/plain` or a
[RuleInput](#rule-input) object. The query parameter `filters` may be
set to `SKIP` to bypass parent project filters while testing a
project-specific
rule.

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/current/test.submit_rule?filters=SKIP HTTP/1.0
  Content-Type: text/plain; charset-UTF-8

  submit_rule(submit(R)) :-
    R = label('Any-Label-Name', reject(_)).
```

The response is a list of [SubmitRecord](#submit-record) entries
describing the permutations that satisfy the tested submit rule.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "status": "NOT_READY",
      "reject": {
        "Any-Label-Name": {}
      }
    }
  ]
```

When testing with the `curl` command line client the `--data-binary
@rules.pl` flag should be used to ensure all LFs are included in the
Prolog code:

``` 
  curl -X POST \
    -H 'Content-Type: text/plain; charset=UTF-8' \
    --data-binary @rules.pl \
    http://.../test.submit_rule
```

### List Revision Drafts

*GET
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/drafts/*

Lists the draft comments of a revision that belong to the calling user.

Returns a map of file paths to lists of [CommentInfo](#comment-info)
entries. The entries in the map are sorted by file
path.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/drafts/ HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java": [
      {
        "id": "TvcXrmjM",
        "line": 23,
        "message": "[nit] trailing whitespace",
        "updated": "2013-02-26 15:40:43.986000000"
      },
      {
        "id": "TveXwFiA",
        "line": 49,
        "in_reply_to": "TfYX-Iuo",
        "message": "Done",
        "updated": "2013-02-26 15:40:45.328000000"
      }
    ]
  }
```

### Create Draft

*PUT
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/drafts*

Creates a draft comment on a revision.

The new draft comment must be provided in the request body inside a
[CommentInput](#comment-input)
entity.

**Request.**

``` 
  PUT /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/drafts HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "path": "gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java",
    "line": 23,
    "message": "[nit] trailing whitespace"
  }
```

As response a [CommentInfo](#comment-info) entity is returned that
describes the draft comment.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "TvcXrmjM",
    "path": "gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java",
    "line": 23,
    "message": "[nit] trailing whitespace",
    "updated": "2013-02-26 15:40:43.986000000"
  }
```

### Get Draft

*GET
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/drafts/[{draft-id}](#draft-id)*

Retrieves a draft comment of a revision that belongs to the calling
user.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/drafts/TvcXrmjM HTTP/1.0
```

As response a [CommentInfo](#comment-info) entity is returned that
describes the draft comment.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "TvcXrmjM",
    "path": "gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java",
    "line": 23,
    "message": "[nit] trailing whitespace",
    "updated": "2013-02-26 15:40:43.986000000"
  }
```

### Update Draft

*PUT
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/drafts/[{draft-id}](#draft-id)*

Updates a draft comment on a revision.

The new draft comment must be provided in the request body inside a
[CommentInput](#comment-input)
entity.

**Request.**

``` 
  PUT /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/drafts/TvcXrmjM HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "path": "gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java",
    "line": 23,
    "message": "[nit] trailing whitespace"
  }
```

As response a [CommentInfo](#comment-info) entity is returned that
describes the draft comment.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "TvcXrmjM",
    "path": "gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java",
    "line": 23,
    "message": "[nit] trailing whitespace",
    "updated": "2013-02-26 15:40:43.986000000"
  }
```

### Delete Draft

*DELETE
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/drafts/[{draft-id}](#draft-id)*

Deletes a draft comment from a
revision.

**Request.**

``` 
  DELETE /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/drafts/TvcXrmjM HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

### List Revision Comments

*GET
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/comments/*

Lists the published comments of a revision.

As result a map is returned that maps the file path to a list of
[CommentInfo](#comment-info) entries. The entries in the map are sorted
by file path and only include file (or inline) comments. Use the [Get
Change Detail](#get-change-detail) endpoint to retrieve the general
change message (or
comment).

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/comments/ HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java": [
      {
        "id": "TvcXrmjM",
        "line": 23,
        "message": "[nit] trailing whitespace",
        "updated": "2013-02-26 15:40:43.986000000",
        "author": {
          "_account_id": 1000096,
          "name": "John Doe",
          "email": "john.doe@example.com"
        }
      },
      {
        "id": "TveXwFiA",
        "line": 49,
        "in_reply_to": "TfYX-Iuo",
        "message": "Done",
        "updated": "2013-02-26 15:40:45.328000000",
        "author": {
          "_account_id": 1000097,
          "name": "Jane Roe",
          "email": "jane.roe@example.com"
        }
      }
    ]
  }
```

### Get Comment

*GET
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/comments/[{comment-id}](#comment-id)*

Retrieves a published comment of a
revision.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/comments/TvcXrmjM HTTP/1.0
```

As response a [CommentInfo](#comment-info) entity is returned that
describes the published comment.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "TvcXrmjM",
    "path": "gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java",
    "line": 23,
    "message": "[nit] trailing whitespace",
    "updated": "2013-02-26 15:40:43.986000000",
    "author": {
      "_account_id": 1000096,
      "name": "John Doe",
      "email": "john.doe@example.com"
    }
  }
```

### Delete Comment

*DELETE
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/comments/[{comment-id}](#comment-id)*  
*POST
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/comments/[{comment-id}](#comment-id)/delete*

Deletes a published comment of a revision. Instead of deleting the whole
comment, this endpoint just replaces the comment’s message with a new
message, which contains the name of the user who deletes the comment and
the reason why it’s deleted. The reason can be provided in the request
body as a [DeleteCommentInput](#delete-comment-input) entity.

Note that only users with the [Administrate
Server](access-control.html#capability_administrateServer) global
capability are permitted to delete a comment.

Please note that some proxies prohibit request bodies for DELETE
requests. In this case, if you want to specify options, use a POST
request:

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/comments/TvcXrmjM/delete HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "reason": "contains confidential information"
  }
```

As response a [CommentInfo](#comment-info) entity is returned that
describes the updated comment.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "TvcXrmjM",
    "path": "gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java",
    "line": 23,
    "message": "Comment removed by: Administrator; Reason: contains confidential information",
    "updated": "2013-02-26 15:40:43.986000000",
    "author": {
      "_account_id": 1000096,
      "name": "John Doe",
      "email": "john.doe@example.com"
    }
  }
```

### List Robot Comments

*GET
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/robotcomments/*

Lists the [robot comments](config-robot-comments.html) of a revision.

As result a map is returned that maps the file path to a list of
[RobotCommentInfo](#robot-comment-info) entries. The entries in the map
are sorted by file
path.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/robotcomments/ HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java": [
      {
        "id": "TvcXrmjM",
        "line": 23,
        "message": "unused import",
        "updated": "2016-02-26 15:40:43.986000000",
        "author": {
          "_account_id": 1000110,
          "name": "Code Analyzer",
          "email": "code.analyzer@example.com"
        },
        "robotId": "importChecker",
        "robotRunId": "76b1375aa8626ea7149792831fe2ed85e80d9e04"
      },
      {
        "id": "TveXwFiA",
        "line": 49,
        "message": "wrong indention",
        "updated": "2016-02-26 15:40:45.328000000",
        "author": {
          "_account_id": 1000110,
          "name": "Code Analyzer",
          "email": "code.analyzer@example.com"
        },
        "robotId": "styleChecker",
        "robotRunId": "5c606c425dd45184484f9d0a2ffd725a7607839b"
      }
    ]
  }
```

### Get Robot Comment

*GET
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/robotcomments/[{comment-id}](#comment-id)*

Retrieves a [robot comment](config-robot-comments.html) of a
revision.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/robotcomments/TvcXrmjM HTTP/1.0
```

As response a [RobotCommentInfo](#robot-comment-info) entity is returned
that describes the robot comment.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "id": "TvcXrmjM",
    "line": 23,
    "message": "unused import",
    "updated": "2016-02-26 15:40:43.986000000",
    "author": {
      "_account_id": 1000110,
      "name": "Code Analyzer",
      "email": "code.analyzer@example.com"
    },
    "robotId": "importChecker",
    "robotRunId": "76b1375aa8626ea7149792831fe2ed85e80d9e04"
  }
```

### Apply Fix

*POST
/changes/[section\_title](#change-id)/revisions/[section\_title](#revision-id)/fixes/[section\_title](#fix-id)/apply*

Applies a suggested fix by creating a change edit which includes the
modifications indicated by the fix suggestion. If a change edit already
exists, it will be updated accordingly. A fix can only be applied if no
change edit exists and the fix refers to the current patch set, or the
fix refers to the patch set on which the change edit is
based.

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/fixes/8f605a55_f6aa4ecc/apply HTTP/1.0
```

If the fix was successfully applied, an [EditInfo](#edit-info)
describing the resulting change edit is returned.

**Response.**

``` 
  HTTP/1.1 200 OK
    Content-Disposition: attachment
    Content-Type: application/json; charset=UTF-8

    )]}'
    {
      "commit":{
        "parents":[
          {
            "commit":"1eee2c9d8f352483781e772f35dc586a69ff5646",
          }
        ],
        "author":{
          "name":"John Doe",
          "email":"john.doe@example.com",
          "date":"2013-05-07 15:21:27.000000000",
          "tz":120
         },
         "committer":{
           "name":"Jane Doe",
           "email":"jane.doe@example.com",
           "date":"2013-05-07 15:35:43.000000000",
           "tz":120
         },
         "subject":"Implement feature X",
         "message":"Implement feature X\n\nWith this feature ..."
      },
      "base_revision":"674ac754f91e64a0efb8087e59a176484bd534d1"
    }
```

If the application failed e.g. due to conflicts with an existing change
edit, the response "`409 Conflict`" including an error message in the
response body is returned.

**Response.**

``` 
  HTTP/1.1 409 Conflict
  Content-Disposition: attachment
  Content-Type: text/plain; charset=UTF-8

  The existing change edit could not be merged with another tree.
```

### List Files

*GET
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/files/*

Lists the files that were modified, added or deleted in a
revision.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/files/ HTTP/1.0
```

As result a map is returned that maps the [file path](#file-id) to a
list of [FileInfo](#file-info) entries. The entries in the map are
sorted by file path.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "/COMMIT_MSG": {
      "status": "A",
      "lines_inserted": 7,
      "size_delta": 551,
      "size": 551
    },
    "gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java": {
      "lines_inserted": 5,
      "lines_deleted": 3,
      "size_delta": 98,
      "size": 23348
    }
  }
```

The request parameter `reviewed` changes the response to return a list
of the paths the caller has marked as reviewed. Clients that also need
the FileInfo should make two requests.

The request parameter `q` changes the response to return a list of all
files (modified or unmodified) that contain that substring in the path
name. This is useful to implement suggestion services finding a file by
partial name.

The integer-valued request parameter `parent` changes the response to
return a list of the files which are different in this commit compared
to the given parent commit. This is useful for supporting review of
merge commits. The value is the 1-based index of the parent’s position
in the commit
object.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/files/?reviewed HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    "/COMMIT_MSG",
    "gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java",
  ]
```

### Get Content

*GET
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/files/[{file-id}](#file-id)/content*

Gets the content of a file from a certain revision.

The optional, integer-valued `parent` parameter can be specified to
request the named file from a parent commit of the specified revision.
The value is the 1-based index of the parent’s position in the commit
object. If the parameter is omitted or the value is non-positive, the
patch set is
referenced.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/files/gerrit-server%2Fsrc%2Fmain%2Fjava%2Fcom%2Fgoogle%2Fgerrit%2Fserver%2Fproject%2FRefControl.java/content HTTP/1.0
```

The content is returned as base64 encoded string. The HTTP response
Content-Type is always `text/plain`, reflecting the base64 wrapping. A
Gerrit-specific `X-FYI-Content-Type` header is returned describing the
server detected content type of the file.

If only the content type is required, callers should use HEAD to avoid
downloading the encoded file contents.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: text/plain; charset=ISO-8859-1
  X-FYI-Content-Encoding: base64
  X-FYI-Content-Type: text/xml

  Ly8gQ29weXJpZ2h0IChDKSAyMDEwIFRoZSBBbmRyb2lkIE9wZW4gU291cmNlIFByb2plY...
```

Alternatively, if the only value of the Accept request header is
`application/json` the content is returned as JSON string and
`X-FYI-Content-Encoding` is set to `json`.

### Download Content

*GET
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/files/[{file-id}](#file-id)/download*

Downloads the content of a file from a certain revision, in a safe
format that poses no risk for inadvertent execution of untrusted code.

If the content type is defined as safe, the binary file content is
returned verbatim. If the content type is not safe, the file is stored
inside a ZIP file, containing a single entry with a random,
unpredictable name having the same base and suffix as the true filename.
The ZIP file is returned in verbatim binary form.

See [Gerrit config documentation](config-gerrit.html#mimetype.name.safe)
for information about safe file type configuration.

The HTTP resource Content-Type is dependent on the file type: the
applicable type for safe files, or "application/zip" for unsafe files.

The optional, integer-valued `parent` parameter can be specified to
request the named file from a parent commit of the specified revision.
The value is the 1-based index of the parent’s position in the commit
object. If the parameter is omitted or the value non-positive, the patch
set is referenced.

Filenames are decorated with a suffix of `_new` for the current patch,
`_old` for the only parent, or `_oldN` for the Nth parent of
many.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/files/website%2Freleases%2Flogo.png/download HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment; filename="logo.png"
  Content-Type: image/png

  `[binary data for logo.png]`
```

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/files/gerrit-server%2Fsrc%2Fmain%2Fjava%2Fcom%2Fgoogle%2Fgerrit%2Fserver%2Fproject%2FRefControl.java/download?suffix=new HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: Content-Disposition:attachment; filename="RefControl_new-931cdb73ae9d97eb500a3533455b055d90b99944.java.zip"
  Content-Type:application/zip

  `[binary ZIP archive containing a single file, "RefControl_new-cb218df1337df48a0e7ab30a49a8067ac7321881.java"]`
```

### Get Diff

*GET
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/files/[{file-id}](#file-id)/diff*

Gets the diff of a file from a certain
revision.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/files/gerrit-server%2Fsrc%2Fmain%2Fjava%2Fcom%2Fgoogle%2Fgerrit%2Fserver%2Fproject%2FRefControl.java/diff HTTP/1.0
```

As response a [DiffInfo](#diff-info) entity is returned that describes
the diff.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]
  {
    "meta_a": {
      "name": "gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java",
      "content_type": "text/x-java-source",
      "lines": 372
    },
    "meta_b": {
      "name": "gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java",
      "content_type": "text/x-java-source",
      "lines": 578
    },
    "change_type": "MODIFIED",
    "diff_header": [
      "diff --git a/gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java b/gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java",
      "index 59b7670..9faf81c 100644",
      "--- a/gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java",
      "+++ b/gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java"
    ],
    "content": [
      {
        "ab": [
          "// Copyright (C) 2010 The Android Open Source Project",
          "//",
          "// Licensed under the Apache License, Version 2.0 (the \"License\");",
          "// you may not use this file except in compliance with the License.",
          "// You may obtain a copy of the License at",
          "//",
          "// http://www.apache.org/licenses/LICENSE-2.0",
          "//",
          "// Unless required by applicable law or agreed to in writing, software",
          "// distributed under the License is distributed on an \"AS IS\" BASIS,",
          "// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.",
          "// See the License for the specific language governing permissions and",
          "// limitations under the License."
        ]
      },
      {
        "b": [
          "//",
          "// Add some more lines in the header."
        ]
      },
      {
        "ab": [
          "",
          "package com.google.gerrit.server.project;",
          "",
          "import com.google.common.collect.Maps;",
          ...
        ]
      }
      ...
    ]
  }
```

If the `intraline` parameter is specified, intraline differences are
included in the
diff.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/b6b9c10649b9041884046119ab794374470a1b45/files/gerrit-server%2Fsrc%2Fmain%2Fjava%2Fcom%2Fgoogle%2Fgerrit%2Fserver%2Fproject%2FRefControl.java/diff?intraline HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]
  {
    "meta_a": {
      "name": "gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java",
      "content_type": "text/x-java-source",
      "lines": 372
    },
    "meta_b": {
      "name": "gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java",
      "content_type": "text/x-java-source",
      "lines": 578
    },
    "change_type": "MODIFIED",
    "diff_header": [
      "diff --git a/gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java b/gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java",
      "index 59b7670..9faf81c 100644",
      "--- a/gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java",
      "+++ b/gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java"
    ],
    "content": [
      ...
      {
        "a": [
          "/** Manages access control for Git references (aka branches, tags). */"
        ],
        "b": [
          "/** Manages access control for the Git references (aka branches, tags). */"
        ],
        "edit_a": [],
        "edit_b": [
          [
            31,
            4
          ]
        ]
      }
      ]
    }
```

The `base` parameter can be specified to control the base patch set from
which the diff should be generated.

The integer-valued request parameter `parent` can be specified to
control the parent commit number against which the diff should be
generated. This is useful for supporting review of merge commits. The
value is the 1-based index of the parent’s position in the commit
object.

If the `weblinks-only` parameter is specified, only the diff web links
are
returned.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/b6b9c10649b9041884046119ab794374470a1b45/files/gerrit-server%2Fsrc%2Fmain%2Fjava%2Fcom%2Fgoogle%2Fgerrit%2Fserver%2Fproject%2FRefControl.java/diff?base=2 HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]
  {
    "meta_a": {
      "name": "gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java",
      "content_type": "text/x-java-source",
      "lines": 578
    },
    "meta_b": {
      "name": "gerrit-server/src/main/java/com/google/gerrit/server/project/RefControl.java",
      "content_type": "text/x-java-source",
      "lines": 578
    },
    "change_type": "MODIFIED",
    "content": [
      {
        "skip": 578
      }
    ]
  }
```

The `whitespace` parameter can be specified to control how whitespace
differences are reported in the result. Valid values are `IGNORE_NONE`,
`IGNORE_TRAILING`, `IGNORE_LEADING_AND_TRAILING` or `IGNORE_ALL`.

The `context` parameter can be specified to control the number of lines
of surrounding context in the diff. Valid values are `ALL` or number of
lines.

### Get Blame

*GET
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/files/[{file-id}](#file-id)/blame*

Gets the blame of a file from a certain
revision.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/files/gerrit-server%2Fsrc%2Fmain%2Fjava%2Fcom%2Fgoogle%2Fgerrit%2Fserver%2Fproject%2FRefControl.java/blame HTTP/1.0
```

As response a [BlameInfo](#blame-info) entity is returned that describes
the blame.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]
  {
    [
      {
        "author": "Joe Daw",
        "id": "64e140b4de5883a4dd74d06c2b62ccd7ffd224a7",
        "time": 1421441349,
        "commit_msg": "RST test\n\nChange-Id: I11e9e24bd122253f4bb10c36dce825ac2410d646\n",
        "ranges": [
          {
            "start": 1,
            "end": 10
          },
          {
            "start": 16,
            "end": 296
          }
        ]
      },
      {
        "author": "Jane Daw",
        "id": "8d52621a0e2ac6adec73bd3a49f2371cd53137a7",
        "time": 1421825421,
        "commit_msg": "add banner\n\nChange-Id: I2eced9b2691015ae3c5138f4d0c4ca2b8fb15be9\n",
        "ranges": [
          {
            "start": 13,
            "end": 13
          }
        ]
      }
    ]
  }
```

The `base` parameter can be specified to control the base patch set from
which the blame should be generated.

### Set Reviewed

*PUT
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/files/[{file-id}](#file-id)/reviewed*

Marks a file of a revision as reviewed by the calling
user.

**Request.**

``` 
  PUT /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/files/gerrit-server%2Fsrc%2Fmain%2Fjava%2Fcom%2Fgoogle%2Fgerrit%2Fserver%2Fproject%2FRefControl.java/reviewed HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 201 Created
```

If the file was already marked as reviewed by the calling user the
response is "`200 OK`".

### Delete Reviewed

*DELETE
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/files/[{file-id}](#file-id)/reviewed*

Deletes the reviewed flag of the calling user from a file of a
revision.

**Request.**

``` 
  DELETE /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/files/gerrit-server%2Fsrc%2Fmain%2Fjava%2Fcom%2Fgoogle%2Fgerrit%2Fserver%2Fproject%2FRefControl.java/reviewed HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

### Cherry Pick Revision

*POST
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/cherrypick*

Cherry picks a revision to a destination branch.

To cherry pick a commit with no change-id associated with it, see
[CherryPickCommit](rest-api-projects.html#cherry-pick-commit).

The commit message and destination branch must be provided in the
request body inside a [CherryPickInput](#cherrypick-input) entity. If
the commit message does not specify a Change-Id, a new one is picked for
the destination
change.

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/cherrypick HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "message" : "Implementing Feature X",
    "destination" : "release-branch"
  }
```

As response a [ChangeInfo](#change-info) entity is returned that
describes the resulting cherry picked change.

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

## Revision Reviewer Endpoints

### List Revision Reviewers

*GET
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/reviewers/*

Lists the reviewers of a revision.

Please note that only the current revision is supported.

As result a list of [ReviewerInfo](#reviewer-info) entries is
returned.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/reviewers/ HTTP/1.0
```

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "approvals": {
        "Verified": "+1",
        "Code-Review": "+2"
      },
      "_account_id": 1000096,
      "name": "John Doe",
      "email": "john.doe@example.com"
    },
    {
      "approvals": {
        "Verified": " 0",
        "Code-Review": "-1"
      },
      "_account_id": 1000097,
      "name": "Jane Roe",
      "email": "jane.roe@example.com"
    }
  ]
```

### List Revision Votes

*GET
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)/reviewers/[{account-id}](rest-api-accounts.html#account-id)/votes/*

Lists the votes for a specific reviewer of the revision.

Please note that only the current revision is
supported.

**Request.**

``` 
  GET /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/reviewers/John%20Doe/votes/ HTTP/1.0
```

As result a map is returned that maps the label name to the label value.
The entries in the map are sorted by label name.

**Response.**

``` 
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json;charset=UTF-8

  )]}'
  {
    "Code-Review": -1,
    "Verified": 1,
    "Work-In-Progress": 1
  }
```

### Delete Revision Vote

*DELETE
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)
/reviewers/[{account-id}](rest-api-accounts.html#account-id)/votes/[{label-id}](#label-id)*  
*POST
/changes/[{change-id}](#change-id)/revisions/[{revision-id}](#revision-id)
/reviewers/[{account-id}](rest-api-accounts.html#account-id)/votes/[{label-id}](#label-id)/delete*

Deletes a single vote from a revision. The deletion will be possible
only if the revision is the current revision. By using this endpoint you
can prevent deleting the vote (with same label) from a newer patch set
by mistake.

Note, that even when the last vote of a reviewer is removed the reviewer
itself is still listed on the change.

Options can be provided in the request body as a
[DeleteVoteInput](#delete-vote-input)
entity.

**Request.**

``` 
  DELETE /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/reviewers/John%20Doe/votes/Code-Review HTTP/1.0
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/reviewers/John%20Doe/votes/Code-Review/delete HTTP/1.0
```

Please note that some proxies prohibit request bodies for DELETE
requests. In this case, if you want to specify options, use a POST
request:

**Request.**

``` 
  POST /changes/myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940/revisions/674ac754f91e64a0efb8087e59a176484bd534d1/reviewers/John%20Doe/votes/Code-Review/delete HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "notify": "NONE"
  }
```

**Response.**

``` 
  HTTP/1.1 204 No Content
```

## IDs

### [{account-id}](rest-api-accounts.html#account-id)

### {change-id}

Identifier that uniquely identifies one change.

This can be:

  - an ID of the change in the format "*\<project\>~\<numericId\>*"

  - an ID of the change in the format
    "*\<project\>~\<branch\>~\<Change-Id\>*", where for the branch the
    `refs/heads/` prefix can be omitted
    ("myProject~master~I8473b95934b5732ac55d26311a706c9c2bde9940")

  - a Change-Id if it uniquely identifies one change
    ("I8473b95934b5732ac55d26311a706c9c2bde9940")

  - a numeric change ID ("4247")

### {comment-id}

UUID of a published comment.

### {draft-id}

UUID of a draft comment.

### {label-id}

The name of the label.

### {file-id}

The path of the file.

The following magic paths are supported:

  - `/COMMIT_MSG`:
    
    The commit message and headers with the parent commit(s), the author
    information and the committer information.

  - `/MERGE_LIST` (for merge commits only):
    
    The list of commits that are being integrated into the destination
    branch by submitting the merge commit.

### {fix-id}

UUID of a suggested fix.

### {revision-id}

Identifier that uniquely identifies one revision of a change.

This can be:

  - the literal `current` to name the current patch set/revision

  - a commit ID ("674ac754f91e64a0efb8087e59a176484bd534d1")

  - an abbreviated commit ID that uniquely identifies one revision of
    the change ("674ac754"), at least 4 digits are required

  - a legacy numeric patch number ("1" for first patch set of the
    change)

  - "0" or the literal `edit` for a change edit

## JSON Entities

### AbandonInput

The `AbandonInput` entity contains information for abandoning a change.

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
<td><p><code>message</code></p></td>
<td><p>optional</p></td>
<td><p>Message to be added as review comment to the change when abandoning the change.</p></td>
</tr>
<tr class="even">
<td><p><code>notify</code></p></td>
<td><p>optional</p></td>
<td><p>Notify handling that defines to whom email notifications should be sent after the change is abandoned.<br />
Allowed values are <code>NONE</code>, <code>OWNER</code>, <code>OWNER_REVIEWERS</code> and <code>ALL</code>.<br />
If not set, the default is <code>ALL</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>notify_details</code></p></td>
<td><p>optional</p></td>
<td><p>Additional information about whom to notify about the update as a map of recipient type to <a href="#notify-info">NotifyInfo</a> entity.</p></td>
</tr>
</tbody>
</table>

### ActionInfo

The `ActionInfo` entity describes a REST API call the client can make to
manipulate a resource. These are frequently implemented by plugins and
may be discovered at runtime.

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
<td><p><code>method</code></p></td>
<td><p>optional</p></td>
<td><p>HTTP method to use with the action. Most actions use <code>POST</code>, <code>PUT</code> or <code>DELETE</code> to cause state changes.</p></td>
</tr>
<tr class="even">
<td><p><code>label</code></p></td>
<td><p>optional</p></td>
<td><p>Short title to display to a user describing the action. In the Gerrit web interface the label is used as the text on the button presented in the UI.</p></td>
</tr>
<tr class="odd">
<td><p><code>title</code></p></td>
<td><p>optional</p></td>
<td><p>Longer text to display describing the action. In a web UI this should be the title attribute of the element, displaying when the user hovers the mouse.</p></td>
</tr>
<tr class="even">
<td><p><code>enabled</code></p></td>
<td><p>optional</p></td>
<td><p>If true the action is permitted at this time and the caller is likely allowed to execute it. This may change if state is updated at the server or permissions are modified. Not present if false.</p></td>
</tr>
</tbody>
</table>

### AddReviewerResult

The `AddReviewerResult` entity describes the result of adding a reviewer
to a change.

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
<td><p><code>input</code></p></td>
<td></td>
<td><p>Value of the <code>reviewer</code> field from <a href="#reviewer-input">ReviewerInput</a> set while adding the reviewer.</p></td>
</tr>
<tr class="even">
<td><p><code>reviewers</code></p></td>
<td><p>optional</p></td>
<td><p>The newly added reviewers as a list of <a href="#reviewer-info">ReviewerInfo</a> entities.</p></td>
</tr>
<tr class="odd">
<td><p><code>ccs</code></p></td>
<td><p>optional</p></td>
<td><p>The newly CCed accounts as a list of <a href="#reviewer-info">ReviewerInfo</a> entities. This field will only appear if the requested <code>state</code> for the reviewer was <code>CC</code> <strong>and</strong> NoteDb is enabled on the server.</p></td>
</tr>
<tr class="even">
<td><p><code>error</code></p></td>
<td><p>optional</p></td>
<td><p>Error message explaining why the reviewer could not be added.<br />
If a group was specified in the input and an error is returned, it means that none of the members were added as reviewer.</p></td>
</tr>
<tr class="odd">
<td><p><code>confirm</code></p></td>
<td><p><code>false</code> if not set</p></td>
<td><p>Whether adding the reviewer requires confirmation.</p></td>
</tr>
</tbody>
</table>

### ApprovalInfo

The `ApprovalInfo` entity contains information about an approval from a
user for a label on a change.

`ApprovalInfo` has the same fields as
[AccountInfo](rest-api-accounts.html#account-info). In addition
`ApprovalInfo` has the following fields:

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
<td><p><code>value</code></p></td>
<td><p>optional</p></td>
<td><p>The vote that the user has given for the label. If present and zero, the user is permitted to vote on the label. If absent, the user is not permitted to vote on that label.</p></td>
</tr>
<tr class="even">
<td><p><code>permitted_voting_range</code></p></td>
<td><p>optional</p></td>
<td><p>The <a href="#voting-range-info">VotingRangeInfo</a> the user is authorized to vote on that label. If present, the user is permitted to vote on the label regarding the range values. If absent, the user is not permitted to vote on that label.</p></td>
</tr>
<tr class="odd">
<td><p><code>date</code></p></td>
<td><p>optional</p></td>
<td><p>The time and date describing when the approval was made.</p></td>
</tr>
<tr class="even">
<td><p><code>tag</code></p></td>
<td><p>optional</p></td>
<td><p>Value of the <code>tag</code> field from <a href="#review-input">ReviewInput</a> set while posting the review. NOTE: To apply different tags on on different votes/comments multiple invocations of the REST call are required.</p></td>
</tr>
<tr class="odd">
<td><p><code>post_submit</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>If true, this vote was made after the change was submitted.</p></td>
</tr>
</tbody>
</table>

### AssigneeInput

The `AssigneeInput` entity contains the identity of the user to be set
as assignee.

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
<td><p><code>assignee</code></p></td>
<td></td>
<td><p>The <a href="rest-api-accounts.html#account-id">ID</a> of one account that should be added as assignee.</p></td>
</tr>
</tbody>
</table>

### BlameInfo

The `BlameInfo` entity stores the commit metadata with the row
coordinates where it applies.

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
<td><p><code>author</code></p></td>
<td><p>The author of the commit.</p></td>
</tr>
<tr class="even">
<td><p><code>id</code></p></td>
<td><p>The id of the commit.</p></td>
</tr>
<tr class="odd">
<td><p><code>time</code></p></td>
<td><p>Commit time.</p></td>
</tr>
<tr class="even">
<td><p><code>commit_msg</code></p></td>
<td><p>The commit message.</p></td>
</tr>
<tr class="odd">
<td><p><code>ranges</code></p></td>
<td><p>The blame row coordinates as <a href="#range-info">RangeInfo</a> entities.</p></td>
</tr>
</tbody>
</table>

### ChangeEditInput

The `ChangeEditInput` entity contains information for restoring a path
within change edit.

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
<td><p><code>restore_path</code></p></td>
<td><p>optional</p></td>
<td><p>Path to file to restore.</p></td>
</tr>
<tr class="even">
<td><p><code>old_path</code></p></td>
<td><p>optional</p></td>
<td><p>Old path to file to rename.</p></td>
</tr>
<tr class="odd">
<td><p><code>new_path</code></p></td>
<td><p>optional</p></td>
<td><p>New path to file to rename.</p></td>
</tr>
</tbody>
</table>

### ChangeEditMessageInput

The `ChangeEditMessageInput` entity contains information for changing
the commit message within a change edit.

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
<td><p><code>message</code></p></td>
<td></td>
<td><p>New commit message.</p></td>
</tr>
</tbody>
</table>

### ChangeInfo

The `ChangeInfo` entity contains information about a change.

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
<td><p>The ID of the change in the format &quot;<em>&lt;project&gt;~&lt;branch&gt;~&lt;Change-Id&gt;</em>&quot;, where <em>project</em>, <em>branch</em> and <em>Change-Id</em> are URL encoded. For <em>branch</em> the <code>refs/heads/</code> prefix is omitted.</p></td>
</tr>
<tr class="even">
<td><p><code>project</code></p></td>
<td></td>
<td><p>The name of the project.</p></td>
</tr>
<tr class="odd">
<td><p><code>branch</code></p></td>
<td></td>
<td><p>The name of the target branch.<br />
The <code>refs/heads/</code> prefix is omitted.</p></td>
</tr>
<tr class="even">
<td><p><code>topic</code></p></td>
<td><p>optional</p></td>
<td><p>The topic to which this change belongs.</p></td>
</tr>
<tr class="odd">
<td><p><code>assignee</code></p></td>
<td><p>optional</p></td>
<td><p>The assignee of the change as an <a href="rest-api-accounts.html#account-info">AccountInfo</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>hashtags</code></p></td>
<td><p>optional</p></td>
<td><p>List of hashtags that are set on the change (only populated when NoteDb is enabled).</p></td>
</tr>
<tr class="odd">
<td><p><code>change_id</code></p></td>
<td></td>
<td><p>The Change-Id of the change.</p></td>
</tr>
<tr class="even">
<td><p><code>subject</code></p></td>
<td></td>
<td><p>The subject of the change (header line of the commit message).</p></td>
</tr>
<tr class="odd">
<td><p><code>status</code></p></td>
<td></td>
<td><p>The status of the change (<code>NEW</code>, <code>MERGED</code>, <code>ABANDONED</code>).</p></td>
</tr>
<tr class="even">
<td><p><code>created</code></p></td>
<td></td>
<td><p>The <a href="rest-api.html#timestamp">timestamp</a> of when the change was created.</p></td>
</tr>
<tr class="odd">
<td><p><code>updated</code></p></td>
<td></td>
<td><p>The <a href="rest-api.html#timestamp">timestamp</a> of when the change was last updated.</p></td>
</tr>
<tr class="even">
<td><p><code>submitted</code></p></td>
<td><p>only set for merged changes</p></td>
<td><p>The <a href="rest-api.html#timestamp">timestamp</a> of when the change was submitted.</p></td>
</tr>
<tr class="odd">
<td><p><code>submitter</code></p></td>
<td><p>only set for merged changes</p></td>
<td><p>The user who submitted the change, as an <a href="rest-api-accounts.html#account-info">AccountInfo</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>starred</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the calling user has starred this change with the default label.</p></td>
</tr>
<tr class="odd">
<td><p><code>stars</code></p></td>
<td><p>optional</p></td>
<td><p>A list of star labels that are applied by the calling user to this change. The labels are lexicographically sorted.</p></td>
</tr>
<tr class="even">
<td><p><code>reviewed</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the change was reviewed by the calling user. Only set if <a href="#reviewed">reviewed</a> is requested.</p></td>
</tr>
<tr class="odd">
<td><p><code>submit_type</code></p></td>
<td><p>optional</p></td>
<td><p>The <a href="project-configuration.html#submit_type">submit type</a> of the change.<br />
Not set for merged changes.</p></td>
</tr>
<tr class="even">
<td><p><code>mergeable</code></p></td>
<td><p>optional</p></td>
<td><p>Whether the change is mergeable.<br />
Not set for merged changes, or if the change has not yet been tested.</p></td>
</tr>
<tr class="odd">
<td><p><code>submittable</code></p></td>
<td><p>optional</p></td>
<td><p>Whether the change has been approved by the project submit rules.<br />
Only set if <a href="#submittable">requested</a>.</p></td>
</tr>
<tr class="even">
<td><p><code>insertions</code></p></td>
<td></td>
<td><p>Number of inserted lines.</p></td>
</tr>
<tr class="odd">
<td><p><code>deletions</code></p></td>
<td></td>
<td><p>Number of deleted lines.</p></td>
</tr>
<tr class="even">
<td><p><code>unresolved_comment_count</code></p></td>
<td><p>optional</p></td>
<td><p>Number of unresolved comments. Not set if the current change index doesn’t have the data.</p></td>
</tr>
<tr class="odd">
<td><p><code>_number</code></p></td>
<td></td>
<td><p>The legacy numeric ID of the change.</p></td>
</tr>
<tr class="even">
<td><p><code>owner</code></p></td>
<td></td>
<td><p>The owner of the change as an <a href="rest-api-accounts.html#account-info">AccountInfo</a> entity.</p></td>
</tr>
<tr class="odd">
<td><p><code>actions</code></p></td>
<td><p>optional</p></td>
<td><p>Actions the caller might be able to perform on this revision. The information is a map of view name to <a href="#action-info">ActionInfo</a> entities.</p></td>
</tr>
<tr class="even">
<td><p><code>labels</code></p></td>
<td><p>optional</p></td>
<td><p>The labels of the change as a map that maps the label names to <a href="#label-info">LabelInfo</a> entries.<br />
Only set if <a href="#labels">labels</a> or <a href="#detailed-labels">detailed labels</a> are requested.</p></td>
</tr>
<tr class="odd">
<td><p><code>permitted_labels</code></p></td>
<td><p>optional</p></td>
<td><p>A map of the permitted labels that maps a label name to the list of values that are allowed for that label.<br />
Only set if <a href="#detailed-labels">detailed labels</a> are requested.</p></td>
</tr>
<tr class="even">
<td><p><code>removable_reviewers</code></p></td>
<td><p>optional</p></td>
<td><p>The reviewers that can be removed by the calling user as a list of <a href="rest-api-accounts.html#account-info">AccountInfo</a> entities.<br />
Only set if <a href="#detailed-labels">detailed labels</a> are requested.</p></td>
</tr>
<tr class="odd">
<td><p><code>reviewers</code></p></td>
<td><p>optional</p></td>
<td><p>The reviewers as a map that maps a reviewer state to a list of <a href="rest-api-accounts.html#account-info">AccountInfo</a> entities. Possible reviewer states are <code>REVIEWER</code>, <code>CC</code> and <code>REMOVED</code>.<br />
<code>REVIEWER</code>: Users with at least one non-zero vote on the change.<br />
<code>CC</code>: Users that were added to the change, but have not voted.<br />
<code>REMOVED</code>: Users that were previously reviewers on the change, but have been removed.<br />
Only set if <a href="#detailed-labels">detailed labels</a> are requested.</p></td>
</tr>
<tr class="even">
<td><p><code>pending_reviewers</code></p></td>
<td><p>optional</p></td>
<td><p>Updates to <code>reviewers</code> that have been made while the change was in the WIP state. Only present on WIP changes and only if there are pending reviewer updates to report. These are reviewers who have not yet been notified about being added to or removed from the change.<br />
Only set if <a href="#detailed-labels">detailed labels</a> are requested.</p></td>
</tr>
<tr class="odd">
<td><p><code>reviewer_updates</code></p></td>
<td><p>optional</p></td>
<td><p>Updates to reviewers set for the change as <a href="#review-update-info">ReviewerUpdateInfo</a> entities. Only set if <a href="#reviewer-updates">reviewer updates</a> are requested and if NoteDb is enabled.</p></td>
</tr>
<tr class="even">
<td><p><code>messages</code></p></td>
<td><p>optional</p></td>
<td><p>Messages associated with the change as a list of <a href="#change-message-info">ChangeMessageInfo</a> entities.<br />
Only set if <a href="#messages">messages</a> are requested.</p></td>
</tr>
<tr class="odd">
<td><p><code>current_revision</code></p></td>
<td><p>optional</p></td>
<td><p>The commit ID of the current patch set of this change.<br />
Only set if <a href="#current-revision">the current revision</a> is requested or if <a href="#all-revisions">all revisions</a> are requested.</p></td>
</tr>
<tr class="even">
<td><p><code>revisions</code></p></td>
<td><p>optional</p></td>
<td><p>All patch sets of this change as a map that maps the commit ID of the patch set to a <a href="#revision-info">RevisionInfo</a> entity.<br />
Only set if <a href="#current-revision">the current revision</a> is requested (in which case it will only contain a key for the current revision) or if <a href="#all-revisions">all revisions</a> are requested.</p></td>
</tr>
<tr class="odd">
<td><p><code>tracking_ids</code></p></td>
<td><p>optional</p></td>
<td><p>A list of <a href="#tracking-id-info">TrackingIdInfo</a> entities describing references to external tracking systems. Only set if <a href="#tracking-ids">tracking ids</a> are requested.</p></td>
</tr>
<tr class="even">
<td><p><code>_more_changes</code></p></td>
<td><p>optional, not set if <code>false</code></p></td>
<td><p>Whether the query would deliver more results if not limited.<br />
Only set on the last change that is returned.</p></td>
</tr>
<tr class="odd">
<td><p><code>problems</code></p></td>
<td><p>optional</p></td>
<td><p>A list of <a href="#problem-info">ProblemInfo</a> entities describing potential problems with this change. Only set if <a href="#check">CHECK</a> is set.</p></td>
</tr>
<tr class="even">
<td><p><code>is_private</code></p></td>
<td><p>optional, not set if <code>false</code></p></td>
<td><p>When present, change is marked as private.</p></td>
</tr>
<tr class="odd">
<td><p><code>work_in_progress</code></p></td>
<td><p>optional, not set if <code>false</code></p></td>
<td><p>When present, change is marked as Work In Progress.</p></td>
</tr>
<tr class="even">
<td><p><code>has_review_started</code></p></td>
<td><p>optional, not set if <code>false</code></p></td>
<td><p>When present, change has been marked Ready at some point in time.</p></td>
</tr>
<tr class="odd">
<td><p><code>revert_of</code></p></td>
<td><p>optional</p></td>
<td><p>The numeric Change-Id of the change that this change reverts.</p></td>
</tr>
</tbody>
</table>

### ChangeInput

The `ChangeInput` entity contains information about creating a new
change.

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
<td><p><code>project</code></p></td>
<td></td>
<td><p>The name of the project.</p></td>
</tr>
<tr class="even">
<td><p><code>branch</code></p></td>
<td></td>
<td><p>The name of the target branch.<br />
The <code>refs/heads/</code> prefix is omitted.</p></td>
</tr>
<tr class="odd">
<td><p><code>subject</code></p></td>
<td></td>
<td><p>The subject of the change (header line of the commit message).</p></td>
</tr>
<tr class="even">
<td><p><code>topic</code></p></td>
<td><p>optional</p></td>
<td><p>The topic to which this change belongs.</p></td>
</tr>
<tr class="odd">
<td><p><code>status</code></p></td>
<td><p>optional, default to <code>NEW</code></p></td>
<td><p>The status of the change (only <code>NEW</code> accepted here).</p></td>
</tr>
<tr class="even">
<td><p><code>is_private</code></p></td>
<td><p>optional, default to <code>false</code></p></td>
<td><p>Whether the new change should be marked as private.</p></td>
</tr>
<tr class="odd">
<td><p><code>work_in_progress</code></p></td>
<td><p>optional, default to <code>false</code></p></td>
<td><p>Whether the new change should be set to work in progress.</p></td>
</tr>
<tr class="even">
<td><p><code>base_change</code></p></td>
<td><p>optional</p></td>
<td><p>A <a href="#change-id">{change-id}</a> that identifies the base change for a create change operation.</p></td>
</tr>
<tr class="odd">
<td><p><code>new_branch</code></p></td>
<td><p>optional, default to <code>false</code></p></td>
<td><p>Allow creating a new branch when set to <code>true</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>merge</code></p></td>
<td><p>optional</p></td>
<td><p>The detail of a merge commit as a <a href="#merge-input">MergeInput</a> entity.</p></td>
</tr>
<tr class="odd">
<td><p><code>notify</code></p></td>
<td><p>optional</p></td>
<td><p>Notify handling that defines to whom email notifications should be sent after the change is created.<br />
Allowed values are <code>NONE</code>, <code>OWNER</code>, <code>OWNER_REVIEWERS</code> and <code>ALL</code>.<br />
If not set, the default is <code>ALL</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>notify_details</code></p></td>
<td><p>optional</p></td>
<td><p>Additional information about whom to notify about the change creation as a map of recipient type to <a href="#notify-info">NotifyInfo</a> entity.</p></td>
</tr>
</tbody>
</table>

### ChangeMessageInfo

The `ChangeMessageInfo` entity contains information about a message
attached to a change.

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
<td><p>The ID of the message.</p></td>
</tr>
<tr class="even">
<td><p><code>author</code></p></td>
<td><p>optional</p></td>
<td><p>Author of the message as an <a href="rest-api-accounts.html#account-info">AccountInfo</a> entity.<br />
Unset if written by the Gerrit system.</p></td>
</tr>
<tr class="odd">
<td><p><code>real_author</code></p></td>
<td><p>optional</p></td>
<td><p>Real author of the message as an <a href="rest-api-accounts.html#account-info">AccountInfo</a> entity.<br />
Set if the message was posted on behalf of another user.</p></td>
</tr>
<tr class="even">
<td><p><code>date</code></p></td>
<td></td>
<td><p>The <a href="rest-api.html#timestamp">timestamp</a> this message was posted.</p></td>
</tr>
<tr class="odd">
<td><p><code>message</code></p></td>
<td></td>
<td><p>The text left by the user.</p></td>
</tr>
<tr class="even">
<td><p><code>tag</code></p></td>
<td><p>optional</p></td>
<td><p>Value of the <code>tag</code> field from <a href="#review-input">ReviewInput</a> set while posting the review. NOTE: To apply different tags on on different votes/comments multiple invocations of the REST call are required.</p></td>
</tr>
<tr class="odd">
<td><p><code>_revision_number</code></p></td>
<td><p>optional</p></td>
<td><p>Which patchset (if any) generated this message.</p></td>
</tr>
</tbody>
</table>

### CherryPickInput

The `CherryPickInput` entity contains information for cherry-picking a
change to a new branch.

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
<td><p><code>message</code></p></td>
<td></td>
<td><p>Commit message for the cherry-picked change</p></td>
</tr>
<tr class="even">
<td><p><code>destination</code></p></td>
<td></td>
<td><p>Destination branch</p></td>
</tr>
<tr class="odd">
<td><p><code>base</code></p></td>
<td><p>optional</p></td>
<td><p>40-hex digit SHA-1 of the commit which will be the parent commit of the newly created change. If set, it must be a merged commit or a change revision on the destination branch.</p></td>
</tr>
<tr class="even">
<td><p><code>parent</code></p></td>
<td><p>optional, defaults to 1</p></td>
<td><p>Number of the parent relative to which the cherry-pick should be considered.</p></td>
</tr>
<tr class="odd">
<td><p><code>notify</code></p></td>
<td><p>optional</p></td>
<td><p>Notify handling that defines to whom email notifications should be sent after the cherry-pick.<br />
Allowed values are <code>NONE</code>, <code>OWNER</code>, <code>OWNER_REVIEWERS</code> and <code>ALL</code>.<br />
If not set, the default is <code>NONE</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>notify_details</code></p></td>
<td><p>optional</p></td>
<td><p>Additional information about whom to notify about the update as a map of recipient type to <a href="#notify-info">NotifyInfo</a> entity.</p></td>
</tr>
<tr class="odd">
<td><p><code>keep_reviewers</code></p></td>
<td><p>optional, defaults to false</p></td>
<td><p>If true, carries reviewers and ccs over from original change to newly created one.</p></td>
</tr>
</tbody>
</table>

### CommentInfo

The `CommentInfo` entity contains information about an inline comment.

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
<td><p><code>patch_set</code></p></td>
<td><p>optional</p></td>
<td><p>The patch set number for the comment; only set in contexts where<br />
comments may be returned for multiple patch sets.</p></td>
</tr>
<tr class="even">
<td><p><code>id</code></p></td>
<td></td>
<td><p>The URL encoded UUID of the comment.</p></td>
</tr>
<tr class="odd">
<td><p><code>path</code></p></td>
<td><p>optional</p></td>
<td><p>The path of the file for which the inline comment was done.<br />
Not set if returned in a map where the key is the file path.</p></td>
</tr>
<tr class="even">
<td><p><code>side</code></p></td>
<td><p>optional</p></td>
<td><p>The side on which the comment was added.<br />
Allowed values are <code>REVISION</code> and <code>PARENT</code>.<br />
If not set, the default is <code>REVISION</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>parent</code></p></td>
<td><p>optional</p></td>
<td><p>The 1-based parent number. Used only for merge commits when <code>side == PARENT</code>. When not set the comment is for the auto-merge tree.</p></td>
</tr>
<tr class="even">
<td><p><code>line</code></p></td>
<td><p>optional</p></td>
<td><p>The number of the line for which the comment was done.<br />
If range is set, this equals the end line of the range.<br />
If neither line nor range is set, it’s a file comment.</p></td>
</tr>
<tr class="odd">
<td><p><code>range</code></p></td>
<td><p>optional</p></td>
<td><p>The range of the comment as a <a href="#comment-range">CommentRange</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>in_reply_to</code></p></td>
<td><p>optional</p></td>
<td><p>The URL encoded UUID of the comment to which this comment is a reply.</p></td>
</tr>
<tr class="odd">
<td><p><code>message</code></p></td>
<td><p>optional</p></td>
<td><p>The comment message.</p></td>
</tr>
<tr class="even">
<td><p><code>updated</code></p></td>
<td></td>
<td><p>The <a href="rest-api.html#timestamp">timestamp</a> of when this comment was written.</p></td>
</tr>
<tr class="odd">
<td><p><code>author</code></p></td>
<td><p>optional</p></td>
<td><p>The author of the message as an <a href="rest-api-accounts.html#account-info">AccountInfo</a> entity.<br />
Unset for draft comments, assumed to be the calling user.</p></td>
</tr>
<tr class="even">
<td><p><code>tag</code></p></td>
<td><p>optional</p></td>
<td><p>Value of the <code>tag</code> field from <a href="#review-input">ReviewInput</a> set while posting the review. NOTE: To apply different tags on on different votes/comments multiple invocations of the REST call are required.</p></td>
</tr>
<tr class="odd">
<td><p><code>unresolved</code></p></td>
<td><p>optional</p></td>
<td><p>Whether or not the comment must be addressed by the user. The state of resolution of a comment thread is stored in the last comment in that thread chronologically.</p></td>
</tr>
</tbody>
</table>

### CommentInput

The `CommentInput` entity contains information for creating an inline
comment.

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
<td><p>optional</p></td>
<td><p>The URL encoded UUID of the comment if an existing draft comment should be updated.</p></td>
</tr>
<tr class="even">
<td><p><code>path</code></p></td>
<td><p>optional</p></td>
<td><p>The path of the file for which the inline comment should be added.<br />
Doesn’t need to be set if contained in a map where the key is the file path.</p></td>
</tr>
<tr class="odd">
<td><p><code>side</code></p></td>
<td><p>optional</p></td>
<td><p>The side on which the comment should be added.<br />
Allowed values are <code>REVISION</code> and <code>PARENT</code>.<br />
If not set, the default is <code>REVISION</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>line</code></p></td>
<td><p>optional</p></td>
<td><p>The number of the line for which the comment should be added.<br />
<code>0</code> if it is a file comment.<br />
If neither line nor range is set, a file comment is added.<br />
If range is set, this value is ignored in favor of the <code>end_line</code> of the range.</p></td>
</tr>
<tr class="odd">
<td><p><code>range</code></p></td>
<td><p>optional</p></td>
<td><p>The range of the comment as a <a href="#comment-range">CommentRange</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>in_reply_to</code></p></td>
<td><p>optional</p></td>
<td><p>The URL encoded UUID of the comment to which this comment is a reply.</p></td>
</tr>
<tr class="odd">
<td><p><code>updated</code></p></td>
<td><p>optional</p></td>
<td><p>The <a href="rest-api.html#timestamp">timestamp</a> of this comment.<br />
Accepted but ignored.</p></td>
</tr>
<tr class="even">
<td><p><code>message</code></p></td>
<td><p>optional</p></td>
<td><p>The comment message.<br />
If not set and an existing draft comment is updated, the existing draft comment is deleted.</p></td>
</tr>
<tr class="odd">
<td><p><code>tag</code></p></td>
<td><p>optional, drafts only</p></td>
<td><p>Value of the <code>tag</code> field. Only allowed on <a href="#create-draft">draft comment</a><br />
inputs; for published comments, use the <code>tag</code> field in<br />
link#review-input[ReviewInput]</p></td>
</tr>
<tr class="even">
<td><p><code>unresolved</code></p></td>
<td><p>optional</p></td>
<td><p>Whether or not the comment must be addressed by the user. This value will default to false if the comment is an orphan, or the value of the <code>in_reply_to</code> comment if it is supplied.</p></td>
</tr>
</tbody>
</table>

### CommentRange

The `CommentRange` entity describes the range of an inline comment.

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
<td><p><code>start_line</code></p></td>
<td></td>
<td><p>The start line number of the range. (1-based, inclusive)</p></td>
</tr>
<tr class="even">
<td><p><code>start_character</code></p></td>
<td></td>
<td><p>The character position in the start line. (0-based, inclusive)</p></td>
</tr>
<tr class="odd">
<td><p><code>end_line</code></p></td>
<td></td>
<td><p>The end line number of the range. (1-based, exclusive)</p></td>
</tr>
<tr class="even">
<td><p><code>end_character</code></p></td>
<td></td>
<td><p>The character position in the end line. (0-based, exclusive)</p></td>
</tr>
</tbody>
</table>

### CommitInfo

The `CommitInfo` entity contains information about a commit.

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
<td><p><code>commit</code></p></td>
<td><p>Optional</p></td>
<td><p>The commit ID. Not set if included in a <a href="#revision-info">RevisionInfo</a> entity that is contained in a map which has the commit ID as key.</p></td>
</tr>
<tr class="even">
<td><p><code>parents</code></p></td>
<td></td>
<td><p>The parent commits of this commit as a list of <a href="#commit-info">CommitInfo</a> entities. In each parent only the <code>commit</code> and <code>subject</code> fields are populated.</p></td>
</tr>
<tr class="odd">
<td><p><code>author</code></p></td>
<td></td>
<td><p>The author of the commit as a <a href="#git-person-info">GitPersonInfo</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>committer</code></p></td>
<td></td>
<td><p>The committer of the commit as a <a href="#git-person-info">GitPersonInfo</a> entity.</p></td>
</tr>
<tr class="odd">
<td><p><code>subject</code></p></td>
<td></td>
<td><p>The subject of the commit (header line of the commit message).</p></td>
</tr>
<tr class="even">
<td><p><code>message</code></p></td>
<td></td>
<td><p>The commit message.</p></td>
</tr>
<tr class="odd">
<td><p><code>web_links</code></p></td>
<td><p>optional</p></td>
<td><p>Links to the commit in external sites as a list of <a href="#web-link-info">WebLinkInfo</a> entities.</p></td>
</tr>
</tbody>
</table>

### CommitMessageInput

The `CommitMessageInput` entity contains information for changing the
commit message of a change.

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
<td><p><code>message</code></p></td>
<td></td>
<td><p>New commit message.</p></td>
</tr>
<tr class="even">
<td><p><code>notify</code></p></td>
<td><p>optional</p></td>
<td><p>Notify handling that defines to whom email notifications should be sent after the commit message was updated.<br />
Allowed values are <code>NONE</code>, <code>OWNER</code>, <code>OWNER_REVIEWERS</code> and <code>ALL</code>.<br />
If not set, the default is <code>OWNER</code> for WIP changes and <code>ALL</code> otherwise.</p></td>
</tr>
<tr class="odd">
<td><p><code>notify_details</code></p></td>
<td><p>optional</p></td>
<td><p>Additional information about whom to notify about the update as a map of recipient type to <a href="#notify-info">NotifyInfo</a> entity.</p></td>
</tr>
</tbody>
</table>

### DeleteCommentInput

The `DeleteCommentInput` entity contains the option for deleting a
comment.

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
<td><p><code>reason</code></p></td>
<td><p>optional</p></td>
<td><p>The reason why the comment should be deleted.<br />
If set, the comment’s message will be replaced with &quot;Comment removed by: <code>name</code>; Reason: <code>reason</code>&quot;, or just &quot;Comment removed by: <code>name</code>.&quot; if not set.</p></td>
</tr>
</tbody>
</table>

### DeleteReviewerInput

The `DeleteReviewerInput` entity contains options for the deletion of a
reviewer.

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
<td><p><code>notify</code></p></td>
<td><p>optional</p></td>
<td><p>Notify handling that defines to whom email notifications should be sent after the reviewer is deleted.<br />
Allowed values are <code>NONE</code>, <code>OWNER</code>, <code>OWNER_REVIEWERS</code> and <code>ALL</code>.<br />
If not set, the default is <code>ALL</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>notify_details</code></p></td>
<td><p>optional</p></td>
<td><p>Additional information about whom to notify about the update as a map of recipient type to <a href="#notify-info">NotifyInfo</a> entity.</p></td>
</tr>
</tbody>
</table>

### DeleteVoteInput

The `DeleteVoteInput` entity contains options for the deletion of a
vote.

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
<td><p>The label for which the vote should be deleted.<br />
If set, must match the label in the URL.</p></td>
</tr>
<tr class="even">
<td><p><code>notify</code></p></td>
<td><p>optional</p></td>
<td><p>Notify handling that defines to whom email notifications should be sent after the vote is deleted.<br />
Allowed values are <code>NONE</code>, <code>OWNER</code>, <code>OWNER_REVIEWERS</code> and <code>ALL</code>.<br />
If not set, the default is <code>ALL</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>notify_details</code></p></td>
<td><p>optional</p></td>
<td><p>Additional information about whom to notify about the update as a map of recipient type to <a href="#notify-info">NotifyInfo</a> entity.</p></td>
</tr>
</tbody>
</table>

### DescriptionInput

The `DescriptionInput` entity contains information for setting a
description.

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
<td><p><code>description</code></p></td>
<td><p>The description text.</p></td>
</tr>
</tbody>
</table>

### DiffContent

The `DiffContent` entity contains information about the content
differences in a file.

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
<td><p><code>a</code></p></td>
<td><p>optional</p></td>
<td><p>Content only in the file on side A (deleted in B).</p></td>
</tr>
<tr class="even">
<td><p><code>b</code></p></td>
<td><p>optional</p></td>
<td><p>Content only in the file on side B (added in B).</p></td>
</tr>
<tr class="odd">
<td><p><code>ab</code></p></td>
<td><p>optional</p></td>
<td><p>Content in the file on both sides (unchanged).</p></td>
</tr>
<tr class="even">
<td><p><code>edit_a</code></p></td>
<td><p>only present during a replace, i.e. both <code>a</code> and <code>b</code> are present</p></td>
<td><p>Text sections deleted from side A as a <a href="#diff-intraline-info">DiffIntralineInfo</a> entity.</p></td>
</tr>
<tr class="odd">
<td><p><code>edit_b</code></p></td>
<td><p>only present during a replace, i.e. both <code>a</code> and <code>b</code> are present</p></td>
<td><p>Text sections inserted in side B as a <a href="#diff-intraline-info">DiffIntralineInfo</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>due_to_rebase</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Indicates whether this entry was introduced by a rebase.</p></td>
</tr>
<tr class="odd">
<td><p><code>skip</code></p></td>
<td><p>optional</p></td>
<td><p>count of lines skipped on both sides when the file is too large to include all common lines.</p></td>
</tr>
<tr class="even">
<td><p><code>common</code></p></td>
<td><p>optional</p></td>
<td><p>Set to <code>true</code> if the region is common according to the requested ignore-whitespace parameter, but a and b contain differing amounts of whitespace. When present and true a and b are used instead of ab.</p></td>
</tr>
</tbody>
</table>

### DiffFileMetaInfo

The `DiffFileMetaInfo` entity contains meta information about a file
diff.

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
<td></td>
<td><p>The name of the file.</p></td>
</tr>
<tr class="even">
<td><p><code>content_type</code></p></td>
<td></td>
<td><p>The content type of the file.</p></td>
</tr>
<tr class="odd">
<td><p><code>lines</code></p></td>
<td></td>
<td><p>The total number of lines in the file.</p></td>
</tr>
<tr class="even">
<td><p><code>web_links</code></p></td>
<td><p>optional</p></td>
<td><p>Links to the file in external sites as a list of <a href="rest-api-changes.html#web-link-info">WebLinkInfo</a> entries.</p></td>
</tr>
</tbody>
</table>

### DiffInfo

The `DiffInfo` entity contains information about the diff of a file in a
revision.

If the [weblinks-only](#weblinks-only) parameter is specified, only the
`web_links` field is set.

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
<td><p><code>meta_a</code></p></td>
<td><p>not present when the file is added</p></td>
<td><p>Meta information about the file on side A as a <a href="#diff-file-meta-info">DiffFileMetaInfo</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>meta_b</code></p></td>
<td><p>not present when the file is deleted</p></td>
<td><p>Meta information about the file on side B as a <a href="#diff-file-meta-info">DiffFileMetaInfo</a> entity.</p></td>
</tr>
<tr class="odd">
<td><p><code>change_type</code></p></td>
<td></td>
<td><p>The type of change (<code>ADDED</code>, <code>MODIFIED</code>, <code>DELETED</code>, <code>RENAMED</code> <code>COPIED</code>, <code>REWRITE</code>).</p></td>
</tr>
<tr class="even">
<td><p><code>intraline_status</code></p></td>
<td><p>only set when the <code>intraline</code> parameter was specified in the request</p></td>
<td><p>Intraline status (<code>OK</code>, <code>ERROR</code>, <code>TIMEOUT</code>).</p></td>
</tr>
<tr class="odd">
<td><p><code>diff_header</code></p></td>
<td></td>
<td><p>A list of strings representing the patch set diff header.</p></td>
</tr>
<tr class="even">
<td><p><code>content</code></p></td>
<td></td>
<td><p>The content differences in the file as a list of <a href="#diff-content">DiffContent</a> entities.</p></td>
</tr>
<tr class="odd">
<td><p><code>web_links</code></p></td>
<td><p>optional</p></td>
<td><p>Links to the file diff in external sites as a list of <a href="rest-api-changes.html#diff-web-link-info">DiffWebLinkInfo</a> entries.</p></td>
</tr>
<tr class="even">
<td><p><code>binary</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the file is binary.</p></td>
</tr>
</tbody>
</table>

### DiffIntralineInfo

The `DiffIntralineInfo` entity contains information about intraline
edits in a file.

The information consists of a list of `<skip length, mark length>`
pairs, where the skip length is the number of characters between the end
of the previous edit and the start of this edit, and the mark length is
the number of edited characters following the skip. The start of the
edits is from the beginning of the related diff content lines.

Note that the implied newline character at the end of each line is
included in the length calculation, and thus it is possible for the
edits to span newlines.

### DiffWebLinkInfo

The `DiffWebLinkInfo` entity describes a link on a diff screen to an
external site.

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
<td><p>The link name.</p></td>
</tr>
<tr class="even">
<td><p><code>url</code></p></td>
<td><p>The link URL.</p></td>
</tr>
<tr class="odd">
<td><p><code>image_url</code></p></td>
<td><p>URL to the icon of the link.</p></td>
</tr>
<tr class="even">
<td><p>show_on_side_by_side_diff_view</p></td>
<td><p>Whether the web link should be shown on the side-by-side diff screen.</p></td>
</tr>
<tr class="odd">
<td><p>show_on_unified_diff_view</p></td>
<td><p>Whether the web link should be shown on the unified diff screen.</p></td>
</tr>
</tbody>
</table>

### EditFileInfo

The `EditFileInfo` entity contains additional information of a file
within a change edit.

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
<td><p><code>web_links</code></p></td>
<td><p>optional</p></td>
<td><p>Links to the diff info in external sites as a list of <a href="#web-link-info">WebLinkInfo</a> entities.</p></td>
</tr>
</tbody>
</table>

### EditInfo

The `EditInfo` entity contains information about a change edit.

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
<td><p><code>commit</code></p></td>
<td></td>
<td><p>The commit of change edit as <a href="#commit-info">CommitInfo</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>base_revision</code></p></td>
<td></td>
<td><p>The revision of the patch set the change edit is based on.</p></td>
</tr>
<tr class="odd">
<td><p><code>fetch</code></p></td>
<td><p>optional</p></td>
<td><p>Information about how to fetch this patch set. The fetch information is provided as a map that maps the protocol name (&quot;<code>git</code>&quot;, &quot;<code>http</code>&quot;, &quot;<code>ssh</code>&quot;) to <a href="#fetch-info">FetchInfo</a> entities.</p></td>
</tr>
<tr class="even">
<td><p><code>files</code></p></td>
<td><p>optional</p></td>
<td><p>The files of the change edit as a map that maps the file names to <a href="#file-info">FileInfo</a> entities.</p></td>
</tr>
</tbody>
</table>

### FetchInfo

The `FetchInfo` entity contains information about how to fetch a patch
set via a certain protocol.

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
<td><p>The URL of the project.</p></td>
</tr>
<tr class="even">
<td><p><code>ref</code></p></td>
<td></td>
<td><p>The ref of the patch set.</p></td>
</tr>
<tr class="odd">
<td><p><code>commands</code></p></td>
<td><p>optional</p></td>
<td><p>The download commands for this patch set as a map that maps the command names to the commands.<br />
Only set if <a href="#download-commands">download commands</a> are requested.</p></td>
</tr>
</tbody>
</table>

### FileInfo

The `FileInfo` entity contains information about a file in a patch set.

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
<td><p>optional</p></td>
<td><p>The status of the file (&quot;<code>A</code>&quot;=Added, &quot;<code>D</code>&quot;=Deleted, &quot;<code>R</code>&quot;=Renamed, &quot;<code>C</code>&quot;=Copied, &quot;<code>W</code>&quot;=Rewritten).<br />
Not set if the file was Modified (&quot;<code>M</code>&quot;).</p></td>
</tr>
<tr class="even">
<td><p><code>binary</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the file is binary.</p></td>
</tr>
<tr class="odd">
<td><p><code>old_path</code></p></td>
<td><p>optional</p></td>
<td><p>The old file path.<br />
Only set if the file was renamed or copied.</p></td>
</tr>
<tr class="even">
<td><p><code>lines_inserted</code></p></td>
<td><p>optional</p></td>
<td><p>Number of inserted lines.<br />
Not set for binary files or if no lines were inserted.</p></td>
</tr>
<tr class="odd">
<td><p><code>lines_deleted</code></p></td>
<td><p>optional</p></td>
<td><p>Number of deleted lines.<br />
Not set for binary files or if no lines were deleted.</p></td>
</tr>
<tr class="even">
<td><p><code>size_delta</code></p></td>
<td></td>
<td><p>Number of bytes by which the file size increased/decreased.</p></td>
</tr>
<tr class="odd">
<td><p><code>size</code></p></td>
<td></td>
<td><p>File size in bytes.</p></td>
</tr>
</tbody>
</table>

### FixInput

The `FixInput` entity contains options for fixing commits using the [fix
change](#fix-change) endpoint.

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
<td><p><code>delete_patch_set_if_commit_missing</code></p></td>
<td><p>If true, delete patch sets from the database if they refer to missing commit options.</p></td>
</tr>
<tr class="even">
<td><p><code>expect_merged_as</code></p></td>
<td><p>If set, check that the change is merged into the destination branch as this exact SHA-1. If not, insert a new patch set referring to this commit.</p></td>
</tr>
</tbody>
</table>

### FixSuggestionInfo

The `FixSuggestionInfo` entity represents a suggested fix.

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
<td><p><code>fix_id</code></p></td>
<td><p>generated, don’t set</p></td>
<td><p>The <a href="#fix-id">UUID</a> of the suggested fix. It will be generated automatically and hence will be ignored if it’s set for input objects.</p></td>
</tr>
<tr class="even">
<td><p><code>description</code></p></td>
<td></td>
<td><p>A description of the suggested fix.</p></td>
</tr>
<tr class="odd">
<td><p><code>replacements</code></p></td>
<td></td>
<td><p>A list of <a href="#fix-replacement-info">FixReplacementInfo</a> entities indicating how the content of one or several files should be modified. Within a file, they should refer to non-overlapping regions.</p></td>
</tr>
</tbody>
</table>

### FixReplacementInfo

The `FixReplacementInfo` entity describes how the content of a file
should be replaced by another content.

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
<td><p><code>path</code></p></td>
<td><p>The path of the file which should be modified. Any file in the repository may be modified.</p></td>
</tr>
<tr class="even">
<td><p><code>range</code></p></td>
<td><p>A <a href="#comment-range">CommentRange</a> indicating which content of the file should be replaced. Lines in the file are assumed to be separated by the line feed character, the carriage return character, the carriage return followed by the line feed character, or one of the other Unicode linebreak sequences supported by Java.</p></td>
</tr>
<tr class="odd">
<td><p><code>replacement</code></p></td>
<td><p>The content which should be used instead of the current one.</p></td>
</tr>
</tbody>
</table>

### GitPersonInfo

The `GitPersonInfo` entity contains information about the
author/committer of a commit.

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
<td><p>The name of the author/committer.</p></td>
</tr>
<tr class="even">
<td><p><code>email</code></p></td>
<td><p>The email address of the author/committer.</p></td>
</tr>
<tr class="odd">
<td><p><code>date</code></p></td>
<td><p>The <a href="rest-api.html#timestamp">timestamp</a> of when this identity was constructed.</p></td>
</tr>
<tr class="even">
<td><p><code>tz</code></p></td>
<td><p>The timezone offset from UTC of when this identity was constructed.</p></td>
</tr>
</tbody>
</table>

### GroupBaseInfo

The `GroupBaseInfo` entity contains base information about the group.

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
<td><p>The UUID of the group.</p></td>
</tr>
<tr class="even">
<td><p><code>name</code></p></td>
<td><p>The name of the group.</p></td>
</tr>
</tbody>
</table>

### HashtagsInput

The `HashtagsInput` entity contains information about hashtags to add
to, and/or remove from, a change.

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
<td><p><code>add</code></p></td>
<td><p>optional</p></td>
<td><p>The list of hashtags to be added to the change.</p></td>
</tr>
<tr class="even">
<td><p>`remove</p></td>
<td><p>optional</p></td>
<td><p>The list of hashtags to be removed from the change.</p></td>
</tr>
</tbody>
</table>

### IncludedInInfo

The `IncludedInInfo` entity contains information about the branches a
change was merged into and tags it was tagged with.

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
<td><p><code>branches</code></p></td>
<td></td>
<td><p>The list of branches this change was merged into. Each branch is listed without the <em>refs/head/</em> prefix.</p></td>
</tr>
<tr class="even">
<td><p><code>tags</code></p></td>
<td></td>
<td><p>The list of tags this change was tagged with. Each tag is listed without the <em>refs/tags/</em> prefix.</p></td>
</tr>
<tr class="odd">
<td><p><code>external</code></p></td>
<td><p>optional</p></td>
<td><p>A map that maps a name to a list of external systems that include this change, e.g. a list of servers on which this change is deployed.</p></td>
</tr>
</tbody>
</table>

### LabelInfo

The `LabelInfo` entity contains information about a label on a change,
always corresponding to the current patch set.

There are two options that control the contents of `LabelInfo`:
[`LABELS`](#labels) and [`DETAILED_LABELS`](#detailed-labels).

  - For a quick summary of the state of labels, use `LABELS`.

  - For detailed information about labels, including exact numeric votes
    for all users and the allowed range of votes for the current user,
    use `DETAILED_LABELS`.

#### Common fields

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
<td><p><code>optional</code></p></td>
<td><p>not set if <code>false</code></p></td>
<td><p>Whether the label is optional. Optional means the label may be set, but it’s neither necessary for submission nor does it block submission if set.</p></td>
</tr>
</tbody>
</table>

#### Fields set by `LABELS`

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
<td><p><code>approved</code></p></td>
<td><p>optional</p></td>
<td><p>One user who approved this label on the change (voted the maximum value) as an <a href="rest-api-accounts.html#account-info">AccountInfo</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>rejected</code></p></td>
<td><p>optional</p></td>
<td><p>One user who rejected this label on the change (voted the minimum value) as an <a href="rest-api-accounts.html#account-info">AccountInfo</a> entity.</p></td>
</tr>
<tr class="odd">
<td><p><code>recommended</code></p></td>
<td><p>optional</p></td>
<td><p>One user who recommended this label on the change (voted positively, but not the maximum value) as an <a href="rest-api-accounts.html#account-info">AccountInfo</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>disliked</code></p></td>
<td><p>optional</p></td>
<td><p>One user who disliked this label on the change (voted negatively, but not the minimum value) as an <a href="rest-api-accounts.html#account-info">AccountInfo</a> entity.</p></td>
</tr>
<tr class="odd">
<td><p><code>blocking</code></p></td>
<td><p>optional</p></td>
<td><p>If <code>true</code>, the label blocks submit operation. If not set, the default is false.</p></td>
</tr>
<tr class="even">
<td><p><code>value</code></p></td>
<td><p>optional</p></td>
<td><p>The voting value of the user who recommended/disliked this label on the change if it is not &quot;<code>+1</code>&quot;/&quot;<code>-1</code>&quot;.</p></td>
</tr>
<tr class="odd">
<td><p><code>default_value</code></p></td>
<td><p>optional</p></td>
<td><p>The default voting value for the label. This value may be outside the range specified in permitted_labels.</p></td>
</tr>
</tbody>
</table>

#### Fields set by `DETAILED_LABELS`

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
<td><p><code>all</code></p></td>
<td><p>optional</p></td>
<td><p>List of all approvals for this label as a list of <a href="#approval-info">ApprovalInfo</a> entities. Items in this list may not represent actual votes cast by users; if a user votes on any label, a corresponding ApprovalInfo will appear in this list for all labels.</p></td>
</tr>
<tr class="even">
<td><p><code>values</code></p></td>
<td><p>optional</p></td>
<td><p>A map of all values that are allowed for this label. The map maps the values (&quot;<code>-2</code>&quot;, &quot;<code>-1</code>&quot;, &quot; <code>0</code>&quot;, &quot;<code>+1</code>&quot;, &quot;<code>+2</code>&quot;) to the value descriptions.</p></td>
</tr>
</tbody>
</table>

### MergeableInfo

The `MergeableInfo` entity contains information about the mergeability
of a change.

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
<td><p><code>submit_type</code></p></td>
<td></td>
<td><p>Submit type used for this change, can be <code>MERGE_IF_NECESSARY</code>, <code>FAST_FORWARD_ONLY</code>, <code>REBASE_IF_NECESSARY</code>, <code>REBASE_ALWAYS</code>, <code>MERGE_ALWAYS</code> or <code>CHERRY_PICK</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>strategy</code></p></td>
<td><p>optional</p></td>
<td><p>The strategy of the merge, can be <code>recursive</code>, <code>resolve</code>, <code>simple-two-way-in-core</code>, <code>ours</code> or <code>theirs</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>mergeable</code></p></td>
<td></td>
<td><p><code>true</code> if this change is cleanly mergeable, <code>false</code> otherwise</p></td>
</tr>
<tr class="even">
<td><p><code>commit_merged</code></p></td>
<td><p>optional</p></td>
<td><p><code>true</code> if this change is already merged, <code>false</code> otherwise</p></td>
</tr>
<tr class="odd">
<td><p><code>content_merged</code></p></td>
<td><p>optional</p></td>
<td><p><code>true</code> if the content of this change is already merged, <code>false</code> otherwise</p></td>
</tr>
<tr class="even">
<td><p><code>conflicts</code></p></td>
<td><p>optional</p></td>
<td><p>A list of paths with conflicts</p></td>
</tr>
<tr class="odd">
<td><p><code>mergeable_into</code></p></td>
<td><p>optional</p></td>
<td><p>A list of other branch names where this change could merge cleanly</p></td>
</tr>
</tbody>
</table>

### MergeInput

The `MergeInput` entity contains information about the merge

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
<td><p><code>source</code></p></td>
<td></td>
<td><p>The source to merge from, e.g. a complete or abbreviated commit SHA-1, a complete reference name, a short reference name under refs/heads, refs/tags, or refs/remotes namespace, etc.</p></td>
</tr>
<tr class="even">
<td><p><code>strategy</code></p></td>
<td><p>optional</p></td>
<td><p>The strategy of the merge, can be <code>recursive</code>, <code>resolve</code>, <code>simple-two-way-in-core</code>, <code>ours</code> or <code>theirs</code>, default will use project settings.</p></td>
</tr>
</tbody>
</table>

### MergePatchSetInput

The `MergePatchSetInput` entity contains information about updating a
new change by creating a new merge commit.

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
<td><p><code>subject</code></p></td>
<td><p>optional</p></td>
<td><p>The new subject for the change, if not specified, will reuse the current patch set’s subject</p></td>
</tr>
<tr class="even">
<td><p><code>inheritParent</code></p></td>
<td><p>optional, default to <code>false</code></p></td>
<td><p>Use the current patch set’s first parent as the merge tip when set to <code>true</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>base_change</code></p></td>
<td><p>optional</p></td>
<td><p>A <a href="#change-id">{change-id}</a> that identifies a change. When <code>inheritParent</code> is <code>false</code>, the merge tip will be the current patch set of the <code>base_change</code> if it’s set. Otherwise, the current branch tip of the destination branch will be used.</p></td>
</tr>
<tr class="even">
<td><p><code>merge</code></p></td>
<td></td>
<td><p>The detail of the source commit for merge as a <a href="#merge-input">MergeInput</a> entity.</p></td>
</tr>
</tbody>
</table>

### MoveInput

The `MoveInput` entity contains information for moving a change to a new
branch.

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
<td><p><code>destination_branch</code></p></td>
<td></td>
<td><p>Destination branch</p></td>
</tr>
<tr class="even">
<td><p><code>message</code></p></td>
<td><p>optional</p></td>
<td><p>A message to be posted in this change’s comments</p></td>
</tr>
</tbody>
</table>

### NotifyInfo

The `NotifyInfo` entity contains detailed information about who should
be notified about an update. These notifications are sent out even if a
`notify` option in the request input disables normal notifications.
`NotifyInfo` entities are normally contained in a `notify_details` map
in the request input where the key is the recipient type. The recipient
type can be `TO`, `CC` and `BCC`.

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
<td><p>optional</p></td>
<td><p>A list of <a href="rest-api-accounts.html#account-id">account IDs</a> that identify the accounts that should be should be notified.</p></td>
</tr>
</tbody>
</table>

### PrivateInput

The `PrivateInput` entity contains information for changing the private
flag on a change.

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
<td><p><code>message</code></p></td>
<td><p>optional</p></td>
<td><p>Message describing why the private flag was changed.</p></td>
</tr>
</tbody>
</table>

### ProblemInfo

The `ProblemInfo` entity contains a description of a potential
consistency problem with a change. These are not related to the code
review process, but rather indicate some inconsistency in Gerrit’s
database or repository metadata related to the enclosing change.

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
<td><p><code>message</code></p></td>
<td></td>
<td><p>Plaintext message describing the problem with the change.</p></td>
</tr>
<tr class="even">
<td><p><code>status</code></p></td>
<td><p>optional</p></td>
<td><p>The status of fixing the problem (<code>FIXED</code>, <code>FIX_FAILED</code>). Only set if a fix was attempted.</p></td>
</tr>
<tr class="odd">
<td><p><code>outcome</code></p></td>
<td><p>optional</p></td>
<td><p>If <code>status</code> is set, an additional plaintext message describing the outcome of the fix.</p></td>
</tr>
</tbody>
</table>

### PublishChangeEditInput

The `PublishChangeEditInput` entity contains options for the publishing
of change edit.

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
<td><p><code>notify</code></p></td>
<td><p>optional</p></td>
<td><p>Notify handling that defines to whom email notifications should be sent after the change edit is published.<br />
Allowed values are <code>NONE</code> and <code>ALL</code>.<br />
If not set, the default is <code>ALL</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>notify_details</code></p></td>
<td><p>optional</p></td>
<td><p>Additional information about whom to notify about the update as a map of recipient type to <a href="#notify-info">NotifyInfo</a> entity.</p></td>
</tr>
</tbody>
</table>

### PureRevertInfo

The `PureRevertInfo` entity describes the result of a pure revert check.

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
<td><p><code>is_pure_revert</code></p></td>
<td><p>Outcome of the check as boolean.</p></td>
</tr>
</tbody>
</table>

### PushCertificateInfo

The `PushCertificateInfo` entity contains information about a push
certificate provided when the user pushed for review with `git push
--signed HEAD:refs/for/<branch>`. Only used when signed push is
[enabled](config-gerrit.html#receive.enableSignedPush) on the server.

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
<td><p><code>certificate</code></p></td>
<td><p>Signed certificate payload and GPG signature block.</p></td>
</tr>
<tr class="even">
<td><p><code>key</code></p></td>
<td><p>Information about the key that signed the push, along with any problems found while checking the signature or the key itself, as a <a href="rest-api-accounts.html#gpg-key-info">GpgKeyInfo</a> entity.</p></td>
</tr>
</tbody>
</table>

### RangeInfo

The `RangeInfo` entity stores the coordinates of a range.

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
<td><p><code>start</code></p></td>
<td><p>First index.</p></td>
</tr>
<tr class="even">
<td><p><code>end</code></p></td>
<td><p>Last index.</p></td>
</tr>
</tbody>
</table>

### RebaseInput

The `RebaseInput` entity contains information for changing parent when
rebasing.

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
<td><p><code>base</code></p></td>
<td><p>optional</p></td>
<td><p>The new parent revision. This can be a ref or a SHA1 to a concrete patchset.<br />
Alternatively, a change number can be specified, in which case the current patch set is inferred.<br />
Empty string is used for rebasing directly on top of the target branch, which effectively breaks dependency towards a parent change.</p></td>
</tr>
</tbody>
</table>

### RelatedChangeAndCommitInfo

The `RelatedChangeAndCommitInfo` entity contains information about a
related change and commit.

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
<td><p><code>project</code></p></td>
<td></td>
<td><p>The project of the change or commit.</p></td>
</tr>
<tr class="even">
<td><p><code>change_id</code></p></td>
<td><p>optional</p></td>
<td><p>The Change-Id of the change.</p></td>
</tr>
<tr class="odd">
<td><p><code>commit</code></p></td>
<td></td>
<td><p>The commit as a <a href="#commit-info">CommitInfo</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>_change_number</code></p></td>
<td><p>optional</p></td>
<td><p>The change number.</p></td>
</tr>
<tr class="odd">
<td><p><code>_revision_number</code></p></td>
<td><p>optional</p></td>
<td><p>The revision number.</p></td>
</tr>
<tr class="even">
<td><p><code>_current_revision_number</code></p></td>
<td><p>optional</p></td>
<td><p>The current revision number.</p></td>
</tr>
<tr class="odd">
<td><p><code>status</code></p></td>
<td><p>optional</p></td>
<td><p>The status of the change. The status of the change is one of (<code>NEW</code>, <code>MERGED</code>, <code>ABANDONED</code>).</p></td>
</tr>
</tbody>
</table>

### RelatedChangesInfo

The `RelatedChangesInfo` entity contains information about related
changes.

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
<td><p><code>changes</code></p></td>
<td><p>A list of <a href="#related-change-and-commit-info">RelatedChangeAndCommitInfo</a> entities describing the related changes. Sorted by git commit order, newest to oldest. Empty if there are no related changes.</p></td>
</tr>
</tbody>
</table>

### RestoreInput

The `RestoreInput` entity contains information for restoring a change.

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
<td><p><code>message</code></p></td>
<td><p>optional</p></td>
<td><p>Message to be added as review comment to the change when restoring the change.</p></td>
</tr>
</tbody>
</table>

### RevertInput

The `RevertInput` entity contains information for reverting a change.

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
<td><p><code>message</code></p></td>
<td><p>optional</p></td>
<td><p>Message to be added as review comment to the change when reverting the change.</p></td>
</tr>
</tbody>
</table>

### ReviewInfo

The `ReviewInfo` entity contains information about a review.

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
<td><p><code>labels</code></p></td>
<td><p>The labels of the review as a map that maps the label names to the voting values.</p></td>
</tr>
</tbody>
</table>

### ReviewerUpdateInfo

The `ReviewerUpdateInfo` entity contains information about updates to
change’s reviewers set.

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
<td><p><code>updated</code></p></td>
<td><p>Timestamp of the update.</p></td>
</tr>
<tr class="even">
<td><p><code>updated_by</code></p></td>
<td><p>The account which modified state of the reviewer in question as <a href="rest-api-accounts.html#account-info">AccountInfo</a> entity.</p></td>
</tr>
<tr class="odd">
<td><p><code>reviewer</code></p></td>
<td><p>The reviewer account added or removed from the change as an <a href="rest-api-accounts.html#account-info">AccountInfo</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>state</code></p></td>
<td><p>The reviewer state, one of <code>REVIEWER</code>, <code>CC</code> or <code>REMOVED</code>.</p></td>
</tr>
</tbody>
</table>

### ReviewInput

The `ReviewInput` entity contains information for adding a review to a
revision.

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
<td><p><code>message</code></p></td>
<td><p>optional</p></td>
<td><p>The message to be added as review comment.</p></td>
</tr>
<tr class="even">
<td><p><code>tag</code></p></td>
<td><p>optional</p></td>
<td><p>Apply this tag to the review comment message, votes, and inline comments. Tags may be used by CI or other automated systems to distinguish them from human reviews. Comments with specific tag values can be filtered out in the web UI.</p></td>
</tr>
<tr class="odd">
<td><p><code>labels</code></p></td>
<td><p>optional</p></td>
<td><p>The votes that should be added to the revision as a map that maps the label names to the voting values.</p></td>
</tr>
<tr class="even">
<td><p><code>comments</code></p></td>
<td><p>optional</p></td>
<td><p>The comments that should be added as a map that maps a file path to a list of <a href="#comment-input">CommentInput</a> entities.</p></td>
</tr>
<tr class="odd">
<td><p><code>robot_comments</code></p></td>
<td><p>optional</p></td>
<td><p>The robot comments that should be added as a map that maps a file path to a list of <a href="#robot-comment-input">RobotCommentInput</a> entities.</p></td>
</tr>
<tr class="even">
<td><p><code>drafts</code></p></td>
<td><p>optional</p></td>
<td><p>Draft handling that defines how draft comments are handled that are already in the database but that were not also described in this input.<br />
Allowed values are <code>DELETE</code>, <code>PUBLISH</code>, <code>PUBLISH_ALL_REVISIONS</code> and <code>KEEP</code>. All values except <code>PUBLISH_ALL_REVISIONS</code> operate only on drafts for a single revision.<br />
Only <code>KEEP</code> is allowed when used in conjunction with <code>on_behalf_of</code>.<br />
If not set, the default is <code>DELETE</code>, unless <code>on_behalf_of</code> is set, in which case the default is <code>KEEP</code> and any other value is disallowed.</p></td>
</tr>
<tr class="odd">
<td><p><code>notify</code></p></td>
<td><p>optional</p></td>
<td><p>Notify handling that defines to whom email notifications should be sent after the review is stored.<br />
Allowed values are <code>NONE</code>, <code>OWNER</code>, <code>OWNER_REVIEWERS</code> and <code>ALL</code>.<br />
If not set, the default is <code>ALL</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>notify_details</code></p></td>
<td><p>optional</p></td>
<td><p>Additional information about whom to notify about the update as a map of recipient type to <a href="#notify-info">NotifyInfo</a> entity.</p></td>
</tr>
<tr class="odd">
<td><p><code>omit_duplicate_comments</code></p></td>
<td><p>optional</p></td>
<td><p>If <code>true</code>, comments with the same content at the same place will be omitted.</p></td>
</tr>
<tr class="even">
<td><p><code>on_behalf_of</code></p></td>
<td><p>optional</p></td>
<td><p><a href="rest-api-accounts.html#account-id">{account-id}</a> the review should be posted on behalf of. To use this option the caller must have been granted <code>labelAs-NAME</code> permission for all keys of labels.</p></td>
</tr>
<tr class="odd">
<td><p><code>reviewers</code></p></td>
<td><p>optional</p></td>
<td><p>A list of <a href="rest-api-changes.html#reviewer-input">ReviewerInput</a> representing reviewers that should be added to the change.</p></td>
</tr>
<tr class="even">
<td><p><code>ready</code></p></td>
<td><p>optional</p></td>
<td><p>If true, and if the change is work in progress, then start review. It is an error for both <code>ready</code> and <code>work_in_progress</code> to be true.</p></td>
</tr>
<tr class="odd">
<td><p><code>work_in_progress</code></p></td>
<td><p>optional</p></td>
<td><p>If true, mark the change as work in progress. It is an error for both <code>ready</code> and <code>work_in_progress</code> to be true.</p></td>
</tr>
</tbody>
</table>

### ReviewResult

The `ReviewResult` entity contains information regarding the updates
that were made to a review.

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
<td><p><code>labels</code></p></td>
<td><p>optional</p></td>
<td><p>Map of labels to values after the review was posted. Null if any reviewer additions were rejected.</p></td>
</tr>
<tr class="even">
<td><p><code>reviewers</code></p></td>
<td><p>optional</p></td>
<td><p>Map of account or group identifier to <a href="rest-api-changes.html#add-reviewer-result">AddReviewerResult</a> representing the outcome of adding as a reviewer. Absent if no reviewer additions were requested.</p></td>
</tr>
<tr class="odd">
<td><p><code>ready</code></p></td>
<td><p>optional</p></td>
<td><p>If true, the change was moved from WIP to ready for review as a result of this action. Not set if false.</p></td>
</tr>
</tbody>
</table>

### ReviewerInfo

The `ReviewerInfo` entity contains information about a reviewer and its
votes on a change.

`ReviewerInfo` has the same fields as
[AccountInfo](rest-api-accounts.html#account-info) and includes
[detailed account information](#detailed-accounts). In addition
`ReviewerInfo` has the following fields:

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
<td><p><code>approvals</code></p></td>
<td><p>The approvals of the reviewer as a map that maps the label names to the approval values (&quot;<code>-2</code>&quot;, &quot;<code>-1</code>&quot;, &quot;<code>0</code>&quot;, &quot;<code>+1</code>&quot;, &quot;<code>+2</code>&quot;).</p></td>
</tr>
<tr class="even">
<td><p><code>_account_id</code></p></td>
<td><p>This field is inherited from <code>AccountInfo</code> but is optional here if an unregistered reviewer was added by email. See <a href="rest-api-changes.html#add-reviewer">add-reviewer</a> for details.</p></td>
</tr>
</tbody>
</table>

### ReviewerInput

The `ReviewerInput` entity contains information for adding a reviewer to
a change.

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
<td><p><code>reviewer</code></p></td>
<td></td>
<td><p>The <a href="rest-api-accounts.html#account-id">ID</a> of one account that should be added as reviewer or the <a href="rest-api-groups.html#group-id">ID</a> of one group for which all members should be added as reviewers.<br />
If an ID identifies both an account and a group, only the account is added as reviewer to the change.</p></td>
</tr>
<tr class="even">
<td><p><code>state</code></p></td>
<td><p>optional</p></td>
<td><p>Add reviewer in this state. Possible reviewer states are <code>REVIEWER</code> and <code>CC</code>. If not given, defaults to <code>REVIEWER</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>confirmed</code></p></td>
<td><p>optional</p></td>
<td><p>Whether adding the reviewer is confirmed.<br />
The Gerrit server may be configured to <a href="config-gerrit.html#addreviewer.maxWithoutConfirmation">require a confirmation</a> when adding a group as reviewer that has many members.</p></td>
</tr>
<tr class="even">
<td><p><code>notify</code></p></td>
<td><p>optional</p></td>
<td><p>Notify handling that defines to whom email notifications should be sent after the reviewer is added.<br />
Allowed values are <code>NONE</code>, <code>OWNER</code>, <code>OWNER_REVIEWERS</code> and <code>ALL</code>.<br />
If not set, the default is <code>ALL</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>notify_details</code></p></td>
<td><p>optional</p></td>
<td><p>Additional information about whom to notify about the update as a map of recipient type to <a href="#notify-info">NotifyInfo</a> entity.</p></td>
</tr>
</tbody>
</table>

### RevisionInfo

The `RevisionInfo` entity contains information about a patch set. Not
all fields are returned by default. Additional fields can be obtained by
adding `o` parameters as described in [Query Changes](#list-changes).

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
<td><p><code>kind</code></p></td>
<td></td>
<td><p>The change kind. Valid values are <code>REWORK</code>, <code>TRIVIAL_REBASE</code>, <code>MERGE_FIRST_PARENT_UPDATE</code>, <code>NO_CODE_CHANGE</code>, and <code>NO_CHANGE</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>_number</code></p></td>
<td></td>
<td><p>The patch set number.</p></td>
</tr>
<tr class="odd">
<td><p><code>created</code></p></td>
<td></td>
<td><p>The <a href="rest-api.html#timestamp">timestamp</a> of when the patch set was created.</p></td>
</tr>
<tr class="even">
<td><p><code>uploader</code></p></td>
<td></td>
<td><p>The uploader of the patch set as an <a href="rest-api-accounts.html#account-info">AccountInfo</a> entity.</p></td>
</tr>
<tr class="odd">
<td><p><code>ref</code></p></td>
<td></td>
<td><p>The Git reference for the patch set.</p></td>
</tr>
<tr class="even">
<td><p><code>fetch</code></p></td>
<td></td>
<td><p>Information about how to fetch this patch set. The fetch information is provided as a map that maps the protocol name (&quot;<code>git</code>&quot;, &quot;<code>http</code>&quot;, &quot;<code>ssh</code>&quot;) to <a href="#fetch-info">FetchInfo</a> entities. This information is only included if a plugin implementing the <a href="intro-project-owner.html#download-commands">download commands</a> interface is installed.</p></td>
</tr>
<tr class="odd">
<td><p><code>commit</code></p></td>
<td><p>optional</p></td>
<td><p>The commit of the patch set as <a href="#commit-info">CommitInfo</a> entity.</p></td>
</tr>
<tr class="even">
<td><p><code>files</code></p></td>
<td><p>optional</p></td>
<td><p>The files of the patch set as a map that maps the file names to <a href="#file-info">FileInfo</a> entities. Only set if <a href="#current-files">CURRENT_FILES</a> or <a href="#all-files">ALL_FILES</a> option is requested.</p></td>
</tr>
<tr class="odd">
<td><p><code>actions</code></p></td>
<td><p>optional</p></td>
<td><p>Actions the caller might be able to perform on this revision. The information is a map of view name to <a href="#action-info">ActionInfo</a> entities.</p></td>
</tr>
<tr class="even">
<td><p><code>reviewed</code></p></td>
<td><p>optional</p></td>
<td><p>Indicates whether the caller is authenticated and has commented on the current revision. Only set if <a href="#reviewed">REVIEWED</a> option is requested.</p></td>
</tr>
<tr class="odd">
<td><p><code>messageWithFooter</code></p></td>
<td><p>optional</p></td>
<td><p>If the <a href="#commit-footers">COMMIT_FOOTERS</a> option is requested and this is the current patch set, contains the full commit message with Gerrit-specific commit footers, as if this revision were submitted using the <a href="project-configuration.html#cherry_pick">Cherry Pick</a> submit type.</p></td>
</tr>
<tr class="even">
<td><p><code>push_certificate</code></p></td>
<td><p>optional</p></td>
<td><p>If the <a href="#push-certificates">PUSH_CERTIFICATES</a> option is requested, contains the push certificate provided by the user when uploading this patch set as a <a href="#push-certificate-info">PushCertificateInfo</a> entity. This field is always set if the option is requested; if no push certificate was provided, it is set to an empty object.</p></td>
</tr>
<tr class="odd">
<td><p><code>description</code></p></td>
<td><p>optional</p></td>
<td><p>The description of this patchset, as displayed in the patchset selector menu. May be null if no description is set.</p></td>
</tr>
</tbody>
</table>

### RobotCommentInfo

The `RobotCommentInfo` entity contains information about a robot inline
comment.

`RobotCommentInfo` has the same fields as [CommentInfo](#comment-info).
In addition `RobotCommentInfo` has the following fields:

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
<td><p><code>robot_id</code></p></td>
<td></td>
<td><p>The ID of the robot that generated this comment.</p></td>
</tr>
<tr class="even">
<td><p><code>robot_run_id</code></p></td>
<td></td>
<td><p>An ID of the run of the robot.</p></td>
</tr>
<tr class="odd">
<td><p><code>url</code></p></td>
<td><p>optional</p></td>
<td><p>URL to more information.</p></td>
</tr>
<tr class="even">
<td><p><code>properties</code></p></td>
<td><p>optional</p></td>
<td><p>Robot specific properties as map that maps arbitrary keys to values.</p></td>
</tr>
<tr class="odd">
<td><p><code>fix_suggestions</code></p></td>
<td><p>optional</p></td>
<td><p>Suggested fixes for this robot comment as a list of <a href="#fix-suggestion-info">FixSuggestionInfo</a> entities.</p></td>
</tr>
</tbody>
</table>

### RobotCommentInput

The `RobotCommentInput` entity contains information for creating an
inline robot comment.

`RobotCommentInput` has the same fields as
[RobotCommentInfo](#robot-comment-info).

### RuleInput

The `RuleInput` entity contains information to test a Prolog rule.

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
<td><p><code>rule</code></p></td>
<td></td>
<td><p>Prolog code to execute instead of the code in <code>refs/meta/config</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>filters</code></p></td>
<td><p><code>RUN</code> if not set</p></td>
<td><p>When <code>RUN</code> filter rules in the parent projects are called to post-process the results of the project specific rule. This behavior matches how the rule will execute if installed.<br />
If <code>SKIP</code> the parent filters are not called, allowing the test to return results from the input rule.</p></td>
</tr>
</tbody>
</table>

### SubmitInfo

The `SubmitInfo` entity contains information about the change status
after submitting.

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
<td><p>The status of the change after submitting is <code>MERGED</code>.</p></td>
</tr>
<tr class="even">
<td><p><code>on_behalf_of</code></p></td>
<td><p>optional</p></td>
<td><p>The <a href="rest-api-accounts.html#account-id">{account-id}</a> of the user on whose behalf the action should be done. To use this option the caller must have been granted both <code>Submit</code> and <code>Submit (On Behalf Of)</code> permissions. The user named by <code>on_behalf_of</code> does not need to be granted the <code>Submit</code> permission. This feature is aimed for CI solutions: the CI account can be granted both permissions, so individual users don’t need <code>Submit</code> permission themselves. Still the changes can be submitted on behalf of real users and not with the identity of the CI account.</p></td>
</tr>
</tbody>
</table>

### SubmitInput

The `SubmitInput` entity contains information for submitting a change.

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
<td><p><code>on_behalf_of</code></p></td>
<td><p>optional</p></td>
<td><p>If set, submit the change on behalf of the given user. The value may take any format <a href="rest-api-accounts.html#account-id">accepted by the accounts REST API</a>. Using this option requires <a href="access-control.html#category_submit_on_behalf_of">Submit (On Behalf Of)</a> permission on the branch.</p></td>
</tr>
<tr class="even">
<td><p><code>notify</code></p></td>
<td><p>optional</p></td>
<td><p>Notify handling that defines to whom email notifications should be sent after the change is submitted.<br />
Allowed values are <code>NONE</code>, <code>OWNER</code>, <code>OWNER_REVIEWERS</code> and <code>ALL</code>.<br />
If not set, the default is <code>ALL</code>.</p></td>
</tr>
<tr class="odd">
<td><p><code>notify_details</code></p></td>
<td><p>optional</p></td>
<td><p>Additional information about whom to notify about the update as a map of recipient type to <a href="#notify-info">NotifyInfo</a> entity.</p></td>
</tr>
</tbody>
</table>

### SubmitRecord

The `SubmitRecord` entity describes results from a submit\_rule. Fields
in this entity roughly correspond to the fields set by `LABELS` in
[LabelInfo](#label-info).

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
<td><p><code>OK</code>, the change can be submitted.<br />
<code>NOT_READY</code>, additional labels are required before submit.<br />
<code>CLOSED</code>, closed changes cannot be submitted.<br />
<code>RULE_ERROR</code>, rule code failed with an error.</p></td>
</tr>
<tr class="even">
<td><p><code>ok</code></p></td>
<td><p>optional</p></td>
<td><p>Map of labels that are approved; an <a href="rest-api-accounts.html#account-info">AccountInfo</a> identifies the voter chosen by the rule.</p></td>
</tr>
<tr class="odd">
<td><p><code>reject</code></p></td>
<td><p>optional</p></td>
<td><p>Map of labels that are preventing submit; <a href="rest-api-accounts.html#account-info">AccountInfo</a> identifies voter.</p></td>
</tr>
<tr class="even">
<td><p><code>need</code></p></td>
<td><p>optional</p></td>
<td><p>Map of labels that need to be given to submit. The value is currently an empty object.</p></td>
</tr>
<tr class="odd">
<td><p><code>may</code></p></td>
<td><p>optional</p></td>
<td><p>Map of labels that can be used, but do not affect submit. <a href="rest-api-accounts.html#account-info">AccountInfo</a> identifies voter, if the label has been applied.</p></td>
</tr>
<tr class="even">
<td><p><code>impossible</code></p></td>
<td><p>optional</p></td>
<td><p>Map of labels that should have been in <code>need</code> but cannot be used by any user because of access restrictions. The value is currently an empty object.</p></td>
</tr>
<tr class="odd">
<td><p><code>error_message</code></p></td>
<td><p>optional</p></td>
<td><p>When status is RULE_ERROR this message provides some text describing the failure of the rule predicate.</p></td>
</tr>
</tbody>
</table>

### SubmittedTogetherInfo

The `SubmittedTogetherInfo` entity contains information about a
collection of changes that would be submitted together.

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
<td><p><code>changes</code></p></td>
<td><p>A list of ChangeInfo entities representing the changes to be submitted together.</p></td>
</tr>
<tr class="even">
<td><p><code>non_visible_changes</code></p></td>
<td><p>The number of changes to be submitted together that the current user cannot see. (This count includes changes that are visible to the current user when their reason for being submitted together involves changes the user cannot see.)</p></td>
</tr>
</tbody>
</table>

### SuggestedReviewerInfo

The `SuggestedReviewerInfo` entity contains information about a reviewer
that can be added to a change (an account or a group).

`SuggestedReviewerInfo` has either the `account` field that contains the
[AccountInfo](rest-api-accounts.html#account-info) entity, or the
`group` field that contains the
[GroupBaseInfo](rest-api-changes.html#group-base-info) entity.

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
<td><p>optional</p></td>
<td><p>An <a href="rest-api-accounts.html#account-info">AccountInfo</a> entity, if the suggestion is an account.</p></td>
</tr>
<tr class="even">
<td><p><code>group</code></p></td>
<td><p>optional</p></td>
<td><p>A <a href="rest-api-changes.html#group-base-info">GroupBaseInfo</a> entity, if the suggestion is a group.</p></td>
</tr>
<tr class="odd">
<td><p><code>count</code></p></td>
<td></td>
<td><p>The total number of accounts in the suggestion. This is <code>1</code> if <code>account</code> is present. If <code>group</code> is present, the total number of accounts that are members of the group is returned (this count includes members of nested groups).</p></td>
</tr>
<tr class="even">
<td><p><code>confirm</code></p></td>
<td><p>optional</p></td>
<td><p>True if <code>group</code> is present and <code>count</code> is above the threshold where the <code>confirmed</code> flag must be passed to add the group as a reviewer.</p></td>
</tr>
</tbody>
</table>

### TopicInput

The `TopicInput` entity contains information for setting a topic.

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
<td><p><code>topic</code></p></td>
<td><p>optional</p></td>
<td><p>The topic.<br />
The topic will be deleted if not set.</p></td>
</tr>
</tbody>
</table>

### TrackingIdInfo

The `TrackingIdInfo` entity describes a reference to an external
tracking system.

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
<td><p><code>system</code></p></td>
<td><p>The name of the external tracking system.</p></td>
</tr>
<tr class="even">
<td><p><code>id</code></p></td>
<td><p>The tracking id.</p></td>
</tr>
</tbody>
</table>

### VotingRangeInfo

The `VotingRangeInfo` entity describes the continuous voting range from
min to max values.

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
<td><p><code>min</code></p></td>
<td><p>The minimum voting value.</p></td>
</tr>
<tr class="even">
<td><p><code>max</code></p></td>
<td><p>The maximum voting value.</p></td>
</tr>
</tbody>
</table>

### WebLinkInfo

The `WebLinkInfo` entity describes a link to an external site.

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
<td><p>The link name.</p></td>
</tr>
<tr class="even">
<td><p><code>url</code></p></td>
<td><p>The link URL.</p></td>
</tr>
<tr class="odd">
<td><p><code>image_url</code></p></td>
<td><p>URL to the icon of the link.</p></td>
</tr>
</tbody>
</table>

### WorkInProgressInput

The `WorkInProgressInput` entity contains additional information for a
change set to WorkInProgress/ReadyForReview.

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
<td><p><code>message</code></p></td>
<td><p>optional</p></td>
<td><p>Message to be added as a review comment to the change being set WorkInProgress/ReadyForReview.</p></td>
</tr>
</tbody>
</table>

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX

