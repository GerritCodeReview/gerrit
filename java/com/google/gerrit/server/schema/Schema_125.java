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

import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.schema.AclUtil.grant;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.config.AllProjectsName;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.config.GerritPersonIdent;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.RefPattern;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

public class Schema_125 extends SchemaVersion {
  private static final String COMMIT_MSG =
      "Assign default permissions on user branches\n"
          + "\n"
          + "By default each user should be able to read and update the own user\n"
          + "branch. Also the user should be able to approve and submit changes for\n"
          + "the own user branch. Assign default permissions for this and remove the\n"
          + "old exclusive read protection from the user branches.\n";

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final AllProjectsName allProjectsName;
  private final SystemGroupBackend systemGroupBackend;
  private final PersonIdent serverUser;

  @Inject
  Schema_125(
      Provider<Schema_124> prior,
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      AllProjectsName allProjectsName,
      SystemGroupBackend systemGroupBackend,
      @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.allProjectsName = allProjectsName;
    this.systemGroupBackend = systemGroupBackend;
    this.serverUser = serverUser;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    try (Repository git = repoManager.openRepository(allUsersName);
        MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED, allUsersName, git)) {
      ProjectConfig config = ProjectConfig.read(md);

      config
          .getAccessSection(RefNames.REFS_USERS + "*", true)
          .remove(new Permission(Permission.READ));
      GroupReference registered = systemGroupBackend.getGroup(REGISTERED_USERS);
      AccessSection users =
          config.getAccessSection(
              RefNames.REFS_USERS + "${" + RefPattern.USERID_SHARDED + "}", true);
      grant(config, users, Permission.READ, true, registered);
      grant(config, users, Permission.PUSH, true, registered);
      grant(config, users, Permission.SUBMIT, true, registered);

      for (LabelType lt : getLabelTypes(config)) {
        if ("Code-Review".equals(lt.getName()) || "Verified".equals(lt.getName())) {
          grant(config, users, lt, lt.getMin().getValue(), lt.getMax().getValue(), registered);
        }
      }

      md.getCommitBuilder().setAuthor(serverUser);
      md.getCommitBuilder().setCommitter(serverUser);
      md.setMessage(COMMIT_MSG);
      config.commit(md);
    } catch (ConfigInvalidException | IOException ex) {
      throw new OrmException(ex);
    }
  }

  private Collection<LabelType> getLabelTypes(ProjectConfig config)
      throws IOException, ConfigInvalidException {
    Map<String, LabelType> labelTypes = new HashMap<>(config.getLabelSections());
    Project.NameKey parent = config.getProject().getParent(allProjectsName);
    while (parent != null) {
      try (Repository git = repoManager.openRepository(parent);
          MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED, parent, git)) {
        ProjectConfig parentConfig = ProjectConfig.read(md);
        for (LabelType lt : parentConfig.getLabelSections().values()) {
          if (!labelTypes.containsKey(lt.getName())) {
            labelTypes.put(lt.getName(), lt);
          }
        }
        parent = parentConfig.getProject().getParent(allProjectsName);
      }
    }
    return labelTypes.values();
  }
}
