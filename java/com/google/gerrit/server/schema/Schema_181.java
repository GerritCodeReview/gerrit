// Copyright (C) 2019 The Android Open Source Project
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
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

/** Set exclusive read permissions for admins on refs/checkers/* in All-Projects. */
public class Schema_181 implements NoteDbSchemaVersion {
  private static final String COMMIT_MSG = "Set exclusive read permissions on checker branches";

  @Override
  public void upgrade(Arguments args, UpdateUI ui) throws OrmException {
    try (Repository git = args.repoManager.openRepository(args.allProjects);
        MetaDataUpdate md =
            new MetaDataUpdate(GitReferenceUpdated.DISABLED, args.allProjects, git)) {
      md.getCommitBuilder().setAuthor(args.serverIdent);
      md.getCommitBuilder().setCommitter(args.serverIdent);
      md.setMessage(COMMIT_MSG);

      ProjectConfig config = args.projectConfigFactory.read(md);
      AccessSection checkers = config.getAccessSection(RefNames.REFS_CHECKERS + "*", true);
      Permission readRefsCheckersPermission = checkers.getPermission(Permission.READ, true);
      readRefsCheckersPermission.setExclusiveGroup(true);

      config
          .getAccessSection(AccessSection.GLOBAL_CAPABILITIES, true)
          .getPermission(GlobalCapability.ADMINISTRATE_SERVER, true)
          .getRules()
          .stream()
          .map(PermissionRule::getGroup)
          .filter(g -> readRefsCheckersPermission.getRule(g) == null)
          .forEach(g -> readRefsCheckersPermission.add(new PermissionRule(config.resolve(g))));
      config.commit(md);
    } catch (IOException | ConfigInvalidException e) {
      throw new OrmException("Failed to grant read permissions on checker refs", e);
    }
  }
}
