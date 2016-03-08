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

package com.google.gerrit.server.schema;

import com.google.gerrit.common.data.SubscribeSection;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.SubmoduleSubscription;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.SubmoduleSubscriptionAccess;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.sql.SQLException;

public class Schema_120 extends SchemaVersion {

  private final GitRepositoryManager mgr;

  @Inject
  Schema_120(Provider<Schema_119> prior,
      GitRepositoryManager mgr) {
    super(prior);
    this.mgr = mgr;
  }

  protected void allowSubmoduleSubscription(Branch.NameKey submodule,
      Branch.NameKey superProject) throws OrmException {
    try (Repository git = mgr.openRepository(submodule.getParentKey());
        RevWalk rw = new RevWalk(git)) {
      BatchRefUpdate bru = git.getRefDatabase().newBatchUpdate();
      try(MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED,
          submodule.getParentKey(), git, bru)) {
        md.setMessage("Added superproject subscription during upgrade");
        ProjectConfig pc = ProjectConfig.read(md);
        SubscribeSection s = new SubscribeSection(superProject.getParentKey());
        s.addRefSpec(submodule.get() + ":" + superProject.get());
        pc.addSubscribeSection(s);
        pc.commit(md);
      }
      bru.execute(rw, NullProgressMonitor.INSTANCE);
    } catch (ConfigInvalidException | IOException e) {
      throw new OrmException(e);
    }
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui)
      throws OrmException, SQLException {
    boolean conversionError = false;
    ui.message("Moving Superproject subscriptions table to submodule ACLs");
    SubmoduleSubscriptionAccess ssa = db.submoduleSubscriptions();

    for (SubmoduleSubscription s : ssa.all()) {
      Branch.NameKey sub = s.getSubmodule();
      Branch.NameKey sup = s.getSuperProject();
      try {
        allowSubmoduleSubscription(sub, s.getSuperProject());
      } catch (OrmException e) {
        ui.message("Error when enabling superproject ("
            + sup.getParentKey()
            +") subscription ACL in submodule (" + sub.getParentKey() + "):"
            + e.getMessage());
        conversionError = true;
      }
    }

    if (!conversionError) {
      execute(db, "DROP TABLE submodule_subscriptions;");
    } else {
      ui.message("After fixing the errors above you may want to drop the "
          + "'submodule_subscriptions' table");
    }
  }

}
