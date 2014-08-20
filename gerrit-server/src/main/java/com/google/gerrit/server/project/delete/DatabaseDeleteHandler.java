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

package com.google.gerrit.server.project.delete;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;

@Singleton
public class DatabaseDeleteHandler {
  private final Provider<ReviewDb> db;

  @Inject
  public DatabaseDeleteHandler(Provider<ReviewDb> db) {
    this.db = db;
  }

  public void assertCanDelete(Project project)
      throws CannotDeleteProjectException, OrmException {
    if (db.get().submoduleSubscriptions()
        .bySubmoduleProject(project.getNameKey())
        .iterator().hasNext()) {
      throw new CannotDeleteProjectException(
          "Project is subscribed by other projects.");
    }
  }

  public boolean hasOpenedChanged(Project project) throws OrmException {
    return db.get().changes().byProjectOpenAll(project.getNameKey()).iterator()
        .hasNext();
  }

  public void delete(Project project) throws OrmException {
    delete(project.getNameKey());
  }

  public void delete(Project.NameKey project) throws OrmException {
    // TODO(davido): Why not to use 1.7 features?
    // http://docs.oracle.com/javase/specs/jls/se7/html/jls-14.html#jls-14.20.3.2
    Connection conn = ((JdbcSchema) db.get()).getConnection();
    try {
      conn.setAutoCommit(false);
      try {
        atomicDelete(project);
        conn.commit();
      } finally {
        conn.setAutoCommit(true);
      }
    } catch (SQLException e) {
      try {
        conn.rollback();
      } catch (SQLException ex) {
        throw new OrmException(ex);
      }
      throw new OrmException(e);
    }
  }

  public void atomicDelete(Project.NameKey project) throws OrmException {
    ResultSet<Change> changes = null;
    changes = db.get().changes().byProject(project);
    deleteChanges(changes);
    db.get().accountProjectWatches().delete(
        db.get().accountProjectWatches().byProject(project));
    db.get().submoduleSubscriptions().delete(
        db.get().submoduleSubscriptions().bySuperProjectProject(
            project));
  }

  private void deleteChanges(ResultSet<Change> changes)
      throws OrmException {
    for (Change change : changes) {
      Change.Id id = change.getId();
      ResultSet<PatchSet> patchSets = null;
      patchSets = db.get().patchSets().byChange(id);
      if (patchSets != null) {
        deleteFromPatchSets(patchSets);
      }

      db.get().patchComments().delete(
          db.get().patchComments().byChange(id));
      db.get().patchSetApprovals().delete(
          db.get().patchSetApprovals().byChange(id));
      db.get().changeMessages().delete(
          db.get().changeMessages().byChange(id));
      db.get().starredChanges().delete(
          db.get().starredChanges().byChange(id));
      db.get().changes().delete(Collections.singleton(change));
    }
  }

  private void deleteFromPatchSets(ResultSet<PatchSet> patchSets)
      throws OrmException {
    for (PatchSet patchSet : patchSets) {
      db.get().patchSetAncestors().delete(
          db.get().patchSetAncestors().byPatchSet(patchSet.getId()));

      db.get().accountPatchReviews().delete(
          db.get().accountPatchReviews().byPatchSet(patchSet.getId()));

      db.get().patchSets().delete(Collections.singleton(patchSet));
    }
  }
}
