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

package com.google.gerrit.server.access;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.data.RefConfigSection;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.GroupJson;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectJson;
import com.google.gerrit.server.project.ProjectJson.ProjectInfo;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.RefControl;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ListAccess implements RestReadView<TopLevelResource> {

  @Option(name = "--project", aliases = {"-p"}, metaVar = "PROJECT",
      usage = "projects for which the access rights should be returned")
  private List<String> projects = Lists.newArrayList();

  private final Provider<CurrentUser> self;
  private final ProjectControl.GenericFactory projectControlFactory;
  private final ProjectCache projectCache;
  private final ProjectJson projectJson;
  private final MetaDataUpdate.Server metaDataUpdateFactory;
  private final GroupControl.Factory groupControlFactory;
  private final GroupBackend groupBackend;
  private final AllProjectsName allProjectsName;

  @Inject
  public ListAccess(Provider<CurrentUser> self,
      ProjectControl.GenericFactory projectControlFactory,
      ProjectCache projectCache, ProjectJson projectJson,
      MetaDataUpdate.Server metaDataUpdateFactory,
      GroupControl.Factory groupControlFactory, GroupBackend groupBackend,
      GroupJson groupJson, AllProjectsName allProjectsName) {
    this.self = self;
    this.projectControlFactory = projectControlFactory;
    this.projectCache = projectCache;
    this.projectJson = projectJson;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.groupControlFactory = groupControlFactory;
    this.groupBackend = groupBackend;
    this.allProjectsName = allProjectsName;
  }

  @Override
  public Map<String, ProjectAccessInfo> apply(TopLevelResource resource)
      throws ResourceNotFoundException, ResourceConflictException, IOException {
    Map<String, ProjectAccessInfo> access = Maps.newTreeMap();
    for (String p: projects) {
      Project.NameKey projectName = new Project.NameKey(p);
      ProjectControl pc = open(projectName);
      ProjectConfig config;

      try {
        // Load the current configuration from the repository, ensuring it's the most
        // recent version available. If it differs from what was in the project
        // state, force a cache flush now.
        //
        MetaDataUpdate md = metaDataUpdateFactory.create(projectName);
        try {
          config = ProjectConfig.read(md);

          if (config.updateGroupNames(groupBackend)) {
            md.setMessage("Update group names\n");
            config.commit(md);
            projectCache.evict(config.getProject());
            pc = open(projectName);
          } else if (config.getRevision() != null
              && !config.getRevision().equals(
                  pc.getProjectState().getConfig().getRevision())) {
            projectCache.evict(config.getProject());
            pc = open(projectName);
          }
        } catch (ConfigInvalidException e) {
          throw new ResourceConflictException(e.getMessage());
        } finally {
          md.close();
        }
      } catch (RepositoryNotFoundException e) {
        throw new ResourceNotFoundException(p);
      }

      access.put(p, new ProjectAccessInfo(pc, config));
    }
    return access;
  }

  private ProjectControl open(Project.NameKey projectName)
      throws ResourceNotFoundException, IOException {
    try {
      return projectControlFactory.validateFor(projectName,
          ProjectControl.OWNER | ProjectControl.VISIBLE, self.get());
    } catch (NoSuchProjectException e) {
      throw new ResourceNotFoundException(projectName.get());
    }
  }

  public class ProjectAccessInfo {
    public String revision;
    public ProjectInfo inheritsFrom;
    public Map<String, AccessSectionInfo> local;
    public Boolean isOwner;
    public Set<String> ownerOf;
    public Boolean canUpload;
    public Boolean canAdd;
    public Boolean configVisible;

    public ProjectAccessInfo(ProjectControl pc, ProjectConfig config) {
      final RefControl metaConfigControl =
          pc.controlForRef(GitRepositoryManager.REF_CONFIG);
      local = Maps.newHashMap();
      ownerOf = Sets.newHashSet();
      Map<AccountGroup.UUID, Boolean> visibleGroups =
          new HashMap<AccountGroup.UUID, Boolean>();

      for (AccessSection section : config.getAccessSections()) {
        String name = section.getName();
        if (AccessSection.GLOBAL_CAPABILITIES.equals(name)) {
          if (pc.isOwner()) {
            local.put(name, new AccessSectionInfo(section));
            ownerOf.add(name);

          } else if (metaConfigControl.isVisible()) {
            local.put(section.getName(), new AccessSectionInfo(section));
          }

        } else if (RefConfigSection.isValid(name)) {
          RefControl rc = pc.controlForRef(name);
          if (rc.isOwner()) {
            local.put(name, new AccessSectionInfo(section));
            ownerOf.add(name);

          } else if (metaConfigControl.isVisible()) {
            local.put(name, new AccessSectionInfo(section));

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
                      local.put(name, new AccessSectionInfo(dst));
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

      if (ownerOf.isEmpty() && pc.isOwnerAnyRef()) {
        // Special case: If the section list is empty, this project has no current
        // access control information. Rely on what ProjectControl determines
        // is ownership, which probably means falling back to site administrators.
        ownerOf.add(AccessSection.ALL);
      }


      if (config.getRevision() != null) {
        revision = config.getRevision().name();
      }

      ProjectState parent =
          Iterables.getFirst(pc.getProjectState().parents(), null);
      if (parent != null) {
        inheritsFrom = projectJson.format(parent.getProject());
      }

      if (pc.getProject().getNameKey().equals(allProjectsName)) {
        if (pc.isOwner()) {
          ownerOf.add(AccessSection.GLOBAL_CAPABILITIES);
        }
      }

      isOwner = toBoolean(pc.isOwner());
      canUpload = toBoolean(pc.isOwner()
          || (metaConfigControl.isVisible() && metaConfigControl.canUpload()));
      canAdd = toBoolean(pc.canAddRefs());
      configVisible = pc.isOwner() || metaConfigControl.isVisible();
    }
  }

  public class AccessSectionInfo {
    public Map<String, PermissionInfo> permissions;

    public AccessSectionInfo(AccessSection section) {
      permissions = Maps.newHashMap();
      for (Permission p : section.getPermissions()) {
        permissions.put(p.getName(), new PermissionInfo(p));
      }
    }
  }

  public class PermissionInfo {
    public String label;
    public Boolean exclusive;
    public Map<String, PermissionRuleInfo> rules;

    public PermissionInfo(Permission permission) {
      label = permission.getLabel();
      exclusive = toBoolean(permission.getExclusiveGroup());
      rules = Maps.newHashMap();
      for (PermissionRule r : permission.getRules()) {
        rules.put(r.getGroup().getUUID().get(), new PermissionRuleInfo(r));
      }
    }
  }

  public class PermissionRuleInfo {
    public PermissionRule.Action action;
    public Boolean force;
    public Integer min;
    public Integer max;


    public PermissionRuleInfo(PermissionRule rule) {
      action = rule.getAction();
      force = toBoolean(rule.getForce());
      if (hasRange(rule)) {
        min = rule.getMin();
        max = rule.getMax();
      }
    }

    private boolean hasRange(PermissionRule rule) {
      return (!(rule.getMin() == null || rule.getMin() == 0))
          || (!(rule.getMax() == null || rule.getMax() == 0));
    }
  }

  private static Boolean toBoolean(boolean value) {
    return value ? true : null;
  }
}
