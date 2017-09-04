package com.google.gerrit.server.project;

import com.google.common.base.MoreObjects;
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
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.GroupsCollection;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SetAccessUtil {

  private final PermissionBackend permissionBackend;
  private final GroupsCollection groupsCollection;
  private final Provider<IdentifiedUser> identifiedUser;
  private final AllProjectsName allProjects;
  private final Provider<SetParent> setParent;

  @Inject
  private SetAccessUtil(
      PermissionBackend permissionBackend,
      GroupsCollection groupsCollection,
      Provider<IdentifiedUser> identifiedUser,
      AllProjectsName allProjects,
      Provider<SetParent> setParent) {
    this.permissionBackend = permissionBackend;
    this.groupsCollection = groupsCollection;
    this.identifiedUser = identifiedUser;
    this.allProjects = allProjects;
    this.setParent = setParent;
  }

  List<AccessSection> getAccessSections(Map<String, AccessSectionInfo> sectionInfos)
      throws UnprocessableEntityException {
    if (sectionInfos == null) {
      return Collections.emptyList();
    }

    List<AccessSection> sections = new ArrayList<>(sectionInfos.size());
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

  /**
   * Checks that the removals and addition are logically valid, but doesn't check current user's
   * permission.
   */
  void validateChanges(
      ProjectControl projectControl,
      ProjectConfig config,
      List<AccessSection> removals,
      List<AccessSection> additions)
      throws BadRequestException, AuthException, InvalidNameException, PermissionBackendException {
    // Perform permission checks.
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
      }

      // Check all permissions for soundness
      for (Permission p : section.getPermissions()) {
        if (isGlobalCapabilities && !GlobalCapability.isCapability(p.getName())) {
          throw new BadRequestException(
              "Cannot add non-global capability " + p.getName() + " to global capabilities");
        }
      }
    }
  }

  void cleanupPermissions(
      ProjectConfig config, List<AccessSection> removals, List<AccessSection> additions) {
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
  }

  void setParentName(
      ProjectControl projectControl,
      ProjectConfig config,
      Project.NameKey newParentProjectName,
      boolean checkPermissions)
      throws ResourceConflictException, AuthException, PermissionBackendException {

    if (newParentProjectName != null
        && !config.getProject().getNameKey().equals(allProjects)
        && !config.getProject().getParent(allProjects).equals(newParentProjectName)) {
      try {
        setParent
            .get()
            .validateParentUpdate(
                projectControl.getProject().getNameKey(),
                projectControl.getUser().asIdentifiedUser(),
                MoreObjects.firstNonNull(newParentProjectName, allProjects).get(),
                checkPermissions);
      } catch (UnprocessableEntityException e) {
        throw new ResourceConflictException(e.getMessage(), e);
      }
      config.getProject().setParentName(newParentProjectName);
    }
  }
}
