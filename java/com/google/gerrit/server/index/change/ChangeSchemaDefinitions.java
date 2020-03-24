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
  static final Schema<ChangeData> V55 =
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
          ChangeField.LEGACY_ID,
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

  /**
   * The computation of the {@link ChangeField#EXTENSION} field is changed, hence reindexing is
   * required.
   */
  @Deprecated static final Schema<ChangeData> V56 = schema(V55);

  /**
   * New numeric types: use dimensional points using the k-d tree geo-spatial data structure to
   * offer fast single- and multi-dimensional numeric range. As the consequense, {@link
   * ChangeField#LEGACY_ID} is replaced with {@link ChangeField#LEGACY_ID_STR}.
   */
  @Deprecated
  static final Schema<ChangeData> V57 =
      new Schema.Builder<ChangeData>()
          .add(V56)
          .remove(ChangeField.LEGACY_ID)
          .add(ChangeField.LEGACY_ID_STR)
          .legacyNumericFields(false)
          .build();

  /**
   * Added new fields {@link ChangeField#CHERRY_PICK_OF_CHANGE} and {@link
   * ChangeField#CHERRY_PICK_OF_PATCHSET}.
   */
  @Deprecated
  static final Schema<ChangeData> V58 =
      new Schema.Builder<ChangeData>()
          .add(V57)
          .add(ChangeField.CHERRY_PICK_OF_CHANGE)
          .add(ChangeField.CHERRY_PICK_OF_PATCHSET)
          .build();

  /** Added new field {@link ChangeField#ATTENTION}. */
  static final Schema<ChangeData> V59 =
      new Schema.Builder<ChangeData>().add(V58).add(ChangeField.ATTENTION).build();

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
