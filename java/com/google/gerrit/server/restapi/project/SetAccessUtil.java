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

package com.google.gerrit.server.restapi.project;

import com.google.common.collect.Iterables;
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
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.RefPattern;
import com.google.gerrit.server.restapi.config.ListCapabilities;
import com.google.gerrit.server.restapi.group.GroupsCollection;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public class SetAccessUtil {
  private final GroupsCollection groupsCollection;
  private final AllProjectsName allProjects;
  private final Provider<SetParent> setParent;
  private final ListCapabilities listCapabilities;

  @Inject
  private SetAccessUtil(
      GroupsCollection groupsCollection,
      AllProjectsName allProjects,
      Provider<SetParent> setParent,
      ListCapabilities listCapabilities) {
    this.groupsCollection = groupsCollection;
    this.allProjects = allProjects;
    this.setParent = setParent;
    this.listCapabilities = listCapabilities;
  }

  List<AccessSection> getAccessSections(Map<String, AccessSectionInfo> sectionInfos)
      throws UnprocessableEntityException {
    if (sectionInfos == null) {
      return Collections.emptyList();
    }

    List<AccessSection> sections = new ArrayList<>(sectionInfos.size());
    for (Map.Entry<String, AccessSectionInfo> entry : sectionInfos.entrySet()) {
      if (entry.getValue().permissions == null) {
        continue;
      }

      AccessSection accessSection = new AccessSection(entry.getKey());
      for (Map.Entry<String, PermissionInfo> permissionEntry :
          entry.getValue().permissions.entrySet()) {
        if (permissionEntry.getValue().rules == null) {
          continue;
        }

        Permission p = new Permission(permissionEntry.getKey());
        if (permissionEntry.getValue().exclusive != null) {
          p.setExclusiveGroup(permissionEntry.getValue().exclusive);
        }

        for (Map.Entry<String, PermissionRuleInfo> permissionRuleInfoEntry :
            permissionEntry.getValue().rules.entrySet()) {
          GroupDescription.Basic group = groupsCollection.parseId(permissionRuleInfoEntry.getKey());
          if (group == null) {
            throw new UnprocessableEntityException(
                permissionRuleInfoEntry.getKey() + " is not a valid group ID");
          }

          PermissionRuleInfo pri = permissionRuleInfoEntry.getValue();
          PermissionRule r = new PermissionRule(GroupReference.forGroup(group));
          if (pri != null) {
            if (pri.max != null) {
              r.setMax(pri.max);
            }
            if (pri.min != null) {
              r.setMin(pri.min);
            }
            if (pri.action != null) {
              r.setAction(GetAccess.ACTION_TYPE.inverse().get(pri.action));
            }
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

  /**
   * Checks that the removals and additions are logically valid, but doesn't check current user's
   * permission.
   */
  void validateChanges(
      ProjectConfig config, List<AccessSection> removals, List<AccessSection> additions)
      throws BadRequestException, InvalidNameException {
    // Perform permission checks
    for (AccessSection section : Iterables.concat(additions, removals)) {
      boolean isGlobalCapabilities = AccessSection.GLOBAL_CAPABILITIES.equals(section.getName());
      if (isGlobalCapabilities) {
        if (!allProjects.equals(config.getName())) {
          throw new BadRequestException(
              "Cannot edit global capabilities for projects other than " + allProjects.get());
        }
      }
    }

    // Perform addition checks
    for (AccessSection section : additions) {
      String name = section.getName();
      boolean isGlobalCapabilities = AccessSection.GLOBAL_CAPABILITIES.equals(name);

      if (!isGlobalCapabilities) {
        if (!AccessSection.isValid(name)) {
          throw new BadRequestException("invalid section name");
        }
        RefPattern.validate(name);
      } else {
        // Check all permissions for soundness
        for (Permission p : section.getPermissions()) {
          if (!isCapability(p.getName())) {
            throw new BadRequestException(
                "Cannot add non-global capability " + p.getName() + " to global capabilities");
          }
        }
      }
    }
  }

  void applyChanges(
      ProjectConfig config, List<AccessSection> removals, List<AccessSection> additions) {
    // Apply removals
    for (AccessSection section : removals) {
      if (section.getPermissions().isEmpty()) {
        // Remove entire section
        config.remove(config.getAccessSection(section.getName()));
        continue;
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
  }

  /**
   * Updates the parent project in the given config.
   *
   * @param identifiedUser the user
   * @param config the config to modify
   * @param projectName the project for which to change access.
   * @param newParentProjectName the new parent to set; passing null will make this a nop
   * @param checkAdmin if set, verify that user has administrateServer permission
   */
  public void setParentName(
      IdentifiedUser identifiedUser,
      ProjectConfig config,
      Project.NameKey projectName,
      Project.NameKey newParentProjectName,
      boolean checkAdmin)
      throws ResourceConflictException, AuthException, PermissionBackendException,
          BadRequestException {
    if (newParentProjectName != null
        && !config.getProject().getNameKey().equals(allProjects)
        && !config.getProject().getParent(allProjects).equals(newParentProjectName)) {
      try {
        setParent
            .get()
            .validateParentUpdate(
                projectName, identifiedUser, newParentProjectName.get(), checkAdmin);
      } catch (UnprocessableEntityException e) {
        throw new ResourceConflictException(e.getMessage(), e);
      }
      config.getProject().setParentName(newParentProjectName);
    }
  }

  private boolean isCapability(String name) {
    if (GlobalCapability.isGlobalCapability(name)) {
      return true;
    }
    Set<String> pluginCapabilities = listCapabilities.collectPluginCapabilities().keySet();
    return pluginCapabilities.contains(name);
  }
}
