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
          ImmutableList.<IndexedField<ChangeData, ?>>of(
              ChangeField.ADDED_LINES_FIELD,
              ChangeField.APPROVAL_FIELD,
              ChangeField.ASSIGNEE_FIELD,
              ChangeField.ATTENTION_SET_FULL_FIELD,
              ChangeField.ATTENTION_SET_USERS_COUNT_FIELD,
              ChangeField.ATTENTION_SET_USERS_FIELD,
              ChangeField.AUTHOR_PARTS_FIELD,
              ChangeField.CHANGE_FIELD,
              ChangeField.CHANGE_ID_FIELD,
              ChangeField.CHERRY_PICK_FIELD,
              ChangeField.CHERRY_PICK_OF_CHANGE_FIELD,
              ChangeField.CHERRY_PICK_OF_PATCHSET_FIELD,
              ChangeField.COMMENTBY_FIELD,
              ChangeField.COMMENT_FIELD,
              ChangeField.COMMITTER_PARTS_FIELD,
              ChangeField.COMMIT_FIELD,
              ChangeField.COMMIT_MESSAGE_FIELD,
              ChangeField.DELETED_LINES_FIELD,
              ChangeField.DELTA_LINES_FIELD,
              ChangeField.DIRECTORY_FIELD,
              ChangeField.DRAFTBY_FIELD,
              ChangeField.EDITBY_FIELD,
              ChangeField.EXACT_AUTHOR_FIELD,
              ChangeField.EXACT_COMMITTER_FIELD,
              ChangeField.EXTENSION_FIELD,
              ChangeField.FILE_PART_FIELD,
              ChangeField.FOOTER_FIELD,
              ChangeField.GROUP_FIELD,
              ChangeField.HASHTAG_CASE_AWARE_FIELD,
              ChangeField.HASHTAG_FIELD,
              ChangeField.IS_PURE_REVERT_FIELD,
              ChangeField.IS_SUBMITTABLE_FIELD,
              ChangeField.LABEL_FIELD,
              ChangeField.MERGEABLE_FIELD,
              ChangeField.MERGED_ON_FIELD,
              ChangeField.MERGE_FIELD,
              ChangeField.NUMERIC_ID_STR_FIELD,
              ChangeField.ONLY_EXTENSIONS_FIELD,
              ChangeField.OWNER_FIELD,
              ChangeField.PATCH_SET_FIELD,
              ChangeField.PATH_FIELD,
              ChangeField.PENDING_REVIEWER_BY_EMAIL_FIELD,
              ChangeField.PENDING_REVIEWER_FIELD,
              ChangeField.PRIVATE_FIELD,
              ChangeField.PROJECT_FIELD,
              ChangeField.REF_FIELD,
              ChangeField.REF_STATE_FIELD,
              ChangeField.REF_STATE_PATTERN_FIELD,
              ChangeField.REVERT_OF_FIELD,
              ChangeField.REVIEWEDBY_FIELD,
              ChangeField.REVIEWER_BY_EMAIL_FIELD,
              ChangeField.REVIEWER_FIELD,
              ChangeField.STARBY_FIELD,
              ChangeField.STARTED_FIELD,
              ChangeField.STAR_FIELD,
              ChangeField.STATUS_FIELD,
              ChangeField.STORED_SUBMIT_RECORD_LENIENT_FIELD,
              ChangeField.STORED_SUBMIT_RECORD_STRICT_FIELD,
              ChangeField.STORED_SUBMIT_REQUIREMENTS_FIELD,
              ChangeField.SUBMISSIONID_FIELD,
              ChangeField.SUBMIT_RECORD_FIELD,
              ChangeField.SUBMIT_RULE_RESULT_FIELD,
              ChangeField.TOPIC_FIELD,
              ChangeField.TOTAL_COMMENT_COUNT_FIELD,
              ChangeField.TR_FIELD,
              ChangeField.UNRESOLVED_COMMENT_COUNT_FIELD,
              ChangeField.UPDATED_FIELD,
              ChangeField.UPLOADER_FIELD,
              ChangeField.WIP_FIELD),
          ImmutableList.<IndexedField<ChangeData, ?>.SearchSpec>of(
              ChangeField.ADDED_LINES_SPEC,
              ChangeField.APPROVAL_SPEC,
              ChangeField.ASSIGNEE_SPEC,
              ChangeField.ATTENTION_SET_FULL_SPEC,
              ChangeField.ATTENTION_SET_USERS,
              ChangeField.ATTENTION_SET_USERS_COUNT,
              ChangeField.AUTHOR_PARTS_SPEC,
              ChangeField.CHANGE_ID_SPEC,
              ChangeField.CHANGE_SPEC,
              ChangeField.CHERRY_PICK_OF_CHANGE,
              ChangeField.CHERRY_PICK_OF_PATCHSET,
              ChangeField.CHERRY_PICK_SPEC,
              ChangeField.COMMENTBY_SPEC,
              ChangeField.COMMENT_SPEC,
              ChangeField.COMMITTER_PARTS_SPEC,
              ChangeField.COMMIT_MESSAGE,
              ChangeField.COMMIT_SPEC,
              ChangeField.DELETED_LINES_SPEC,
              ChangeField.DELTA_LINES_SPEC,
              ChangeField.DIRECTORY_SPEC,
              ChangeField.DRAFTBY_SPEC,
              ChangeField.EDITBY_SPEC,
              ChangeField.EXACT_AUTHOR_SPEC,
              ChangeField.EXACT_COMMITTER_SPEC,
              ChangeField.EXACT_COMMIT_SPEC,
              ChangeField.EXACT_TOPIC,
              ChangeField.EXTENSION_SPEC,
              ChangeField.FILE_PART_SPEC,
              ChangeField.FOOTER_SPEC,
              ChangeField.FUZZY_HASHTAG,
              ChangeField.FUZZY_TOPIC,
              ChangeField.GROUP_SPEC,
              ChangeField.HASHTAG_CASE_AWARE_SPEC,
              ChangeField.HASHTAG_SPEC,
              ChangeField.IS_PURE_REVERT_SPEC,
              ChangeField.IS_SUBMITTABLE_SPEC,
              ChangeField.LABEL_SPEC,
              ChangeField.MERGEABLE_SPEC,
              ChangeField.MERGED_ON_SPEC,
              ChangeField.MERGE_SPEC,
              ChangeField.NUMERIC_ID_STR_SPEC,
              ChangeField.ONLY_EXTENSIONS_SPEC,
              ChangeField.OWNER_SPEC,
              ChangeField.PATCH_SET_SPEC,
              ChangeField.PATH_SPEC,
              ChangeField.PENDING_REVIEWER_BY_EMAIL,
              ChangeField.PENDING_REVIEWER_SPEC,
              ChangeField.PRIVATE_SPEC,
              ChangeField.PROJECTS_SPEC,
              ChangeField.PROJECT_SPEC,
              ChangeField.REF_SPEC,
              ChangeField.REF_STATE_PATTERN_SPEC,
              ChangeField.REF_STATE_SPEC,
              ChangeField.REVERT_OF,
              ChangeField.REVIEWEDBY_SPEC,
              ChangeField.REVIEWER_BY_EMAIL,
              ChangeField.REVIEWER_SPEC,
              ChangeField.STARBY_SPEC,
              ChangeField.STARTED_SPEC,
              ChangeField.STAR_SPEC,
              ChangeField.STATUS_SPEC,
              ChangeField.STORED_SUBMIT_RECORD_LENIENT_SPEC,
              ChangeField.STORED_SUBMIT_RECORD_STRICT_SPEC,
              ChangeField.STORED_SUBMIT_REQUIREMENTS_SPEC,
              ChangeField.SUBMISSIONID_SPEC,
              ChangeField.SUBMIT_RECORD_SPEC,
              ChangeField.SUBMIT_RULE_RESULT_SPEC,
              ChangeField.TOTAL_COMMENT_COUNT_SPEC,
              ChangeField.TR_SPEC,
              ChangeField.UNRESOLVED_COMMENT_COUNT_SPEC,
              ChangeField.UPDATED_SPEC,
              ChangeField.UPLOADER_SPEC,
              ChangeField.WIP_SPEC));

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
  @Deprecated
  static final Schema<ChangeData> V79 =
      new Schema.Builder<ChangeData>()
          .add(V78)
          .remove(ChangeField.STAR_SPEC, ChangeField.STARBY_SPEC, ChangeField.DRAFTBY_SPEC)
          .remove(ChangeField.STAR_FIELD, ChangeField.STARBY_FIELD, ChangeField.DRAFTBY_FIELD)
          .build();

  /** Add subject field. */
  @Deprecated
  static final Schema<ChangeData> V80 =
      new Schema.Builder<ChangeData>()
          .add(V79)
          .addIndexedFields(ChangeField.SUBJECT_FIELD)
          .addSearchSpecs(ChangeField.SUBJECT_SPEC)
          .build();

  /** Add prefixsubject field. */
  @Deprecated
  static final Schema<ChangeData> V81 =
      new Schema.Builder<ChangeData>()
          .add(V80)
          .addSearchSpecs(ChangeField.PREFIX_SUBJECT_SPEC)
          .build();

  /** Remove assignee field. */
  @SuppressWarnings("deprecation")
  static final Schema<ChangeData> V82 =
      new Schema.Builder<ChangeData>()
          .add(V81)
          .remove(ChangeField.ASSIGNEE_SPEC)
          .remove(ChangeField.ASSIGNEE_FIELD)
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
