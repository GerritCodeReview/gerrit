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

package com.google.gerrit.server.notedb;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Id;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.notedb.NoteDbUpdateManager.Result;
import com.google.gerrit.server.notedb.rebuild.ChangeRebuilder;
import com.google.gerrit.server.notedb.rebuild.ChangeRebuilderImpl;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

@VisibleForTesting
@Singleton
public class TestChangeRebuilderWrapper extends ChangeRebuilder {
  private final ChangeRebuilderImpl delegate;
  private final AtomicBoolean failNextUpdate;
  private final AtomicBoolean stealNextUpdate;

  @Inject
  TestChangeRebuilderWrapper(SchemaFactory<ReviewDb> schemaFactory, ChangeRebuilderImpl rebuilder) {
    super(schemaFactory);
    this.delegate = rebuilder;
    this.failNextUpdate = new AtomicBoolean();
    this.stealNextUpdate = new AtomicBoolean();
  }

  public void failNextUpdate() {
    failNextUpdate.set(true);
  }

  public void stealNextUpdate() {
    stealNextUpdate.set(true);
  }

  @Override
  public Result rebuild(ReviewDb db, Change.Id changeId) throws IOException, OrmException {
    return rebuild(db, changeId, true);
  }

  @Override
  public Result rebuildEvenIfReadOnly(ReviewDb db, Change.Id changeId)
      throws IOException, OrmException {
    return rebuild(db, changeId, false);
  }

  private Result rebuild(ReviewDb db, Change.Id changeId, boolean checkReadOnly)
      throws IOException, OrmException {
    if (failNextUpdate.getAndSet(false)) {
      throw new IOException("Update failed");
    }
    Result result =
        checkReadOnly
            ? delegate.rebuild(db, changeId)
            : delegate.rebuildEvenIfReadOnly(db, changeId);
    if (stealNextUpdate.getAndSet(false)) {
      throw new IOException("Update stolen");
    }
    return result;
  }

  @Override
  public Result rebuild(NoteDbUpdateManager manager, ChangeBundle bundle)
      throws IOException, OrmException {
    // stealNextUpdate doesn't really apply in this case because the IOException
    // would normally come from the manager.execute() method, which isn't called
    // here.
    return delegate.rebuild(manager, bundle);
  }

  @Override
  public NoteDbUpdateManager stage(ReviewDb db, Change.Id changeId)
      throws IOException, OrmException {
    // Don't inspect stealNextUpdate; that happens in execute() below.
    return delegate.stage(db, changeId);
  }

  @Override
  public Result execute(ReviewDb db, Change.Id changeId, NoteDbUpdateManager manager)
      throws OrmException, IOException {
    if (failNextUpdate.getAndSet(false)) {
      throw new IOException("Update failed");
    }
    Result result = delegate.execute(db, changeId, manager);
    if (stealNextUpdate.getAndSet(false)) {
      throw new IOException("Update stolen");
    }
    return result;
  }

  @Override
  public void buildUpdates(NoteDbUpdateManager manager, ChangeBundle bundle)
      throws IOException, OrmException {
    // Don't check for manual failure; that happens in execute().
    delegate.buildUpdates(manager, bundle);
  }

  @Override
  public void rebuildReviewDb(ReviewDb db, Project.NameKey project, Id changeId)
      throws OrmException {
    if (failNextUpdate.getAndSet(false)) {
      throw new OrmException("Update failed");
    }
    delegate.rebuildReviewDb(db, project, changeId);
  }
}
