// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

'use strict';

var Changes = Changes || {};

Changes.DiffType = {
  ADDED: 'ADDED',
  COPIED: 'COPIED',
  DELETED: 'DELETED',
  MODIFIED: 'MODIFIED',
  RENAMED: 'RENAMED',
  REWRITE: 'REWRITE',
};

// Must be kept in sync with the ListChangesOption enum and protobuf.
Changes.ListChangesOption = {
  LABELS: 0,
  DETAILED_LABELS: 8,

  // Return information on the current patch set of the change.
  CURRENT_REVISION: 1,
  ALL_REVISIONS: 2,

  // If revisions are included, parse the commit object.
  CURRENT_COMMIT: 3,
  ALL_COMMITS: 4,

  // If a patch set is included, include the files of the patch set.
  CURRENT_FILES: 5,
  ALL_FILES: 6,

  // If accounts are included, include detailed account info.
  DETAILED_ACCOUNTS: 7,

  // Include messages associated with the change.
  MESSAGES: 9,

  // Include allowed actions client could perform.
  CURRENT_ACTIONS: 10,

  // Set the reviewed boolean for the caller.
  REVIEWED: 11,

  // Include download commands for the caller.
  DOWNLOAD_COMMANDS: 13,

  // Include patch set weblinks.
  WEB_LINKS: 14,

  // Include consistency check results.
  CHECK: 15,

  // Include allowed change actions client could perform.
  CHANGE_ACTIONS: 16,

  // Include a copy of commit messages including review footers.
  COMMIT_FOOTERS: 17,

  // Include push certificate information along with any patch sets.
  PUSH_CERTIFICATES: 18
};

Changes.listChangesOptionsToHex = function() {
  var v = 0;
  for (var i = 0; i < arguments.length; i++) {
    v |= 1 << arguments[i];
  }
  return v.toString(16);
};
