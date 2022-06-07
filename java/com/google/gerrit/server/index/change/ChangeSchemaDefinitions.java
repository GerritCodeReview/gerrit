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
  /** Added new field {@link ChangeIndexField#IS_SUBMITTABLE} based on submit requirements. */
  static final Schema<ChangeData> V74 =
      schema(
          ImmutableList.of(
              ChangeIndexField.LEGACY_ID_STR_FIELD,
              ChangeIndexField.CHANGE_ID_FIELD,
              ChangeIndexField.STATUS_FIELD,
              ChangeIndexField.PROJECT_FIELD,
              ChangeIndexField.REF_FIELD,
              ChangeIndexField.TOPIC_FIELD,
              ChangeIndexField.SUBMISSIONID_FIELD,
              ChangeIndexField.UPDATED_FIELD,
              ChangeIndexField.MERGED_ON_FIELD,
              ChangeIndexField.PATH_FIELD,
              ChangeIndexField.HASHTAG_FIELD,
              ChangeIndexField.HASHTAG_CASE_AWARE_FIELD,
              ChangeIndexField.OWNER_FIELD,
              ChangeIndexField.UPLOADER_FIELD,
              ChangeIndexField.ADDED_FIELD,
              ChangeIndexField.AUTHOR_NAME_EMAIL_FILED,
              ChangeIndexField.COMMITER_FILED,
              ChangeIndexField.COMMITER_NAME_EMAIL_FILED,
              ChangeIndexField.CHANGE_FIELD,
              ChangeIndexField.APPROVAL_FIELD,
              ChangeIndexField.COMMIT_MESSAGE_FIELD,
              ChangeIndexField.ADDED_FIELD,
              ChangeIndexField.PRIVATE_FIELD,
              ChangeIndexField.WIP_FIELD,
              ChangeIndexField.STARTED_FIELD,
              ChangeIndexField.COMMENTBY_FIELD,
              ChangeIndexField.PATCH_SET_FIELD,
              ChangeIndexField.STORED_SUBMIT_REQUIREMENTS_FILED,
              ChangeIndexField.REF_STATE_PATTERN_FIELD,
              ChangeIndexField.FOOTER_FIELD),
          ImmutableList.of(
              ChangeIndexField.LEGACY_ID_STR_SPEC,
              ChangeIndexField.CHANGE_ID_SPEC,
              ChangeIndexField.STATUS_SPEC,
              ChangeIndexField.PROJECT_EXACT_SPEC,
              ChangeIndexField.PROJECT_PREFIX_SPEC,
              ChangeIndexField.REF_SPEC,
              ChangeIndexField.EXACT_TOPIC_SPEC,
              ChangeIndexField.FUZZY_TOPIC_SPEC,
              ChangeIndexField.SUBMISSIONID_SPEC,
              ChangeIndexField.UPDATED_SPEC,
              ChangeIndexField.MERGED_ON_SPEC,
              ChangeIndexField.PATH_FIELD_SPEC,
              ChangeIndexField.EXACT_HASHTAG_SPEC,
              ChangeIndexField.FUZZY_HASHTAG_SPEC,
              ChangeIndexField.HASHTAG_CASE_AWARE_SPEC,
              ChangeIndexField.OWNER_SPEC,
              ChangeIndexField.UPLOADER_SPEC,
              ChangeIndexField.AUTHOR_FULL_TEXT_SPEC,
              ChangeIndexField.AUTHOR_NAME_EMAIL_SPEC,
              ChangeIndexField.COMMITER_FULL_TEXT_SPEC,
              ChangeIndexField.COMMITER_NAME_EMAIL_SPEC,
              ChangeIndexField.CHANGE_SPEC,
              ChangeIndexField.APPROVAL_SPEC,
              ChangeIndexField.COMMIT_MESSAGE_FULL_TEXT_SPEC,
              ChangeIndexField.ADDED_SPEC,
              ChangeIndexField.PRIVATE_SPEC,
              ChangeIndexField.WIP_SPEC,
              ChangeIndexField.STARTED_SPEC,
              ChangeIndexField.COMMENTBY_SPEC,
              ChangeIndexField.PATCH_SET_SPEC,
              ChangeIndexField.STORED_SUBMIT_REQUIREMENTS_SPEC,
              ChangeIndexField.REF_STATE_PATTERN_SPEC,
              ChangeIndexField.FOOTER_SPEC));

  /**
   * Added new field {@link ChangeIndexField#PREFIX_HASHTAG_SPEC} and {@link
   * ChangeIndexField#PREFIX_TOPIC_SPEC} to allow easier search for topics.
   */
  @Deprecated
  static final Schema<ChangeData> V75 =
      new Schema.Builder<ChangeData>()
          .add(V74)
          .addFieldSpecs(ChangeIndexField.PREFIX_HASHTAG_SPEC)
          .addFieldSpecs(ChangeIndexField.PREFIX_TOPIC_SPEC)
          .build();

  /** Added new field {@link ChangeIndexField#FOOTER_NAME_SPEC}. */
  @Deprecated
  static final Schema<ChangeData> V76 = new Schema.Builder<ChangeData>().add(V75).addIndexFields(ChangeIndexField.FOOTER_NAME_FIELD).addFieldSpecs(ChangeIndexField.FOOTER_NAME_SPEC).build();

  /** Added new field {@link ChangeIndexField#COMMIT_MESSAGE_EXACT_SPEC}. */
  @Deprecated
  static final Schema<ChangeData> V77 =
      new Schema.Builder<ChangeData>()
          .add(V76)
          .addFieldSpecs(ChangeIndexField.COMMIT_MESSAGE_EXACT_SPEC)
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
