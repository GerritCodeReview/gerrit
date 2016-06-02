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
import com.google.common.collect.ImmutableMultimap;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

@VisibleForTesting
@Singleton
public class TestChangeRebuilderWrapper extends ChangeRebuilder {
  private final ChangeRebuilderImpl delegate;
  private final AtomicBoolean stealNextUpdate;

  @Inject
  TestChangeRebuilderWrapper(SchemaFactory<ReviewDb> schemaFactory,
      ChangeRebuilderImpl rebuilder) {
    super(schemaFactory);
    this.delegate = rebuilder;
    this.stealNextUpdate = new AtomicBoolean();
  }

  public void stealNextUpdate() {
    stealNextUpdate.set(true);
  }

  @Override
  public NoteDbChangeState rebuild(ReviewDb db, Change.Id changeId)
      throws NoSuchChangeException, IOException, OrmException,
      ConfigInvalidException {
    NoteDbChangeState result = delegate.rebuild(db, changeId);
    if (stealNextUpdate.getAndSet(false)) {
      throw new IOException("Update stolen");
    }
    return result;
  }

  @Override
  public NoteDbChangeState rebuild(NoteDbUpdateManager manager,
      ChangeBundle bundle) throws NoSuchChangeException, IOException,
      OrmException, ConfigInvalidException {
    // stealNextUpdate doesn't really apply in this case because the IOException
    // would normally come from the manager.execute() method, which isn't called
    // here.
    return delegate.rebuild(manager, bundle);
  }

  @Override
  public boolean rebuildProject(ReviewDb db,
      ImmutableMultimap<Project.NameKey, Change.Id> allChanges,
      Project.NameKey project, Repository allUsersRepo)
      throws NoSuchChangeException, IOException, OrmException,
      ConfigInvalidException {
    boolean result =
        delegate.rebuildProject(db, allChanges, project, allUsersRepo);
    if (stealNextUpdate.getAndSet(false)) {
      throw new IOException("Update stolen");
    }
    return result;
  }
}
