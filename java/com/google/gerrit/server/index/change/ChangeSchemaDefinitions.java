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
  @Deprecated
  static final Schema<ChangeData> V39 =
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
          ChangeField.DRAFTBY,
          ChangeField.EDITBY,
          ChangeField.EXACT_COMMIT,
          ChangeField.EXACT_TOPIC,
          ChangeField.FILE_PART,
          ChangeField.FUZZY_TOPIC,
          ChangeField.GROUP,
          ChangeField.HASHTAG,
          ChangeField.HASHTAG_CASE_AWARE,
          ChangeField.ID,
          ChangeField.LABEL,
          ChangeField.LEGACY_ID,
          ChangeField.MERGEABLE,
          ChangeField.OWNER,
          ChangeField.PATCH_SET,
          ChangeField.PATH,
          ChangeField.PROJECT,
          ChangeField.PROJECTS,
          ChangeField.REF,
          ChangeField.REF_STATE,
          ChangeField.REF_STATE_PATTERN,
          ChangeField.REVIEWEDBY,
          ChangeField.REVIEWER,
          ChangeField.STAR,
          ChangeField.STARBY,
          ChangeField.STATUS,
          ChangeField.STORED_SUBMIT_RECORD_LENIENT,
          ChangeField.STORED_SUBMIT_RECORD_STRICT,
          ChangeField.SUBMISSIONID,
          ChangeField.SUBMIT_RECORD,
          ChangeField.TR,
          ChangeField.UNRESOLVED_COMMENT_COUNT,
          ChangeField.UPDATED);

  @Deprecated static final Schema<ChangeData> V40 = schema(V39, ChangeField.PRIVATE);
  @Deprecated static final Schema<ChangeData> V41 = schema(V40, ChangeField.REVIEWER_BY_EMAIL);
  @Deprecated static final Schema<ChangeData> V42 = schema(V41, ChangeField.WIP);

  @Deprecated
  static final Schema<ChangeData> V43 =
      schema(V42, ChangeField.EXACT_AUTHOR, ChangeField.EXACT_COMMITTER);

  @Deprecated
  static final Schema<ChangeData> V44 =
      schema(
          V43,
          ChangeField.STARTED,
          ChangeField.PENDING_REVIEWER,
          ChangeField.PENDING_REVIEWER_BY_EMAIL);

  @Deprecated static final Schema<ChangeData> V45 = schema(V44, ChangeField.REVERT_OF);

  @Deprecated static final Schema<ChangeData> V46 = schema(V45);

  // Removal of draft change workflow requires reindexing
  @Deprecated static final Schema<ChangeData> V47 = schema(V46);

  // Rename of star label 'mute' to 'reviewed' requires reindexing
  @Deprecated static final Schema<ChangeData> V48 = schema(V47);

  @Deprecated static final Schema<ChangeData> V49 = schema(V48);

  // Bump Lucene version requires reindexing
  @Deprecated static final Schema<ChangeData> V50 = schema(V49);

  @Deprecated static final Schema<ChangeData> V51 = schema(V50, ChangeField.TOTAL_COMMENT_COUNT);

  @Deprecated static final Schema<ChangeData> V52 = schema(V51, ChangeField.EXTENSION);

  @Deprecated static final Schema<ChangeData> V53 = schema(V52, ChangeField.ONLY_EXTENSIONS);

  @Deprecated static final Schema<ChangeData> V54 = schema(V53, ChangeField.FOOTER);

  @Deprecated static final Schema<ChangeData> V55 = schema(V54, ChangeField.DIRECTORY);

  // The computation of the 'extension' field is changed, hence reindexing is required.
  @Deprecated static final Schema<ChangeData> V56 = schema(V55);

  // Change document ID type from LegacyIntField to StringField
  static final Schema<ChangeData> V57 = schema(V56);

  public static final String NAME = "changes";
  public static final ChangeSchemaDefinitions INSTANCE = new ChangeSchemaDefinitions();

  private ChangeSchemaDefinitions() {
    super(NAME, ChangeData.class);
  }
}
