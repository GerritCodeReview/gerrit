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

package com.google.gerrit.server.schema;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.sql.SQLException;

public class Schema_81 extends SchemaVersion {

  private final File pluginsDir;
  private final GitRepositoryManager mgr;
  private final AllProjectsName allProjects;
  private final PersonIdent serverUser;

  @Inject
  Schema_81(Provider<Schema_80> prior, SitePaths sitePaths,
      AllProjectsName allProjects, GitRepositoryManager mgr,
      @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.pluginsDir = sitePaths.plugins_dir;
    this.mgr = mgr;
    this.allProjects = allProjects;
    this.serverUser = serverUser;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException,
      SQLException {
    try {
      migrateStartReplicationCapability(db, scanForReplicationPlugin());
    } catch (RepositoryNotFoundException e) {
      throw new OrmException(e);
    } catch (SQLException e) {
      throw new OrmException(e);
    } catch (IOException e) {
      throw new OrmException(e);
    } catch (ConfigInvalidException e) {
      throw new OrmException(e);
    }
  }

  private File[] scanForReplicationPlugin() {
    File[] matches = null;
    if (pluginsDir != null && pluginsDir.exists()) {
      matches = pluginsDir.listFiles(new FileFilter() {
        @Override
        public boolean accept(File pathname) {
          String n = pathname.getName();
          return (n.endsWith(".jar") || n.endsWith(".jar.disabled"))
              && pathname.isFile() && n.indexOf("replication") >= 0;
        }
      });
    }
    return matches;
  }

  private void migrateStartReplicationCapability(ReviewDb db, File[] matches)
      throws SQLException, RepositoryNotFoundException, IOException,
      ConfigInvalidException {
    Description d = new Description();
    if (matches == null || matches.length == 0) {
      d.what = Description.Action.REMOVE;
    } else {
      d.what = Description.Action.RENAME;
      d.prefix = nameOf(matches[0]);
    }
    migrateStartReplicationCapability(db, d);
  }

  private void migrateStartReplicationCapability(ReviewDb db, Description d)
      throws SQLException, RepositoryNotFoundException, IOException,
      ConfigInvalidException {
    Repository git = mgr.openRepository(allProjects);
    try {
      MetaDataUpdate md =
          new MetaDataUpdate(GitReferenceUpdated.DISABLED, allProjects, git);
      md.getCommitBuilder().setAuthor(serverUser);
      md.getCommitBuilder().setCommitter(serverUser);
      ProjectConfig config = ProjectConfig.read(md);
      AccessSection capabilities =
          config.getAccessSection(AccessSection.GLOBAL_CAPABILITIES);
      Permission startReplication =
          capabilities.getPermission("startReplication");
      if (startReplication == null) {
        return;
      }
      String msg = null;
      switch (d.what) {
        case REMOVE:
          capabilities.remove(startReplication);
          msg = "Remove startReplication capability, plugin not installed\n";
          break;
        case RENAME:
          capabilities.remove(startReplication);
          Permission pluginStartReplication =
              capabilities.getPermission(
                  String.format("%s-startReplication", d.prefix), true);
          pluginStartReplication.setRules(startReplication.getRules());
          msg = "Rename startReplication capability to match updated plugin\n";
          break;
      }
      config.replace(capabilities);
      md.setMessage(msg);
      config.commit(md);
    } finally {
      git.close();
    }
  }

  private static String nameOf(File jar) {
    String name = jar.getName();
    if (name.endsWith(".disabled")) {
      name = name.substring(0, name.lastIndexOf('.'));
    }
    int ext = name.lastIndexOf('.');
    return 0 < ext ? name.substring(0, ext) : name;
  }

  private static class Description {
    private enum Action {
      REMOVE, RENAME
    }
    Action what;
    String prefix;
  }
}
