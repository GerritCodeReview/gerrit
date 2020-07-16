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

package com.google.gerrit.server.restapi.project;

import static com.google.gerrit.server.permissions.GlobalPermission.ADMINISTRATE_SERVER;
import static com.google.gerrit.server.permissions.ProjectPermission.CREATE_REF;
import static com.google.gerrit.server.permissions.ProjectPermission.CREATE_TAG_REF;
import static com.google.gerrit.server.permissions.RefPermission.CREATE_CHANGE;
import static com.google.gerrit.server.permissions.RefPermission.READ;
import static com.google.gerrit.server.permissions.RefPermission.WRITE_CONFIG;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.stream.Collectors.toMap;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.access.AccessSectionInfo;
import com.google.gerrit.extensions.api.access.PermissionInfo;
import com.google.gerrit.extensions.api.access.PermissionRuleInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.WebLinks;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectJson;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

@Singleton
public class GetAccess implements RestReadView<ProjectResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final ImmutableBiMap<PermissionRule.Action, PermissionRuleInfo.Action> ACTION_TYPE =
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

  private final Provider<CurrentUser> user;
  private final PermissionBackend permissionBackend;
  private final AllProjectsName allProjectsName;
  private final ProjectJson projectJson;
  private final ProjectCache projectCache;
  private final Provider<MetaDataUpdate.Server> metaDataUpdateFactory;
  private final GroupBackend groupBackend;
  private final WebLinks webLinks;
  private final ProjectConfig.Factory projectConfigFactory;

  @Inject
  public GetAccess(
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      AllProjectsName allProjectsName,
      ProjectCache projectCache,
      Provider<MetaDataUpdate.Server> metaDataUpdateFactory,
      ProjectJson projectJson,
      GroupBackend groupBackend,
      WebLinks webLinks,
      ProjectConfig.Factory projectConfigFactory) {
    this.user = self;
    this.permissionBackend = permissionBackend;
    this.allProjectsName = allProjectsName;
    this.projectJson = projectJson;
    this.projectCache = projectCache;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.groupBackend = groupBackend;
    this.webLinks = webLinks;
    this.projectConfigFactory = projectConfigFactory;
  }

  public ProjectAccessInfo apply(Project.NameKey nameKey) throws Exception {
    ProjectState state =
        projectCache.get(nameKey).orElseThrow(() -> new ResourceNotFoundException(nameKey.get()));
    return apply(new ProjectResource(state, user.get())).value();
  }

  @Override
  public Response<ProjectAccessInfo> apply(ProjectResource rsrc)
      throws ResourceNotFoundException, ResourceConflictException, IOException,
          PermissionBackendException {
    // Load the current configuration from the repository, ensuring it's the most
    // recent version available. If it differs from what was in the project
    // state, force a cache flush now.

    Project.NameKey projectName = rsrc.getNameKey();
    ProjectAccessInfo info = new ProjectAccessInfo();
    ProjectState projectState =
        projectCache.get(projectName).orElseThrow(illegalState(projectName));
    PermissionBackend.ForProject perm = permissionBackend.currentUser().project(projectName);

    ProjectConfig config;
    try (MetaDataUpdate md = metaDataUpdateFactory.get().create(projectName)) {
      config = projectConfigFactory.read(md);
      info.configWebLinks = new ArrayList<>();

      // config may have a null revision if the repo doesn't have its own refs/meta/config.
      if (config.getRevision() != null) {
        info.configWebLinks.addAll(
            webLinks.getFileHistoryLinks(
                projectName.get(), config.getRevision().getName(), ProjectConfig.PROJECT_CONFIG));
      }

      if (config.updateGroupNames(groupBackend)) {
        md.setMessage("Update group names\n");
        config.commit(md);
        projectCache.evict(config.getProject());
        projectState = projectCache.get(projectName).orElseThrow(illegalState(projectName));
        perm = permissionBackend.currentUser().project(projectName);
      } else if (config.getRevision() != null
          && !config.getRevision().equals(projectState.getConfig().getRevision().orElse(null))) {
        projectCache.evict(config.getProject());
        projectState = projectCache.get(projectName).orElseThrow(illegalState(projectName));
        perm = permissionBackend.currentUser().project(projectName);
      }
    } catch (ConfigInvalidException e) {
      throw new ResourceConflictException(e.getMessage());
    } catch (RepositoryNotFoundException e) {
      throw new ResourceNotFoundException(rsrc.getName(), e);
    }

    // The following implementation must match the ProjectAccessFactory JSON RPC endpoint.

    info.local = new HashMap<>();
    info.ownerOf = new HashSet<>();
    Map<AccountGroup.UUID, GroupInfo> groups = new HashMap<>();
    boolean canReadConfig = check(perm, RefNames.REFS_CONFIG, READ);
    boolean canWriteConfig = check(perm, ProjectPermission.WRITE_CONFIG);

    // Check if the project state permits read only when the user is not allowed to write the config
    // (=owner). This is so that the owner can still read (and in the next step write) the project's
    // config to set the project state to any state that is not HIDDEN.
    if (!canWriteConfig) {
      projectState.checkStatePermitsRead();
    }

    for (AccessSection section : config.getAccessSections()) {
      String name = section.getName();
      if (AccessSection.GLOBAL_CAPABILITIES.equals(name)) {
        if (canWriteConfig) {
          info.local.put(name, createAccessSection(groups, section));
          info.ownerOf.add(name);

        } else if (canReadConfig) {
          info.local.put(section.getName(), createAccessSection(groups, section));
        }

      } else if (AccessSection.isValidRefSectionName(name)) {
        if (check(perm, name, WRITE_CONFIG)) {
          info.local.put(name, createAccessSection(groups, section));
          info.ownerOf.add(name);

        } else if (canReadConfig) {
          info.local.put(name, createAccessSection(groups, section));

        } else if (check(perm, name, READ)) {
          // Filter the section to only add rules describing groups that
          // are visible to the current-user. This includes any group the
          // user is a member of, as well as groups they own or that
          // are visible to all users.

          AccessSection.Builder dst = null;
          for (Permission srcPerm : section.getPermissions()) {
            Permission.Builder dstPerm = null;

            for (PermissionRule srcRule : srcPerm.getRules()) {
              AccountGroup.UUID groupId = srcRule.getGroup().getUUID();
              if (groupId == null) {
                continue;
              }

              loadGroup(groups, groupId);
              if (dstPerm == null) {
                if (dst == null) {
                  dst = AccessSection.builder(name);
                  info.local.put(name, createAccessSection(groups, dst.build()));
                }
                dstPerm = dst.upsertPermission(srcPerm.getName());
              }
              dstPerm.add(srcRule.toBuilder());
            }
          }
        }
      }
    }

    if (info.ownerOf.isEmpty()) {
      try {
        permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
        // Special case: If the section list is empty, this project has no current
        // access control information. Fall back to site administrators.
        info.ownerOf.add(AccessSection.ALL);
      } catch (AuthException e) {
        // Do nothing.
      }
    }

    if (config.getRevision() != null) {
      info.revision = config.getRevision().name();
    }

    ProjectState parent = Iterables.getFirst(projectState.parents(), null);
    if (parent != null) {
      info.inheritsFrom = projectJson.format(parent.getProject());
    }

    if (projectName.equals(allProjectsName)
        && permissionBackend.currentUser().testOrFalse(ADMINISTRATE_SERVER)) {
      info.ownerOf.add(AccessSection.GLOBAL_CAPABILITIES);
    }

    info.isOwner = toBoolean(canWriteConfig);
    info.canUpload =
        toBoolean(
            projectState.statePermitsWrite()
                && (canWriteConfig
                    || (canReadConfig
                        && perm.ref(RefNames.REFS_CONFIG).testOrFalse(CREATE_CHANGE))));
    info.canAdd = toBoolean(perm.testOrFalse(CREATE_REF));
    info.canAddTags = toBoolean(perm.testOrFalse(CREATE_TAG_REF));
    info.configVisible = canReadConfig || canWriteConfig;

    info.groups =
        groups.entrySet().stream()
            .filter(e -> e.getValue() != null)
            .collect(toMap(e -> e.getKey().get(), Map.Entry::getValue));

    return Response.ok(info);
  }

  private void loadGroup(Map<AccountGroup.UUID, GroupInfo> groups, AccountGroup.UUID id) {
    if (!groups.containsKey(id)) {
      GroupDescription.Basic basic = groupBackend.get(id);
      GroupInfo group;
      if (basic != null) {
        group = new GroupInfo();
        // The UI only needs name + URL, so don't populate other fields to avoid leaking data
        // about groups invisible to the user.
        group.name = basic.getName();
        group.url = basic.getUrl();
      } else {
        logger.atWarning().log("no such group: %s", id);
        group = null;
      }
      groups.put(id, group);
    }
  }

  private static boolean check(PermissionBackend.ForProject ctx, String ref, RefPermission perm)
      throws PermissionBackendException {
    try {
      ctx.ref(ref).check(perm);
      return true;
    } catch (AuthException denied) {
      return false;
    }
  }

  private static boolean check(PermissionBackend.ForProject ctx, ProjectPermission perm)
      throws PermissionBackendException {
    try {
      ctx.check(perm);
      return true;
    } catch (AuthException denied) {
      return false;
    }
  }

  private AccessSectionInfo createAccessSection(
      Map<AccountGroup.UUID, GroupInfo> groups, AccessSection section) {
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
          loadGroup(groups, group);
        }
      }
      accessSectionInfo.permissions.put(p.getName(), pInfo);
    }
    return accessSectionInfo;
  }

  private static Boolean toBoolean(boolean value) {
    return value ? true : null;
  }
}
