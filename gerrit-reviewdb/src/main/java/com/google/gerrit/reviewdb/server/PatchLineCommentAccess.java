// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.reviewdb.server;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwtorm.server.Access;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.PrimaryKey;
import com.google.gwtorm.server.Query;
import com.google.gwtorm.server.ResultSet;

public interface PatchLineCommentAccess extends Access<PatchLineComment, PatchLineComment.Key> {
  @Override
  @PrimaryKey("key")
  PatchLineComment get(PatchLineComment.Key id) throws OrmException;

  @Query("WHERE key.patchKey.patchSetId.changeId = ?")
  ResultSet<PatchLineComment> byChange(Change.Id id) throws OrmException;

  @Query("WHERE key.patchKey.patchSetId = ?")
  ResultSet<PatchLineComment> byPatchSet(PatchSet.Id id) throws OrmException;

  @Query(
      "WHERE key.patchKey.patchSetId.changeId = ?"
          + " AND key.patchKey.fileName = ? AND status = '"
          + PatchLineComment.STATUS_PUBLISHED
          + "' ORDER BY lineNbr,writtenOn")
  ResultSet<PatchLineComment> publishedByChangeFile(Change.Id id, String file) throws OrmException;

  @Query(
      "WHERE key.patchKey.patchSetId = ? AND status = '" + PatchLineComment.STATUS_PUBLISHED + "'")
  ResultSet<PatchLineComment> publishedByPatchSet(PatchSet.Id patchset) throws OrmException;

  @Query(
      "WHERE key.patchKey.patchSetId = ? AND status = '"
          + PatchLineComment.STATUS_DRAFT
          + "' AND author = ? ORDER BY key.patchKey,lineNbr,writtenOn")
  ResultSet<PatchLineComment> draftByPatchSetAuthor(PatchSet.Id patchset, Account.Id author)
      throws OrmException;

  @Query(
      "WHERE key.patchKey.patchSetId.changeId = ?"
          + " AND key.patchKey.fileName = ? AND author = ? AND status = '"
          + PatchLineComment.STATUS_DRAFT
          + "' ORDER BY lineNbr,writtenOn")
  ResultSet<PatchLineComment> draftByChangeFileAuthor(Change.Id id, String file, Account.Id author)
      throws OrmException;

  @Query("WHERE status = '" + PatchLineComment.STATUS_DRAFT + "' AND author = ?")
  ResultSet<PatchLineComment> draftByAuthor(Account.Id author) throws OrmException;
}
