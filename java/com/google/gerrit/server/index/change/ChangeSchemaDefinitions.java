// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.index.change;

import static com.google.gerrit.index.SchemaUtil.schema;

import com.google.gerrit.index.Schema;
import com.google.gerrit.index.SchemaDefinitions;
import com.google.gerrit.server.query.change.ChangeData;

public class ChangeSchemaDefinitions extends SchemaDefinitions<ChangeData> {
  // New numeric types: use dimensional points using the k-d tree geo-spatial data structure
  // to offer fast single- and multi-dimensional numeric range. As the consequense, integer
  // document id type is replaced with string document id type.
  @Deprecated
  static final Schema<ChangeData> V57 =
      schema(
          ChangeField.ADDED,
          ChangeField.APPROVAL,
          ChangeField.ASSIGNEE,
          ChangeField.AUTHOR,
          ChangeField.CHANGE,
          ChangeField.COMMENT,
          ChangeField.COMMENTBY,
          ChangeField.COMMIT,
          ChangeField.COMMITTER,
          ChangeField.COMMIT_MESSAGE,
          ChangeField.DELETED,
          ChangeField.DELTA,
          ChangeField.DIRECTORY,
          ChangeField.DRAFTBY,
          ChangeField.EDITBY,
          ChangeField.EXACT_AUTHOR,
          ChangeField.EXACT_COMMIT,
          ChangeField.EXACT_COMMITTER,
          ChangeField.EXACT_TOPIC,
          ChangeField.EXTENSION,
          ChangeField.FILE_PART,
          ChangeField.FOOTER,
          ChangeField.FUZZY_TOPIC,
          ChangeField.GROUP,
          ChangeField.HASHTAG,
          ChangeField.HASHTAG_CASE_AWARE,
          ChangeField.ID,
          ChangeField.LABEL,
          ChangeField.LEGACY_ID2,
          ChangeField.MERGEABLE,
          ChangeField.ONLY_EXTENSIONS,
          ChangeField.OWNER,
          ChangeField.PATCH_SET,
          ChangeField.PATH,
          ChangeField.PENDING_REVIEWER,
          ChangeField.PENDING_REVIEWER_BY_EMAIL,
          ChangeField.PRIVATE,
          ChangeField.PROJECT,
          ChangeField.PROJECTS,
          ChangeField.REF,
          ChangeField.REF_STATE,
          ChangeField.REF_STATE_PATTERN,
          ChangeField.REVERT_OF,
          ChangeField.REVIEWEDBY,
          ChangeField.REVIEWER,
          ChangeField.REVIEWER_BY_EMAIL,
          ChangeField.STAR,
          ChangeField.STARBY,
          ChangeField.STARTED,
          ChangeField.STATUS,
          ChangeField.STORED_SUBMIT_RECORD_LENIENT,
          ChangeField.STORED_SUBMIT_RECORD_STRICT,
          ChangeField.SUBMISSIONID,
          ChangeField.SUBMIT_RECORD,
          ChangeField.TOTAL_COMMENT_COUNT,
          ChangeField.TR,
          ChangeField.UNRESOLVED_COMMENT_COUNT,
          ChangeField.UPDATED,
          ChangeField.WIP);

  // Upgrade Lucene to 7.x requires reindexing.
  @Deprecated static final Schema<ChangeData> V58 = schema(V57);

  // Upgrade Lucene to 8.x requires reindexing.
  static final Schema<ChangeData> V59 = schema(V58);

  public static final String NAME = "changes";
  public static final ChangeSchemaDefinitions INSTANCE = new ChangeSchemaDefinitions();

  private ChangeSchemaDefinitions() {
    super(NAME, ChangeData.class);
  }
}
