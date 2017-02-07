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

package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ChangeAccess;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbWrapper;
import com.google.gwtorm.server.AtomicUpdate;

public class BatchUpdateReviewDb extends ReviewDbWrapper {
  private final ChangeAccess changesWrapper;

  BatchUpdateReviewDb(ReviewDb delegate) {
    super(delegate);
    changesWrapper = new BatchUpdateChanges(delegate.changes());
  }

  public ReviewDb unsafeGetDelegate() {
    return delegate;
  }

  @Override
  public ChangeAccess changes() {
    return changesWrapper;
  }

  @Override
  public void commit() {
    throw new UnsupportedOperationException(
        "do not call commit; BatchUpdate always manages transactions");
  }

  @Override
  public void rollback() {
    throw new UnsupportedOperationException(
        "do not call rollback; BatchUpdate always manages transactions");
  }

  private static class BatchUpdateChanges extends ChangeAccessWrapper {
    private BatchUpdateChanges(ChangeAccess delegate) {
      super(delegate);
    }

    @Override
    public void insert(Iterable<Change> instances) {
      throw new UnsupportedOperationException(
          "do not call insert; change is automatically inserted");
    }

    @Override
    public void upsert(Iterable<Change> instances) {
      throw new UnsupportedOperationException(
          "do not call upsert; existing changes are updated automatically,"
              + " or use InsertChangeOp for insertion");
    }

    @Override
    public void update(Iterable<Change> instances) {
      throw new UnsupportedOperationException(
          "do not call update; change is updated automatically");
    }

    @Override
    public void beginTransaction(Change.Id key) {
      throw new UnsupportedOperationException("updateChange is always called within a transaction");
    }

    @Override
    public void deleteKeys(Iterable<Change.Id> keys) {
      throw new UnsupportedOperationException(
          "do not call deleteKeys; use ChangeContext#deleteChange()");
    }

    @Override
    public void delete(Iterable<Change> instances) {
      throw new UnsupportedOperationException(
          "do not call delete; use ChangeContext#deleteChange()");
    }

    @Override
    public Change atomicUpdate(Change.Id key, AtomicUpdate<Change> update) {
      throw new UnsupportedOperationException(
          "do not call atomicUpdate; updateChange is always called within a" + " transaction");
    }
  }
}
