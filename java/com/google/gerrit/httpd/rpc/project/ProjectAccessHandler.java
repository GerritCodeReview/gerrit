// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.project;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.gerrit.common.ProjectAccessUtil.mergeSections;

import com.google.common.base.MoreObjects;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.errors.InvalidNameException;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.common.errors.PermissionDeniedException;
import com.google.gerrit.common.errors.UpdateParentFailedException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupBackends;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ContributorAgreementsChecker;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.RefPattern;
import com.google.gerrit.server.project.SetParent;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;

public abstract class ProjectAccessHandler<T> extends Handler<T> {

  protected final GroupBackend groupBackend;
  protected final Project.NameKey projectName;
  protected final ObjectId base;
  protected final CurrentUser user;

  private final MetaDataUpdate.User metaDataUpdateFactory;
  private final AllProjectsName allProjects;
  private final AllUsersName allUsers;
  private final Provider<SetParent> setParent;
  private final ContributorAgreementsChecker contributorAgreements;
  private final PermissionBackend permissionBackend;
  private final Project.NameKey parentProjectName;

  protected String message;

  private List<AccessSection> sectionList;
  private boolean checkIfOwner;
  private Boolean canWriteConfig;

  protected ProjectAccessHandler(
      GroupBackend groupBackend,
      MetaDataUpdate.User metaDataUpdateFactory,
      AllProjectsName allProjects,
      AllUsersName allUsers,
      Provider<SetParent> setParent,
      CurrentUser user,
      Project.NameKey projectName,
      ObjectId base,
      List<AccessSection> sectionList,
      Project.NameKey parentProjectName,
      String message,
      ContributorAgreementsChecker contributorAgreements,
      PermissionBackend permissionBackend,
      boolean checkIfOwner) {
    this.groupBackend = groupBackend;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.allProjects = allProjects;
    this.allUsers = allUsers;
    this.setParent = setParent;
    this.user = user;

    this.projectName = projectName;
    this.base = base;
    this.sectionList = sectionList;
    this.parentProjectName = parentProjectName;
    this.message = message;
    this.contributorAgreements = contributorAgreements;
    this.permissionBackend = permissionBackend;
    this.checkIfOwner = checkIfOwner;
  }

  @Override
  public final T call()
      throws NoSuchProjectException, IOException, ConfigInvalidException, InvalidNameException,
          NoSuchGroupException, OrmException, UpdateParentFailedException,
          PermissionDeniedException, PermissionBackendException {
    try {
      contributorAgreements.check(projectName, user);
    } catch (AuthException e) {
      throw new PermissionDeniedException(e.getMessage());
    }

    try (MetaDataUpdate md = metaDataUpdateFactory.create(projectName)) {
      ProjectConfig config = ProjectConfig.read(md, base);
      Set<String> toDelete = scanSectionNames(config);
      PermissionBackend.ForProject forProject = permissionBackend.user(user).project(projectName);

      for (AccessSection section : mergeSections(sectionList)) {
        String name = section.getName();

        if (AccessSection.GLOBAL_CAPABILITIES.equals(name)) {
          if (checkIfOwner && !canWriteConfig()) {
            continue;
          }
          replace(config, toDelete, section);

        } else if (AccessSection.isValid(name)) {
          if (checkIfOwner && !forProject.ref(name).test(RefPermission.WRITE_CONFIG)) {
            continue;
          }

          RefPattern.validate(name);
          boolean differs = replace(config, toDelete, section);
          if (differs
              && groupMutationsDisallowed(projectName)
              && isGroupMutation(section.getName())) {
            throw new ConfigInvalidException(
                String.format(
                    "permissions on %s are managed by gerrit and cannot be modified",
                    RefNames.REFS_GROUPS));
          }
        }
      }

      for (String name : toDelete) {
        if (groupMutationsDisallowed(projectName) && isGroupMutation(name)) {
          throw new ConfigInvalidException(
              String.format(
                  "permissions on %s are managed by gerrit and cannot be modified",
                  RefNames.REFS_GROUPS));
        }

        if (AccessSection.GLOBAL_CAPABILITIES.equals(name)) {
          if (!checkIfOwner || canWriteConfig()) {
            config.remove(config.getAccessSection(name));
          }

        } else if (!checkIfOwner || forProject.ref(name).test(RefPermission.WRITE_CONFIG)) {
          config.remove(config.getAccessSection(name));
        }
      }

      boolean parentProjectUpdate = false;
      if (!config.getProject().getNameKey().equals(allProjects)
          && !config.getProject().getParent(allProjects).equals(parentProjectName)) {
        parentProjectUpdate = true;
        try {
          setParent
              .get()
              .validateParentUpdate(
                  projectName,
                  user.asIdentifiedUser(),
                  MoreObjects.firstNonNull(parentProjectName, allProjects).get(),
                  checkIfOwner);
        } catch (AuthException e) {
          throw new UpdateParentFailedException(
              "You are not allowed to change the parent project since you are "
                  + "not an administrator. You may save the modifications for review "
                  + "so that an administrator can approve them.",
              e);
        } catch (ResourceConflictException | UnprocessableEntityException e) {
          throw new UpdateParentFailedException(e.getMessage(), e);
        }
        config.getProject().setParentName(parentProjectName);
      }

      if (message != null && !message.isEmpty()) {
        if (!message.endsWith("\n")) {
          message += "\n";
        }
        md.setMessage(message);
      } else {
        md.setMessage("Modify access rules\n");
      }

      return updateProjectConfig(config, md, parentProjectUpdate);
    } catch (RepositoryNotFoundException notFound) {
      throw new NoSuchProjectException(projectName);
    }
  }

  protected abstract T updateProjectConfig(
      ProjectConfig config, MetaDataUpdate md, boolean parentProjectUpdate)
      throws IOException, NoSuchProjectException, ConfigInvalidException, OrmException,
          PermissionDeniedException, PermissionBackendException;

  /** @return true if the access section differed from the existing one and had to be replaced. */
  private boolean replace(ProjectConfig config, Set<String> toDelete, AccessSection section)
      throws NoSuchGroupException {
    for (Permission permission : section.getPermissions()) {
      for (PermissionRule rule : permission.getRules()) {
        lookupGroup(rule);
      }
    }
    boolean differs = !section.equals(config.getAccessSection(section.getName()));
    config.replace(section);
    toDelete.remove(section.getName());
    return differs;
  }

  private static Set<String> scanSectionNames(ProjectConfig config) {
    Set<String> names = new HashSet<>();
    for (AccessSection section : config.getAccessSections()) {
      names.add(section.getName());
    }
    return names;
  }

  private void lookupGroup(PermissionRule rule) throws NoSuchGroupException {
    GroupReference ref = rule.getGroup();
    if (ref.getUUID() == null) {
      final GroupReference group = GroupBackends.findBestSuggestion(groupBackend, ref.getName());
      if (group == null) {
        throw new NoSuchGroupException(ref.getName());
      }
      ref.setUUID(group.getUUID());
    }
  }

  /** Provide a local cache for {@code ProjectPermission.WRITE_CONFIG} capability. */
  private boolean canWriteConfig() throws PermissionBackendException {
    checkNotNull(user);

    if (canWriteConfig != null) {
      return canWriteConfig;
    }
    try {
      permissionBackend.user(user).project(projectName).check(ProjectPermission.WRITE_CONFIG);
      canWriteConfig = true;
    } catch (AuthException e) {
      canWriteConfig = false;
    }
    return canWriteConfig;
  }

  private boolean groupMutationsDisallowed(Project.NameKey projectName) {
    return (projectName.get().equals(allProjects.get())
        || projectName.get().equals(allUsers.get()));
  }

  private boolean isGroupMutation(String sectionName) {
    return sectionName.startsWith(RefNames.REFS_GROUPS);
  }
}
