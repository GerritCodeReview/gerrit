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

package com.google.gerrit.server.project;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.errors.InvalidNameException;
import com.google.gerrit.extensions.api.access.AccessSectionInfo;
import com.google.gerrit.extensions.api.access.PermissionInfo;
import com.google.gerrit.extensions.api.access.PermissionRuleInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.GroupsCollection;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class SetAccess implements RestModifyView<ProjectResource, ProjectAccessInput> {
  protected final GroupBackend groupBackend;
  private final GroupsCollection groupsCollection;
  private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
  private final AllProjectsName allProjects;
  private final Provider<SetParent> setParent;
  private final GetAccess getAccess;
  private final ProjectCache projectCache;
  private final Provider<IdentifiedUser> identifiedUser;

  @Inject
  private SetAccess(
      GroupBackend groupBackend,
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      AllProjectsName allProjects,
      Provider<SetParent> setParent,
      GroupsCollection groupsCollection,
      ProjectCache projectCache,
      GetAccess getAccess,
      Provider<IdentifiedUser> identifiedUser) {
    this.groupBackend = groupBackend;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.allProjects = allProjects;
    this.setParent = setParent;
    this.groupsCollection = groupsCollection;
    this.getAccess = getAccess;
    this.projectCache = projectCache;
    this.identifiedUser = identifiedUser;
  }

  @Override
  public ProjectAccessInfo apply(ProjectResource rsrc, ProjectAccessInput input)
      throws ResourceNotFoundException, ResourceConflictException, IOException, AuthException,
          BadRequestException, UnprocessableEntityException {
    List<AccessSection> removals = getAccessSections(input.remove);
    List<AccessSection> additions = getAccessSections(input.add);
    MetaDataUpdate.User metaDataUpdateUser = metaDataUpdateFactory.get();

    ProjectControl projectControl = rsrc.getControl();
    ProjectConfig config;

    Project.NameKey newParentProjectName =
        input.parent == null ? null : new Project.NameKey(input.parent);

    try (MetaDataUpdate md = metaDataUpdateUser.create(rsrc.getNameKey())) {
      config = ProjectConfig.read(md);

      // Perform removal checks
      for (AccessSection section : removals) {
        boolean isGlobalCapabilities = AccessSection.GLOBAL_CAPABILITIES.equals(section.getName());

        if (isGlobalCapabilities) {
          checkGlobalCapabilityPermissions(config.getName());
        } else if (!projectControl.controlForRef(section.getName()).isOwner()) {
          throw new AuthException(
              "You are not allowed to edit permissions" + "for ref: " + section.getName());
        }
      }
      // Perform addition checks
      for (AccessSection section : additions) {
        String name = section.getName();
        boolean isGlobalCapabilities = AccessSection.GLOBAL_CAPABILITIES.equals(name);

        if (isGlobalCapabilities) {
          checkGlobalCapabilityPermissions(config.getName());
        } else {
          if (!AccessSection.isValid(name)) {
            throw new BadRequestException("invalid section name");
          }
          if (!projectControl.controlForRef(name).isOwner()) {
            throw new AuthException("You are not allowed to edit permissions" + "for ref: " + name);
          }
          RefPattern.validate(name);
        }

        // Check all permissions for soundness
        for (Permission p : section.getPermissions()) {
          if (isGlobalCapabilities && !GlobalCapability.isCapability(p.getName())) {
            throw new BadRequestException(
                "Cannot add non-global capability " + p.getName() + " to global capabilities");
          }
        }
      }

      // Apply removals
      for (AccessSection section : removals) {
        if (section.getPermissions().isEmpty()) {
          // Remove entire section
          config.remove(config.getAccessSection(section.getName()));
        }
        // Remove specific permissions
        for (Permission p : section.getPermissions()) {
          if (p.getRules().isEmpty()) {
            config.remove(config.getAccessSection(section.getName()), p);
          } else {
            for (PermissionRule r : p.getRules()) {
              config.remove(config.getAccessSection(section.getName()), p, r);
            }
          }
        }
      }

      // Apply additions
      for (AccessSection section : additions) {
        AccessSection currentAccessSection = config.getAccessSection(section.getName());

        if (currentAccessSection == null) {
          // Add AccessSection
          config.replace(section);
        } else {
          for (Permission p : section.getPermissions()) {
            Permission currentPermission = currentAccessSection.getPermission(p.getName());
            if (currentPermission == null) {
              // Add Permission
              currentAccessSection.addPermission(p);
            } else {
              for (PermissionRule r : p.getRules()) {
                // AddPermissionRule
                currentPermission.add(r);
              }
            }
          }
        }
      }

      if (newParentProjectName != null
          && !config.getProject().getNameKey().equals(allProjects)
          && !config.getProject().getParent(allProjects).equals(newParentProjectName)) {
        try {
          setParent
              .get()
              .validateParentUpdate(
                  projectControl,
                  MoreObjects.firstNonNull(newParentProjectName, allProjects).get(),
                  true);
        } catch (UnprocessableEntityException e) {
          throw new ResourceConflictException(e.getMessage(), e);
        }
        config.getProject().setParentName(newParentProjectName);
      }

      if (!Strings.isNullOrEmpty(input.message)) {
        if (!input.message.endsWith("\n")) {
          input.message += "\n";
        }
        md.setMessage(input.message);
      } else {
        md.setMessage("Modify access rules\n");
      }

      config.commit(md);
      projectCache.evict(config.getProject());
    } catch (InvalidNameException e) {
      throw new BadRequestException(e.toString());
    } catch (ConfigInvalidException e) {
      throw new ResourceConflictException(rsrc.getName());
    }

    return getAccess.apply(rsrc.getNameKey());
  }

  private List<AccessSection> getAccessSections(Map<String, AccessSectionInfo> sectionInfos)
      throws UnprocessableEntityException {
    List<AccessSection> sections = new LinkedList<>();
    if (sectionInfos == null) {
      return sections;
    }

    for (Map.Entry<String, AccessSectionInfo> entry : sectionInfos.entrySet()) {
      AccessSection accessSection = new AccessSection(entry.getKey());

      if (entry.getValue().permissions == null) {
        continue;
      }

      for (Map.Entry<String, PermissionInfo> permissionEntry :
          entry.getValue().permissions.entrySet()) {
        Permission p = new Permission(permissionEntry.getKey());
        if (permissionEntry.getValue().exclusive != null) {
          p.setExclusiveGroup(permissionEntry.getValue().exclusive);
        }

        if (permissionEntry.getValue().rules == null) {
          continue;
        }
        for (Map.Entry<String, PermissionRuleInfo> permissionRuleInfoEntry :
            permissionEntry.getValue().rules.entrySet()) {
          PermissionRuleInfo pri = permissionRuleInfoEntry.getValue();

          GroupDescription.Basic group = groupsCollection.parseId(permissionRuleInfoEntry.getKey());
          if (group == null) {
            throw new UnprocessableEntityException(
                permissionRuleInfoEntry.getKey() + " is not a valid group ID");
          }
          PermissionRule r = new PermissionRule(GroupReference.forGroup(group));
          if (pri != null) {
            if (pri.max != null) {
              r.setMax(pri.max);
            }
            if (pri.min != null) {
              r.setMin(pri.min);
            }
            r.setAction(GetAccess.ACTION_TYPE.inverse().get(pri.action));
            if (pri.force != null) {
              r.setForce(pri.force);
            }
          }
          p.add(r);
        }
        accessSection.getPermissions().add(p);
      }
      sections.add(accessSection);
    }
    return sections;
  }

  private void checkGlobalCapabilityPermissions(Project.NameKey projectName)
      throws BadRequestException, AuthException {

    if (!allProjects.equals(projectName)) {
      throw new BadRequestException(
          "Cannot edit global capabilities " + "for projects other than " + allProjects.get());
    }

    if (!identifiedUser.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException(
          "Editing global capabilities " + "requires " + GlobalCapability.ADMINISTRATE_SERVER);
    }
  }
}
