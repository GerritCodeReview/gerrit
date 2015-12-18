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
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSet.Id;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.PatchSetAccess;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;

import java.util.Map;

public class PatchSetAccessWrapper extends StackTraceDumper implements PatchSetAccess {
  private final PatchSetAccess patchSetAccess;

  public PatchSetAccessWrapper(PatchSetAccess patchSetAccess) {
    super();
    this.patchSetAccess = patchSetAccess;
  }

  public PatchSet get(Id id) throws OrmException {
    return patchSetAccess.get(id);
  }

  public ResultSet<PatchSet> byChange(
      com.google.gerrit.reviewdb.client.Change.Id id) throws OrmException {
    logStackTrace(id);
    return patchSetAccess.byChange(id);
  }

  public ResultSet<PatchSet> byRevision(RevId rev) throws OrmException {
    return patchSetAccess.byRevision(rev);
  }

  public ResultSet<PatchSet> byRevisionRange(RevId reva, RevId revb)
      throws OrmException {
    return patchSetAccess.byRevisionRange(reva, revb);
  }

  public String getRelationName() {
    return patchSetAccess.getRelationName();
  }

  public int getRelationID() {
    return patchSetAccess.getRelationID();
  }

  public ResultSet<PatchSet> iterateAllEntities() throws OrmException {
    return patchSetAccess.iterateAllEntities();
  }

  public Id primaryKey(PatchSet entity) {
    return patchSetAccess.primaryKey(entity);
  }

  public Map<Id, PatchSet> toMap(Iterable<PatchSet> c) {
    return patchSetAccess.toMap(c);
  }

  public CheckedFuture<PatchSet, OrmException> getAsync(Id key) {
    return patchSetAccess.getAsync(key);
  }

  public ResultSet<PatchSet> get(Iterable<Id> keys) throws OrmException {
    return patchSetAccess.get(keys);
  }

  public void insert(Iterable<PatchSet> instances) throws OrmException {
    patchSetAccess.insert(instances);
  }

  public void update(Iterable<PatchSet> instances) throws OrmException {
    patchSetAccess.update(instances);
  }

  public void upsert(Iterable<PatchSet> instances) throws OrmException {
    patchSetAccess.upsert(instances);
  }

  public void deleteKeys(Iterable<Id> keys) throws OrmException {
    patchSetAccess.deleteKeys(keys);
  }

  public void delete(Iterable<PatchSet> instances) throws OrmException {
    patchSetAccess.delete(instances);
  }

  public void beginTransaction(Id key) throws OrmException {
    patchSetAccess.beginTransaction(key);
  }

  public PatchSet atomicUpdate(Id key, AtomicUpdate<PatchSet> update)
      throws OrmException {
    return patchSetAccess.atomicUpdate(key, update);
  }


}
