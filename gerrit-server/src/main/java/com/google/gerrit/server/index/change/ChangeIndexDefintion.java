// Copyright (C) 2013 The Android Open Source Project
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

import static com.google.gerrit.server.index.SchemaUtil.schema;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.index.IndexDefinition;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;

/** Secondary index schemas for changes. */
public class ChangeIndexDefintion
    extends IndexDefinition<Change.Id, ChangeData, ChangeIndex> {
  public static final String NAME = "changes";

  @Deprecated
  static final Schema<ChangeData> V25 = schema(
      ChangeField.LEGACY_ID,
      ChangeField.ID,
      ChangeField.STATUS,
      ChangeField.PROJECT,
      ChangeField.PROJECTS,
      ChangeField.REF,
      ChangeField.EXACT_TOPIC,
      ChangeField.FUZZY_TOPIC,
      ChangeField.UPDATED,
      ChangeField.FILE_PART,
      ChangeField.PATH,
      ChangeField.OWNER,
      ChangeField.REVIEWER,
      ChangeField.COMMIT,
      ChangeField.TR,
      ChangeField.LABEL,
      ChangeField.COMMIT_MESSAGE,
      ChangeField.COMMENT,
      ChangeField.CHANGE,
      ChangeField.APPROVAL,
      ChangeField.MERGEABLE,
      ChangeField.ADDED,
      ChangeField.DELETED,
      ChangeField.DELTA,
      ChangeField.HASHTAG,
      ChangeField.COMMENTBY,
      ChangeField.PATCH_SET,
      ChangeField.GROUP,
      ChangeField.SUBMISSIONID,
      ChangeField.EDITBY,
      ChangeField.REVIEWEDBY,
      ChangeField.EXACT_COMMIT,
      ChangeField.AUTHOR,
      ChangeField.COMMITTER);

  @Deprecated
  static final Schema<ChangeData> V26 = schema(
      ChangeField.LEGACY_ID,
      ChangeField.ID,
      ChangeField.STATUS,
      ChangeField.PROJECT,
      ChangeField.PROJECTS,
      ChangeField.REF,
      ChangeField.EXACT_TOPIC,
      ChangeField.FUZZY_TOPIC,
      ChangeField.UPDATED,
      ChangeField.FILE_PART,
      ChangeField.PATH,
      ChangeField.OWNER,
      ChangeField.REVIEWER,
      ChangeField.COMMIT,
      ChangeField.TR,
      ChangeField.LABEL,
      ChangeField.COMMIT_MESSAGE,
      ChangeField.COMMENT,
      ChangeField.CHANGE,
      ChangeField.APPROVAL,
      ChangeField.MERGEABLE,
      ChangeField.ADDED,
      ChangeField.DELETED,
      ChangeField.DELTA,
      ChangeField.HASHTAG,
      ChangeField.COMMENTBY,
      ChangeField.PATCH_SET,
      ChangeField.GROUP,
      ChangeField.SUBMISSIONID,
      ChangeField.EDITBY,
      ChangeField.REVIEWEDBY,
      ChangeField.EXACT_COMMIT,
      ChangeField.AUTHOR,
      ChangeField.COMMITTER,
      ChangeField.DRAFTBY);

  static final Schema<ChangeData> V27 = schema(V26.getFields().values());

  @Inject
  ChangeIndexDefintion(
      ChangeIndexCollection indexCollection,
      ChangeIndex.Factory indexFactory,
      AllChangesIndexer allChangesIndexer) {
    super(NAME, indexCollection, indexFactory, allChangesIndexer, ChangeData.class);
  }
}
