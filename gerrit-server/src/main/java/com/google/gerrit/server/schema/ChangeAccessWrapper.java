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
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Id;
import com.google.gerrit.reviewdb.server.ChangeAccess;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ChangeAccessWrapper extends StackTraceDumper implements ChangeAccess {
  private final ChangeAccess change;

  public ChangeAccessWrapper(ChangeAccess change) {
    this.change = change;
  }

  public Change get(Id id) throws OrmException {
    logStackTrace(id);
    return change.get(id);
  }

  public ResultSet<Change> all() throws OrmException {
    logStackTrace();
    return change.all();
  }

  public String getRelationName() {
    return change.getRelationName();
  }

  public int getRelationID() {
    return change.getRelationID();
  }

  public ResultSet<Change> iterateAllEntities() throws OrmException {
    logStackTrace();
    return change.iterateAllEntities();
  }

  public Id primaryKey(Change entity) {
    return change.primaryKey(entity);
  }

  public Map<Id, Change> toMap(Iterable<Change> c) {
    return change.toMap(c);
  }

  public CheckedFuture<Change, OrmException> getAsync(Id key) {
    logStackTrace(key);
    return change.getAsync(key);
  }

  public ResultSet<Change> get(Iterable<Id> keys) throws OrmException {
    logStackTrace(keys);
    return change.get(keys);
  }

  public void insert(Iterable<Change> instances) throws OrmException {
    change.insert(instances);
  }

  public void update(Iterable<Change> instances) throws OrmException {
    change.update(instances);
  }

  public void upsert(Iterable<Change> instances) throws OrmException {
    change.upsert(instances);
  }

  public void deleteKeys(Iterable<Id> keys) throws OrmException {
    change.deleteKeys(keys);
  }

  public void delete(Iterable<Change> instances) throws OrmException {
    change.delete(instances);
  }

  public void beginTransaction(Id key) throws OrmException {
    change.beginTransaction(key);
  }

  public Change atomicUpdate(Id key, AtomicUpdate<Change> update)
      throws OrmException {
    return change.atomicUpdate(key, update);
  }
}
