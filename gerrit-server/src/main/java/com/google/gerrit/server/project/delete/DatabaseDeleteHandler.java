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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;

public class DatabaseDeleteHandler {
  private final ReviewDb db;

  @Inject
  public DatabaseDeleteHandler(ReviewDb db) {
    this.db = db;
  }

  public void assertCanDelete(Project project)
      throws CannotDeleteProjectException, OrmException {
    if (db.submoduleSubscriptions().bySubmoduleProject(project.getNameKey())
        .iterator().hasNext()) {
      throw new CannotDeleteProjectException(
          "Project is subscribed by other projects.");
    }
  }

  public boolean hasOpenedChanged(Project project) throws OrmException {
    return db.changes().byProjectOpenAll(project.getNameKey()).iterator()
        .hasNext();
  }

  public void delete(Project project) throws OrmException {
    // TODO(davido): Why not to use 1.7 features?
    // http://docs.oracle.com/javase/specs/jls/se7/html/jls-14.html#jls-14.20.3.2
    Connection conn = ((JdbcSchema) db).getConnection();
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

  public void atomicDelete(Project project) throws OrmException {
    ResultSet<Change> changes = null;
    changes = db.changes().byProject(project.getNameKey());
    deleteChanges(changes);
    db.accountProjectWatches().delete(
        db.accountProjectWatches().byProject(project.getNameKey()));
    db.submoduleSubscriptions().delete(
        db.submoduleSubscriptions().bySuperProjectProject(
            project.getNameKey()));
  }

  private void deleteChanges(ResultSet<Change> changes)
      throws OrmException {
    for (Change change : changes) {
      Change.Id id = change.getId();
      ResultSet<PatchSet> patchSets = null;
      patchSets = db.patchSets().byChange(id);
      if (patchSets != null) {
        deleteFromPatchSets(patchSets);
      }

      db.patchComments().delete(db.patchComments().byChange(id));
      db.patchSetApprovals().delete(db.patchSetApprovals().byChange(id));
      db.changeMessages().delete(db.changeMessages().byChange(id));
      db.starredChanges().delete(db.starredChanges().byChange(id));
      db.changes().delete(Collections.singleton(change));
    }
  }

  private void deleteFromPatchSets(ResultSet<PatchSet> patchSets)
      throws OrmException {
    for (PatchSet patchSet : patchSets) {
      db.patchSetAncestors().delete(
          db.patchSetAncestors().byPatchSet(patchSet.getId()));

      db.accountPatchReviews().delete(
          db.accountPatchReviews().byPatchSet(patchSet.getId()));

      db.patchSets().delete(Collections.singleton(patchSet));
    }
  }
}
