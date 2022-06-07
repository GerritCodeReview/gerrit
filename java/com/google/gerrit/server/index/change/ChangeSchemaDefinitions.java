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
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.SchemaDefinitions;
import com.google.gerrit.server.query.change.ChangeData;

/** Definition of change index versions (schemata). See {@link SchemaDefinitions}. */
public class ChangeSchemaDefinitions extends SchemaDefinitions<ChangeData> {
  @Deprecated
  /** Added new field {@link ChangeField#IS_SUBMITTABLE} based on submit requirements. */
  static final Schema<ChangeData> V74 =
      schema(
          ImmutableList.of(
              ChangeField.LEGACY_ID_STR_FIELD,
              ChangeField.CHANGE_ID_FIELD,
              ChangeField.STATUS_FIELD,
              ChangeField.PROJECT_FIELD,
              ChangeField.REF_FIELD,
              ChangeField.TOPIC_FIELD,
              ChangeField.SUBMISSIONID_FIELD,
              ChangeField.UPDATED_FIELD,
              ChangeField.MERGED_ON_FIELD,
              ChangeField.PATH_FIELD,
              ChangeField.HASHTAG_FIELD,
              ChangeField.HASHTAG_CASE_AWARE_FIELD,
              ChangeField.OWNER_FIELD,
              ChangeField.UPLOADER_FIELD,
              ChangeField.ADDED_FIELD,
              ChangeField.AUTHOR_NAME_EMAIL_FILED,
              ChangeField.COMMITER_FILED,
              ChangeField.COMMITER_NAME_EMAIL_FILED,
              ChangeField.CHANGE_FIELD,
              ChangeField.APPROVAL_FIELD,
              ChangeField.COMMIT_MESSAGE_FIELD,
              ChangeField.ADDED_FIELD,
              ChangeField.PRIVATE_FIELD,
              ChangeField.WIP_FIELD,
              ChangeField.STARTED_FIELD,
              ChangeField.COMMENTBY_FIELD,
              ChangeField.PATCH_SET_FIELD,
              ChangeField.STORED_SUBMIT_REQUIREMENTS_FILED,
              ChangeField.REF_STATE_PATTERN_FIELD,
              ChangeField.FOOTER_FIELD),
          ImmutableList.of(
              ChangeField.LEGACY_ID_STR_SPEC,
              ChangeField.CHANGE_ID_SPEC,
              ChangeField.STATUS_SPEC,
              ChangeField.PROJECT_EXACT_SPEC,
              ChangeField.PROJECT_PREFIX_SPEC,
              ChangeField.REF_SPEC,
              ChangeField.EXACT_TOPIC_SPEC,
              ChangeField.FUZZY_TOPIC_SPEC,
              ChangeField.SUBMISSIONID_SPEC,
              ChangeField.UPDATED_SPEC,
              ChangeField.MERGED_ON_SPEC,
              ChangeField.PATH_FIELD_SPEC,
              ChangeField.EXACT_HASHTAG_SPEC,
              ChangeField.FUZZY_HASHTAG_SPEC,
              ChangeField.HASHTAG_CASE_AWARE_SPEC,
              ChangeField.OWNER_SPEC,
              ChangeField.UPLOADER_SPEC,
              ChangeField.AUTHOR_FULL_TEXT_SPEC,
              ChangeField.AUTHOR_NAME_EMAIL_SPEC,
              ChangeField.COMMITER_FULL_TEXT_SPEC,
              ChangeField.COMMITER_NAME_EMAIL_SPEC,
              ChangeField.CHANGE_SPEC,
              ChangeField.APPROVAL_SPEC,
              ChangeField.COMMIT_MESSAGE_FULL_TEXT_SPEC,
              ChangeField.ADDED_SPEC,
              ChangeField.PRIVATE_SPEC,
              ChangeField.WIP_SPEC,
              ChangeField.STARTED_SPEC,
              ChangeField.COMMENTBY_SPEC,
              ChangeField.PATCH_SET_SPEC,
              ChangeField.STORED_SUBMIT_REQUIREMENTS_SPEC,
              ChangeField.REF_STATE_PATTERN_SPEC,
              ChangeField.FOOTER_SPEC));

  /**
   * Added new field {@link ChangeField#PREFIX_HASHTAG_SPEC} and {@link
   * ChangeField#PREFIX_TOPIC_SPEC} to allow easier search for topics.
   */
  @Deprecated
  static final Schema<ChangeData> V75 =
      new Schema.Builder<ChangeData>()
          .add(V74)
          .addFieldSpecs(ChangeField.PREFIX_HASHTAG_SPEC)
          .addFieldSpecs(ChangeField.PREFIX_TOPIC_SPEC)
          .build();

  /** Added new field {@link ChangeField#FOOTER_NAME_SPEC}. */
  @Deprecated
  static final Schema<ChangeData> V76 = new Schema.Builder<ChangeData>().add(V75).addIndexFields(ChangeField.FOOTER_NAME_FIELD).addFieldSpecs(ChangeField.FOOTER_NAME_SPEC).build();

  /** Added new field {@link ChangeField#COMMIT_MESSAGE_EXACT_SPEC}. */
  @Deprecated
  static final Schema<ChangeData> V77 =
      new Schema.Builder<ChangeData>()
          .add(V76)
          .addFieldSpecs(ChangeField.COMMIT_MESSAGE_EXACT_SPEC)
          .build();

  // Upgrade Lucene to 7.x requires reindexing.
  static final Schema<ChangeData> V78 = schema(V77);

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
