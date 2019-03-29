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

/** Definition of change index versions (schemata). See {@link SchemaDefinitions}. */
public class ChangeSchemaDefinitions extends SchemaDefinitions<ChangeData> {
  @Deprecated
  /** Added new field {@link ChangeField#IS_SUBMITTABLE} based on submit requirements. */
  static final Schema<ChangeData> V74 =
      schema(
          ChangeField.ADDED,
          ChangeField.APPROVAL,
          ChangeField.ASSIGNEE,
          ChangeField.ATTENTION_SET_FULL,
          ChangeField.ATTENTION_SET_USERS,
          ChangeField.ATTENTION_SET_USERS_COUNT,
          ChangeField.AUTHOR,
          ChangeField.CHANGE,
          ChangeField.CHERRY_PICK,
          ChangeField.CHERRY_PICK_OF_CHANGE,
          ChangeField.CHERRY_PICK_OF_PATCHSET,
          ChangeField.COMMENT,
          ChangeField.COMMENTBY,
          ChangeField.COMMIT,
          ChangeField.COMMIT_MESSAGE,
          ChangeField.COMMITTER,
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
          ChangeField.FUZZY_HASHTAG,
          ChangeField.FUZZY_TOPIC,
          ChangeField.GROUP,
          ChangeField.HASHTAG,
          ChangeField.HASHTAG_CASE_AWARE,
          ChangeField.ID,
          ChangeField.IS_PURE_REVERT,
          ChangeField.IS_SUBMITTABLE,
          ChangeField.LABEL,
          ChangeField.LEGACY_ID_STR,
          ChangeField.MERGE,
          ChangeField.MERGEABLE,
          ChangeField.MERGED_ON,
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
          ChangeField.STORED_SUBMIT_REQUIREMENTS,
          ChangeField.SUBMISSIONID,
          ChangeField.SUBMIT_RECORD,
          ChangeField.SUBMIT_RULE_RESULT,
          ChangeField.TOTAL_COMMENT_COUNT,
          ChangeField.TR,
          ChangeField.UNRESOLVED_COMMENT_COUNT,
          ChangeField.UPDATED,
          ChangeField.UPLOADER,
          ChangeField.WIP);

  /**
   * Added new field {@link ChangeField#PREFIX_HASHTAG} and {@link ChangeField#PREFIX_TOPIC} to
   * allow easier search for topics.
   */
  @Deprecated
  static final Schema<ChangeData> V75 =
      new Schema.Builder<ChangeData>()
          .add(V74)
          .add(ChangeField.PREFIX_HASHTAG)
          .add(ChangeField.PREFIX_TOPIC)
          .build();

  /** Added new field {@link ChangeField#FOOTER_NAME}. */
  @Deprecated
  static final Schema<ChangeData> V76 =
      new Schema.Builder<ChangeData>().add(V75).add(ChangeField.FOOTER_NAME).build();

  /** Added new field {@link ChangeField#COMMIT_MESSAGE_EXACT}. */
  @Deprecated
  static final Schema<ChangeData> V77 =
      new Schema.Builder<ChangeData>().add(V76).add(ChangeField.COMMIT_MESSAGE_EXACT).build();

  // Upgrade Lucene to 7.x requires reindexing.
  @Deprecated static final Schema<ChangeData> V78 = schema(V77);

  // Upgrade Lucene to 8.x requires reindexing.
  static final Schema<ChangeData> V79 = schema(V78);

  /**
   * Name of the change index to be used when contacting index backends or loading configurations.
   */
  public static final String NAME = "changes";

  /** Singleton instance of the schema definitions. This is one per JVM. */
  public static final ChangeSchemaDefinitions INSTANCE = new ChangeSchemaDefinitions();

  private ChangeSchemaDefinitions() {
    super(NAME, ChangeData.class);
  }
}
