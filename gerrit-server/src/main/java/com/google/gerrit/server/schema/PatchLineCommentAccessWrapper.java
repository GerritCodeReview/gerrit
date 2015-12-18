// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.gerrit.reviewdb.client.Change.Id;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchLineComment.Key;
import com.google.gerrit.reviewdb.server.PatchLineCommentAccess;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;

import java.util.Map;

public class PatchLineCommentAccessWrapper extends StackTraceDumper implements PatchLineCommentAccess {
  private final PatchLineCommentAccess patch;

  public PatchLineCommentAccessWrapper(PatchLineCommentAccess patch) {
    super();
    this.patch = patch;
  }

  public PatchLineComment get(Key id) throws OrmException {
    logStackTrace(id);
    return patch.get(id);
  }

  public ResultSet<PatchLineComment> byChange(Id id) throws OrmException {
    logStackTrace(id);
    return patch.byChange(id);
  }

  public ResultSet<PatchLineComment> byPatchSet(
      com.google.gerrit.reviewdb.client.PatchSet.Id id) throws OrmException {
    logStackTrace(id);
    return patch.byPatchSet(id);
  }

  public ResultSet<PatchLineComment> publishedByChangeFile(Id id, String file)
      throws OrmException {
    logStackTrace(id,file);
    return patch.publishedByChangeFile(id, file);
  }

  public ResultSet<PatchLineComment> publishedByPatchSet(
      com.google.gerrit.reviewdb.client.PatchSet.Id patchset)
      throws OrmException {
    logStackTrace(patchset);
    return patch.publishedByPatchSet(patchset);
  }

  public String getRelationName() {
    return patch.getRelationName();
  }

  public ResultSet<PatchLineComment> draftByPatchSetAuthor(
      com.google.gerrit.reviewdb.client.PatchSet.Id patchset,
      com.google.gerrit.reviewdb.client.Account.Id author) throws OrmException {
    logStackTrace(patchset, author);
    return patch.draftByPatchSetAuthor(patchset, author);
  }

  public int getRelationID() {
    return patch.getRelationID();
  }

  public ResultSet<PatchLineComment> iterateAllEntities() throws OrmException {
    logStackTrace();
    return patch.iterateAllEntities();
  }

  public ResultSet<PatchLineComment> draftByChangeFileAuthor(Id id,
      String file, com.google.gerrit.reviewdb.client.Account.Id author)
      throws OrmException {
    logStackTrace(id, file, author);
    return patch.draftByChangeFileAuthor(id, file, author);
  }

  public Key primaryKey(PatchLineComment entity) {
    logStackTrace(entity);
    return patch.primaryKey(entity);
  }

  public ResultSet<PatchLineComment> draftByAuthor(
      com.google.gerrit.reviewdb.client.Account.Id author) throws OrmException {
    logStackTrace(author);
    return patch.draftByAuthor(author);
  }

  public Map<Key, PatchLineComment> toMap(Iterable<PatchLineComment> c) {
    return patch.toMap(c);
  }

  public CheckedFuture<PatchLineComment, OrmException> getAsync(Key key) {
    logStackTrace(key);
    return patch.getAsync(key);
  }

  public ResultSet<PatchLineComment> get(Iterable<Key> keys)
      throws OrmException {
    logStackTrace(keys);
    return patch.get(keys);
  }

  public void insert(Iterable<PatchLineComment> instances) throws OrmException {
    patch.insert(instances);
  }

  public void update(Iterable<PatchLineComment> instances) throws OrmException {
    patch.update(instances);
  }

  public void upsert(Iterable<PatchLineComment> instances) throws OrmException {
    patch.upsert(instances);
  }

  public void deleteKeys(Iterable<Key> keys) throws OrmException {
    patch.deleteKeys(keys);
  }

  public void delete(Iterable<PatchLineComment> instances) throws OrmException {
    patch.delete(instances);
  }

  public void beginTransaction(Key key) throws OrmException {
    patch.beginTransaction(key);
  }

  public PatchLineComment atomicUpdate(Key key,
      AtomicUpdate<PatchLineComment> update) throws OrmException {
    return patch.atomicUpdate(key, update);
  }
}
