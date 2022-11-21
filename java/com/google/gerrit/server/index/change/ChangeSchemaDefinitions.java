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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.index.IndexedField;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.SchemaDefinitions;
import com.google.gerrit.server.query.change.ChangeData;

/**
 * Definition of change index versions (schemata). See {@link SchemaDefinitions}.
 *
 * <p>Upgrades are subject to constraints, see {@code
 * com.google.gerrit.index.IndexUpgradeValidator}.
 */
public class ChangeSchemaDefinitions extends SchemaDefinitions<ChangeData> {
  /** Added new field {@link ChangeField#IS_SUBMITTABLE_SPEC} based on submit requirements. */
  @Deprecated
  static final Schema<ChangeData> V74 =
      schema(
          /* version= */ 74,
          ImmutableList.of(
              ChangeField.ADDED,
              ChangeField.CHANGE,
              ChangeField.CHERRY_PICK,
              ChangeField.COMMENTBY,
              ChangeField.DELETED,
              ChangeField.DELTA,
              ChangeField.DRAFTBY,
              ChangeField.EDITBY,
              ChangeField.GROUP,
              ChangeField.ID,
              ChangeField.LEGACY_ID_STR,
              ChangeField.MERGE,
              ChangeField.MERGEABLE,
              ChangeField.PATCH_SET,
              ChangeField.PRIVATE,
              ChangeField.REF_STATE,
              ChangeField.REF_STATE_PATTERN,
              ChangeField.REVIEWEDBY,
              ChangeField.STAR,
              ChangeField.STARBY,
              ChangeField.STARTED,
              ChangeField.STORED_SUBMIT_RECORD_LENIENT,
              ChangeField.STORED_SUBMIT_RECORD_STRICT,
              ChangeField.STORED_SUBMIT_REQUIREMENTS,
              ChangeField.SUBMIT_RECORD,
              ChangeField.SUBMIT_RULE_RESULT,
              ChangeField.UPDATED,
              ChangeField.WIP),
          ImmutableList.<IndexedField<ChangeData, ?>>of(
              ChangeField.APPROVAL_FIELD,
              ChangeField.ASSIGNEE_FIELD,
              ChangeField.ATTENTION_SET_FULL_FIELD,
              ChangeField.ATTENTION_SET_USERS_COUNT_FIELD,
              ChangeField.ATTENTION_SET_USERS_FIELD,
              ChangeField.AUTHOR_PARTS_FIELD,
              ChangeField.CHERRY_PICK_OF_CHANGE_FIELD,
              ChangeField.CHERRY_PICK_OF_PATCHSET_FIELD,
              ChangeField.COMMENT_FIELD,
              ChangeField.COMMITTER_PARTS_FIELD,
              ChangeField.COMMIT_FIELD,
              ChangeField.COMMIT_MESSAGE_FIELD,
              ChangeField.DIRECTORY_FIELD,
              ChangeField.EXACT_AUTHOR_FIELD,
              ChangeField.EXACT_COMMITTER_FIELD,
              ChangeField.EXTENSION_FIELD,
              ChangeField.FILE_PART_FIELD,
              ChangeField.FOOTER_FIELD,
              ChangeField.HASHTAG_CASE_AWARE_FIELD,
              ChangeField.HASHTAG_FIELD,
              ChangeField.IS_PURE_REVERT_FIELD,
              ChangeField.IS_SUBMITTABLE_FIELD,
              ChangeField.LABEL_FIELD,
              ChangeField.MERGED_ON_FIELD,
              ChangeField.ONLY_EXTENSIONS_FIELD,
              ChangeField.OWNER_FIELD,
              ChangeField.PATH_FIELD,
              ChangeField.PENDING_REVIEWER_BY_EMAIL_FIELD,
              ChangeField.PENDING_REVIEWER_FIELD,
              ChangeField.PROJECT_FIELD,
              ChangeField.REF_FIELD,
              ChangeField.REVERT_OF_FIELD,
              ChangeField.REVIEWER_BY_EMAIL_FIELD,
              ChangeField.REVIEWER_FIELD,
              ChangeField.STATUS_FIELD,
              ChangeField.SUBMISSIONID_FIELD,
              ChangeField.TOPIC_FIELD,
              ChangeField.TOTAL_COMMENT_COUNT_FIELD,
              ChangeField.TR_FIELD,
              ChangeField.UNRESOLVED_COMMENT_COUNT_FIELD,
              ChangeField.UPLOADER_FIELD),
          ImmutableList.<IndexedField<ChangeData, ?>.SearchSpec>of(
              ChangeField.APPROVAL_SPEC,
              ChangeField.ASSIGNEE_SPEC,
              ChangeField.ATTENTION_SET_FULL_SPEC,
              ChangeField.ATTENTION_SET_USERS,
              ChangeField.ATTENTION_SET_USERS_COUNT,
              ChangeField.AUTHOR_PARTS_SPEC,
              ChangeField.CHERRY_PICK_OF_CHANGE,
              ChangeField.CHERRY_PICK_OF_PATCHSET,
              ChangeField.COMMENT_SPEC,
              ChangeField.COMMITTER_PARTS_SPEC,
              ChangeField.COMMIT_MESSAGE,
              ChangeField.COMMIT_SPEC,
              ChangeField.DIRECTORY_SPEC,
              ChangeField.EXACT_AUTHOR_SPEC,
              ChangeField.EXACT_COMMITTER_SPEC,
              ChangeField.EXACT_COMMIT_SPEC,
              ChangeField.EXACT_TOPIC,
              ChangeField.EXTENSION_SPEC,
              ChangeField.FILE_PART_SPEC,
              ChangeField.FOOTER_SPEC,
              ChangeField.FUZZY_HASHTAG,
              ChangeField.FUZZY_TOPIC,
              ChangeField.HASHTAG_CASE_AWARE_SPEC,
              ChangeField.HASHTAG_SPEC,
              ChangeField.IS_PURE_REVERT_SPEC,
              ChangeField.IS_SUBMITTABLE_SPEC,
              ChangeField.LABEL_SPEC,
              ChangeField.MERGED_ON_SPEC,
              ChangeField.ONLY_EXTENSIONS_SPEC,
              ChangeField.OWNER_SPEC,
              ChangeField.PATH_SPEC,
              ChangeField.PENDING_REVIEWER_BY_EMAIL,
              ChangeField.PENDING_REVIEWER_SPEC,
              ChangeField.PROJECTS_SPEC,
              ChangeField.PROJECT_SPEC,
              ChangeField.REF_SPEC,
              ChangeField.REVERT_OF,
              ChangeField.REVIEWER_BY_EMAIL,
              ChangeField.REVIEWER_SPEC,
              ChangeField.STATUS_SPEC,
              ChangeField.SUBMISSIONID_SPEC,
              ChangeField.TOTAL_COMMENT_COUNT_SPEC,
              ChangeField.TR_SPEC,
              ChangeField.UNRESOLVED_COMMENT_COUNT_SPEC,
              ChangeField.UPLOADER_SPEC));

  /**
   * Added new field {@link ChangeField#PREFIX_HASHTAG} and {@link ChangeField#PREFIX_TOPIC} to
   * allow easier search for topics.
   */
  @Deprecated
  static final Schema<ChangeData> V75 =
      new Schema.Builder<ChangeData>()
          .add(V74)
          .addSearchSpecs(ChangeField.PREFIX_HASHTAG)
          .addSearchSpecs(ChangeField.PREFIX_TOPIC)
          .build();

  /** Added new field {@link ChangeField#FOOTER_NAME}. */
  @Deprecated
  static final Schema<ChangeData> V76 =
      new Schema.Builder<ChangeData>()
          .add(V75)
          .addIndexedFields(ChangeField.FOOTER_NAME_FIELD)
          .addSearchSpecs(ChangeField.FOOTER_NAME)
          .build();

  /** Added new field {@link ChangeField#COMMIT_MESSAGE_EXACT}. */
  @Deprecated
  static final Schema<ChangeData> V77 =
      new Schema.Builder<ChangeData>()
          .add(V76)
          .addIndexedFields(ChangeField.COMMIT_MESSAGE_EXACT_FIELD)
          .addSearchSpecs(ChangeField.COMMIT_MESSAGE_EXACT)
          .build();

  // Upgrade Lucene to 7.x requires reindexing.
  @Deprecated static final Schema<ChangeData> V78 = schema(V77);

  /** Remove draft and star fields. */
  static final Schema<ChangeData> V79 =
      new Schema.Builder<ChangeData>()
          .add(V78)
          .remove(ChangeField.DRAFTBY, ChangeField.STAR, ChangeField.STARBY)
          .build();
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
