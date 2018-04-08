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

import static com.google.gerrit.server.group.SystemGroupBackend.PROJECT_OWNERS;
import static java.util.stream.Collectors.toSet;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.config.AllProjectsName;
import com.google.gerrit.config.GerritPersonIdent;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

public class Schema_135 extends SchemaVersion {
  private static final String COMMIT_MSG =
      "Allow admins and project owners to create refs/meta/config";

  private final GitRepositoryManager repoManager;
  private final AllProjectsName allProjectsName;
  private final SystemGroupBackend systemGroupBackend;
  private final PersonIdent serverUser;

  @Inject
  Schema_135(
      Provider<Schema_134> prior,
      GitRepositoryManager repoManager,
      AllProjectsName allProjectsName,
      SystemGroupBackend systemGroupBackend,
      @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.repoManager = repoManager;
    this.allProjectsName = allProjectsName;
    this.systemGroupBackend = systemGroupBackend;
    this.serverUser = serverUser;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    try (Repository git = repoManager.openRepository(allProjectsName);
        MetaDataUpdate md =
            new MetaDataUpdate(GitReferenceUpdated.DISABLED, allProjectsName, git)) {
      ProjectConfig config = ProjectConfig.read(md);

      AccessSection meta = config.getAccessSection(RefNames.REFS_CONFIG, true);
      Permission createRefsMetaConfigPermission = meta.getPermission(Permission.CREATE, true);

      Set<GroupReference> groups =
          Stream.concat(
                  config
                      .getAccessSection(AccessSection.GLOBAL_CAPABILITIES, true)
                      .getPermission(GlobalCapability.ADMINISTRATE_SERVER, true)
                      .getRules()
                      .stream()
                      .map(PermissionRule::getGroup),
                  Stream.of(systemGroupBackend.getGroup(PROJECT_OWNERS)))
              .filter(g -> createRefsMetaConfigPermission.getRule(g) == null)
              .collect(toSet());

      for (GroupReference group : groups) {
        createRefsMetaConfigPermission.add(new PermissionRule(config.resolve(group)));
      }

      md.getCommitBuilder().setAuthor(serverUser);
      md.getCommitBuilder().setCommitter(serverUser);
      md.setMessage(COMMIT_MSG);
      config.commit(md);
    } catch (ConfigInvalidException | IOException ex) {
      throw new OrmException(ex);
    }
  }
}
