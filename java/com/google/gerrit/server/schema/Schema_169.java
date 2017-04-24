// Copyright (C) 2017 The Android Open Source Project
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

import static com.google.gerrit.common.data.Permission.ADD_PATCH_SET;
import static com.google.gerrit.common.data.Permission.CREATE_REVIEW;
import static com.google.gerrit.common.data.Permission.PUSH;
import static com.google.gerrit.common.data.Permission.PUSH_MERGE;
import static com.google.gerrit.common.data.Permission.SUBMIT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class Schema_169 extends SchemaVersion {
  private static final String COMMIT_MSG = "Rewrite project.config to get rid of the refs/for/ ACLs";

  private final GitRepositoryManager repoManager;
  private final PersonIdent serverUser;

  @Inject
  Schema_169(
      Provider<Schema_168> prior,
      GitRepositoryManager repoManager,
      @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.repoManager = repoManager;
    this.serverUser = serverUser;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    ui.message("Migrating away from refs/for/ namespace for ACLs...");

    for (Project.NameKey projectName : repoManager.list()) {
      try (Repository git = repoManager.openRepository(projectName);
          MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED, projectName, git)) {
        ProjectConfig config = ProjectConfig.read(md);

        boolean configUpdated = false;
        List<AccessSection> accessSectionsToRemove = new ArrayList<>();
        for (AccessSection accessSection : config.getAccessSections()) {
          String sourceRef = accessSection.getName();
          if (accessSection.getName().startsWith("refs/for/")) {
            // refs/for/* => refs/*
            // refs/for/refs/heads/master => refs/heads/master
            String destinationRef = sourceRef.replaceAll("for/(refs/)?", "");
            List<Permission> permissionsToRemove = new ArrayList<>();
            List<Permission> permissions = accessSection.getPermissions();
            for (Permission permission : permissions) {
              switch (permission.getName()) {
                case PUSH:
                case PUSH_MERGE:
                case ADD_PATCH_SET:
                case SUBMIT:
                  List<PermissionRule> rulesToRemove = new ArrayList<>();
                  AccessSection destSection = config.getAccessSection(destinationRef, true);
                  for (PermissionRule rule : permission.getRules()) {
                    updateRule(destSection, permission, rule);
                    rulesToRemove.add(rule);
                    configUpdated = true;
                  }
                  rulesToRemove.stream().forEach(r -> permission.remove(r));
                  if (permission.getRules().isEmpty()) {
                    permissionsToRemove.add(permission);
                  }
              }
            }
            permissionsToRemove.stream().forEach(p -> accessSection.remove(p));
            if (accessSection.getPermissions().isEmpty()) {
              accessSectionsToRemove.add(accessSection);
            }
          } else if (sourceRef.startsWith("refs/*")) {
            // Push [refs/*] => Push + Create Review [refs/*]
            for (Permission permission : accessSection.getPermissions()) {
              if (permission.getName().equals(PUSH) || permission.getName().equals(SUBMIT)) {
                for (PermissionRule rule : permission.getRules()) {
                  updateRule(accessSection, permission, rule);
                  configUpdated = true;
                  break;
                }
              }
            }
          }
        }
        accessSectionsToRemove.stream().forEach(s -> config.remove(s));

        if (configUpdated) {
          md.getCommitBuilder().setAuthor(serverUser);
          md.getCommitBuilder().setCommitter(serverUser);
          md.setMessage(COMMIT_MSG);
          config.commit(md);
          ui.message("Updated project.config for " + projectName.toString() + "...");
        }
      } catch (ConfigInvalidException | IOException ex) {
        throw new OrmException(ex);
      }
    }
  }

  public static void updateRule(
      AccessSection destSection,
      Permission sourcePermission,
      PermissionRule sourceRule) {
    String pn = sourcePermission.getName();
    Permission p = destSection.getPermission(pn.equals(PUSH) ? CREATE_REVIEW : pn, true);
    p.setExclusiveGroup(sourcePermission.getExclusiveGroup());
    PermissionRule r = p.getRule(sourceRule.getGroup(), true);
    r.setForce(pn.equals(SUBMIT) ? true : sourceRule.getForce());
    r.setAction(sourceRule.getAction());
  }
}
