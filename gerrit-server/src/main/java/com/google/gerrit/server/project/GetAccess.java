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

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.data.RefConfigSection;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.api.access.AccessSectionInfo;
import com.google.gerrit.extensions.api.access.PermissionInfo;
import com.google.gerrit.extensions.api.access.PermissionRuleInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

@Singleton
public class GetAccess implements RestReadView<ProjectResource> {

  public static final BiMap<PermissionRule.Action, PermissionRuleInfo.Action> ACTION_TYPE =
      ImmutableBiMap.of(
          PermissionRule.Action.ALLOW,
          PermissionRuleInfo.Action.ALLOW,
          PermissionRule.Action.BATCH,
          PermissionRuleInfo.Action.BATCH,
          PermissionRule.Action.BLOCK,
          PermissionRuleInfo.Action.BLOCK,
          PermissionRule.Action.DENY,
          PermissionRuleInfo.Action.DENY,
          PermissionRule.Action.INTERACTIVE,
          PermissionRuleInfo.Action.INTERACTIVE);

  private final Provider<CurrentUser> self;
  private final GroupControl.Factory groupControlFactory;
  private final AllProjectsName allProjectsName;
  private final ProjectJson projectJson;
  private final ProjectCache projectCache;
  private final MetaDataUpdate.Server metaDataUpdateFactory;
  private final ProjectControl.GenericFactory projectControlFactory;
  private final GroupBackend groupBackend;

  @Inject
  public GetAccess(
      Provider<CurrentUser> self,
      GroupControl.Factory groupControlFactory,
      AllProjectsName allProjectsName,
      ProjectCache projectCache,
      MetaDataUpdate.Server metaDataUpdateFactory,
      ProjectJson projectJson,
      ProjectControl.GenericFactory projectControlFactory,
      GroupBackend groupBackend) {
    this.self = self;
    this.groupControlFactory = groupControlFactory;
    this.allProjectsName = allProjectsName;
    this.projectJson = projectJson;
    this.projectCache = projectCache;
    this.projectControlFactory = projectControlFactory;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.groupBackend = groupBackend;
  }

  public ProjectAccessInfo apply(Project.NameKey nameKey)
      throws ResourceNotFoundException, ResourceConflictException, IOException {
    try {
      return this.apply(new ProjectResource(projectControlFactory.controlFor(nameKey, self.get())));
    } catch (NoSuchProjectException e) {
      throw new ResourceNotFoundException(nameKey.get());
    }
  }

  @Override
  public ProjectAccessInfo apply(ProjectResource rsrc)
      throws ResourceNotFoundException, ResourceConflictException, IOException {
    // Load the current configuration from the repository, ensuring it's the most
    // recent version available. If it differs from what was in the project
    // state, force a cache flush now.
    //
    Project.NameKey projectName = rsrc.getNameKey();
    ProjectAccessInfo info = new ProjectAccessInfo();
    ProjectConfig config;
    ProjectControl pc = open(projectName);
    RefControl metaConfigControl = pc.controlForRef(RefNames.REFS_CONFIG);
    try (MetaDataUpdate md = metaDataUpdateFactory.create(projectName)) {
      config = ProjectConfig.read(md);

      if (config.updateGroupNames(groupBackend)) {
        md.setMessage("Update group names\n");
        config.commit(md);
        projectCache.evict(config.getProject());
        pc = open(projectName);
      } else if (config.getRevision() != null
          && !config.getRevision().equals(pc.getProjectState().getConfig().getRevision())) {
        projectCache.evict(config.getProject());
        pc = open(projectName);
      }
    } catch (ConfigInvalidException e) {
      throw new ResourceConflictException(e.getMessage());
    } catch (RepositoryNotFoundException e) {
      throw new ResourceNotFoundException(rsrc.getName());
    }

    info.local = new HashMap<>();
    info.ownerOf = new HashSet<>();
    Map<AccountGroup.UUID, Boolean> visibleGroups = new HashMap<>();

    for (AccessSection section : config.getAccessSections()) {
      String name = section.getName();
      if (AccessSection.GLOBAL_CAPABILITIES.equals(name)) {
        if (pc.isOwner()) {
          info.local.put(name, createAccessSection(section));
          info.ownerOf.add(name);

        } else if (metaConfigControl.isVisible()) {
          info.local.put(section.getName(), createAccessSection(section));
        }

      } else if (RefConfigSection.isValid(name)) {
        RefControl rc = pc.controlForRef(name);
        if (rc.isOwner()) {
          info.local.put(name, createAccessSection(section));
          info.ownerOf.add(name);

        } else if (metaConfigControl.isVisible()) {
          info.local.put(name, createAccessSection(section));

        } else if (rc.isVisible()) {
          // Filter the section to only add rules describing groups that
          // are visible to the current-user. This includes any group the
          // user is a member of, as well as groups they own or that
          // are visible to all users.

          AccessSection dst = null;
          for (Permission srcPerm : section.getPermissions()) {
            Permission dstPerm = null;

            for (PermissionRule srcRule : srcPerm.getRules()) {
              AccountGroup.UUID group = srcRule.getGroup().getUUID();
              if (group == null) {
                continue;
              }

              Boolean canSeeGroup = visibleGroups.get(group);
              if (canSeeGroup == null) {
                try {
                  canSeeGroup = groupControlFactory.controlFor(group).isVisible();
                } catch (NoSuchGroupException e) {
                  canSeeGroup = Boolean.FALSE;
                }
                visibleGroups.put(group, canSeeGroup);
              }

              if (canSeeGroup) {
                if (dstPerm == null) {
                  if (dst == null) {
                    dst = new AccessSection(name);
                    info.local.put(name, createAccessSection(dst));
                  }
                  dstPerm = dst.getPermission(srcPerm.getName(), true);
                }
                dstPerm.add(srcRule);
              }
            }
          }
        }
      }
    }

    if (info.ownerOf.isEmpty() && pc.isOwnerAnyRef()) {
      // Special case: If the section list is empty, this project has no current
      // access control information. Rely on what ProjectControl determines
      // is ownership, which probably means falling back to site administrators.
      info.ownerOf.add(AccessSection.ALL);
    }

    if (config.getRevision() != null) {
      info.revision = config.getRevision().name();
    }

    ProjectState parent = Iterables.getFirst(pc.getProjectState().parents(), null);
    if (parent != null) {
      info.inheritsFrom = projectJson.format(parent.getProject());
    }

    if (pc.getProject().getNameKey().equals(allProjectsName)) {
      if (pc.isOwner()) {
        info.ownerOf.add(AccessSection.GLOBAL_CAPABILITIES);
      }
    }

    info.isOwner = toBoolean(pc.isOwner());
    info.canUpload =
        toBoolean(pc.isOwner() || (metaConfigControl.isVisible() && metaConfigControl.canUpload()));
    info.canAdd = toBoolean(pc.canAddRefs());
    info.configVisible = pc.isOwner() || metaConfigControl.isVisible();

    return info;
  }

  private AccessSectionInfo createAccessSection(AccessSection section) {
    AccessSectionInfo accessSectionInfo = new AccessSectionInfo();
    accessSectionInfo.permissions = new HashMap<>();
    for (Permission p : section.getPermissions()) {
      PermissionInfo pInfo = new PermissionInfo(p.getLabel(), p.getExclusiveGroup() ? true : null);
      pInfo.rules = new HashMap<>();
      for (PermissionRule r : p.getRules()) {
        PermissionRuleInfo info =
            new PermissionRuleInfo(ACTION_TYPE.get(r.getAction()), r.getForce());
        if (r.hasRange()) {
          info.max = r.getMax();
          info.min = r.getMin();
        }
        AccountGroup.UUID group = r.getGroup().getUUID();
        if (group != null) {
          pInfo.rules.put(group.get(), info);
        }
      }
      accessSectionInfo.permissions.put(p.getName(), pInfo);
    }
    return accessSectionInfo;
  }

  private ProjectControl open(Project.NameKey projectName)
      throws ResourceNotFoundException, IOException {
    try {
      return projectControlFactory.validateFor(
          projectName, ProjectControl.OWNER | ProjectControl.VISIBLE, self.get());
    } catch (NoSuchProjectException e) {
      throw new ResourceNotFoundException(projectName.get());
    }
  }

  private static Boolean toBoolean(boolean value) {
    return value ? true : null;
  }
}
