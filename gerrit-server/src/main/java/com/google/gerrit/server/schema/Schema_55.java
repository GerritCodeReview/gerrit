// Copyright (C) 2011 The Android Open Source Project
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

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.util.Collections;

public class Schema_55 extends SchemaVersion {
  private final LocalDiskRepositoryManager mgr;

  @Inject
  Schema_55(Provider<Schema_54> prior, LocalDiskRepositoryManager mgr) {
    super(prior);
    this.mgr = mgr;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    SystemConfig sc = db.systemConfig().get(new SystemConfig.Key());
    String oldName = sc.wildProjectName.get();
    String newName = "All-Projects";
    if ("-- All Projects --".equals(oldName)) {
      ui.message("Renaming \"" + oldName + "\" to \"" + newName + "\"");

      File base = mgr.getBasePath();
      File oldDir = FileKey.resolve(new File(base, oldName), FS.DETECTED);
      File newDir = new File(base, newName + Constants.DOT_GIT_EXT);
      if (!oldDir.renameTo(newDir)) {
        throw new OrmException("Cannot rename " + oldDir.getAbsolutePath()
            + " to " + newDir.getAbsolutePath());
      }

      sc.wildProjectName = new Project.NameKey(newName);
      db.systemConfig().update(Collections.singleton(sc));
    }
  }
}
