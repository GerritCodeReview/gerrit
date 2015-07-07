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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class Schema_109 extends SchemaVersion {

  @Inject
  Schema_109(Provider<Schema_108> prior,
      @SuppressWarnings("unused") GitRepositoryManager repoManager) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    ui.message("Listing all changes ...");
    SetMultimap<Project.NameKey, Change.Id> openByProject =
        getOpenChangesByProject(db);
    ui.message("done");

    ui.message("Setting all submitted changes back to new ...");
    int i = 0;
    for (Map.Entry<Project.NameKey, Collection<Change.Id>> e
        : openByProject.asMap().entrySet()) {
      updateOpenChanges(db, (Set<Change.Id>) e.getValue());
      if (++i % 100 == 0) {
        ui.message("  done " + i + " projects ...");
      }
    }
    ui.message("done");
  }

  private static void updateOpenChanges(ReviewDb db, Set<Change.Id> changes)
      throws OrmException {
    for (Change.Id id : changes) {
      Change c = db.changes().get(id);
      if (c.getStatus() != null) {
        db.changes().atomicUpdate(id, new AtomicUpdate<Change>() {
          @Override
          public Change update(Change c) {
            c.setStatus(Change.Status.NEW);
            // TODO: add a review note informing the users ?
            ChangeUtil.updated(c);
            return c;
          }
        });
      }
    }
  }

  private SetMultimap<Project.NameKey, Change.Id> getOpenChangesByProject(
      ReviewDb db) throws OrmException {
    SetMultimap<Project.NameKey, Change.Id> openByProject =
        HashMultimap.create();
    for (Change c : db.changes().all()) {
      if (c.getStatus().isOpen()) {
        openByProject.put(c.getProject(), c.getId());
      }
    }
    return openByProject;
  }
}
