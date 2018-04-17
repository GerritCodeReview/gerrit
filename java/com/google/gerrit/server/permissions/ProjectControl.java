// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.permissions;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.config.GitReceivePackGroups;
import com.google.gerrit.server.config.GitUploadPackGroups;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackend.ForChange;
import com.google.gerrit.server.permissions.PermissionBackend.ForProject;
import com.google.gerrit.server.permissions.PermissionBackend.ForRef;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.project.ProjectAccessor;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.SectionMatcher;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/** Access control management for a user accessing a project's data. */
class ProjectControl {
  interface Factory {
    ProjectControl create(CurrentUser who, ProjectState ps);
  }

  private final Set<AccountGroup.UUID> uploadGroups;
  private final Set<AccountGroup.UUID> receiveGroups;
  private final PermissionBackend permissionBackend;
  private final CurrentUser user;
  private final ProjectAccessor accessor;
  private final ChangeControl.Factory changeControlFactory;
  private final PermissionCollection.Factory permissionFilter;
  private final DefaultRefFilter.Factory refFilterFactory;

  private List<SectionMatcher> allSections;
  private Map<String, RefControl> refControls;
  private Boolean declaredOwner;

  @Inject
  ProjectControl(
      @GitUploadPackGroups Set<AccountGroup.UUID> uploadGroups,
      @GitReceivePackGroups Set<AccountGroup.UUID> receiveGroups,
      PermissionCollection.Factory permissionFilter,
      ChangeControl.Factory changeControlFactory,
      PermissionBackend permissionBackend,
      DefaultRefFilter.Factory refFilterFactory,
      @Assisted CurrentUser who,
      @Assisted ProjectAccessor accessor) {
    this.changeControlFactory = changeControlFactory;
    this.uploadGroups = uploadGroups;
    this.receiveGroups = receiveGroups;
    this.permissionFilter = permissionFilter;
    this.permissionBackend = permissionBackend;
    this.refFilterFactory = refFilterFactory;
    user = who;
    this.accessor = accessor;
  }

  ProjectControl forUser(CurrentUser who) {
    ProjectControl r =
        new ProjectControl(
            uploadGroups,
            receiveGroups,
            permissionFilter,
            changeControlFactory,
            permissionBackend,
            refFilterFactory,
            who,
            accessor);
    // Not per-user, and reusing saves lookup time.
    r.allSections = allSections;
    return r;
  }

  ForProject asForProject() {
    return new ForProjectImpl();
  }

  ChangeControl controlFor(ReviewDb db, Change change) throws OrmException {
    return changeControlFactory.create(
        controlForRef(change.getDest()), db, change.getProject(), change.getId());
  }

  ChangeControl controlFor(ChangeNotes notes) {
    return changeControlFactory.create(controlForRef(notes.getChange().getDest()), notes);
  }

  RefControl controlForRef(Branch.NameKey ref) {
    return controlForRef(ref.get());
  }

  public RefControl controlForRef(String refName) {
    if (refControls == null) {
      refControls = new HashMap<>();
    }
    RefControl ctl = refControls.get(refName);
    if (ctl == null) {
      PermissionCollection relevant = permissionFilter.filter(access(), refName, user);
      ctl = new RefControl(this, refName, relevant);
      refControls.put(refName, ctl);
    }
    return ctl;
  }

  CurrentUser getUser() {
    return user;
  }

  ProjectAccessor getProjectAccessor() {
    return accessor;
  }

  // TODO(dborowitz): Remove this method.
  ProjectState getProjectState() {
    return accessor.getProjectState();
  }

  Project getProject() {
    return getProjectState().getProject();
  }

  /** Is this user a project owner? */
  boolean isOwner() {
    return (isDeclaredOwner() && controlForRef("refs/*").canPerform(Permission.OWNER)) || isAdmin();
  }

  /**
   * @return {@code Capable.OK} if the user can upload to at least one reference. Does not check
   *     Contributor Agreements.
   */
  boolean canPushToAtLeastOneRef() {
    return canPerformOnAnyRef(Permission.PUSH)
        || canPerformOnAnyRef(Permission.CREATE_TAG)
        || isOwner();
  }

  boolean isAdmin() {
    try {
      permissionBackend.user(user).check(GlobalPermission.ADMINISTRATE_SERVER);
      return true;
    } catch (AuthException | PermissionBackendException e) {
      return false;
    }
  }

  boolean match(PermissionRule rule, boolean isChangeOwner) {
    return match(rule.getGroup().getUUID(), isChangeOwner);
  }

  boolean allRefsAreVisible(Set<String> ignore) {
    return user.isInternalUser() || canPerformOnAllRefs(Permission.READ, ignore);
  }

  /** Can the user run upload pack? */
  private boolean canRunUploadPack() {
    for (AccountGroup.UUID group : uploadGroups) {
      if (match(group)) {
        return true;
      }
    }
    return false;
  }

  /** Can the user run receive pack? */
  private boolean canRunReceivePack() {
    for (AccountGroup.UUID group : receiveGroups) {
      if (match(group)) {
        return true;
      }
    }
    return false;
  }

  private boolean canAddRefs() {
    return (canPerformOnAnyRef(Permission.CREATE) || isAdmin());
  }

  private boolean canCreateChanges() {
    for (SectionMatcher matcher : access()) {
      AccessSection section = matcher.getSection();
      if (section.getName().startsWith("refs/for/")) {
        Permission permission = section.getPermission(Permission.PUSH);
        if (permission != null && controlForRef(section.getName()).canPerform(Permission.PUSH)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isDeclaredOwner() {
    if (declaredOwner == null) {
      GroupMembership effectiveGroups = user.getEffectiveGroups();
      declaredOwner = effectiveGroups.containsAnyOf(accessor.getAllOwners());
    }
    return declaredOwner;
  }

  private boolean canPerformOnAnyRef(String permissionName) {
    for (SectionMatcher matcher : access()) {
      AccessSection section = matcher.getSection();
      Permission permission = section.getPermission(permissionName);
      if (permission == null) {
        continue;
      }

      for (PermissionRule rule : permission.getRules()) {
        if (rule.isBlock() || rule.isDeny() || !match(rule)) {
          continue;
        }

        // Being in a group that was granted this permission is only an
        // approximation.  There might be overrides and doNotInherit
        // that would render this to be false.
        //
        if (controlForRef(section.getName()).canPerform(permissionName)) {
          return true;
        }
        break;
      }
    }

    return false;
  }

  private boolean canPerformOnAllRefs(String permission, Set<String> ignore) {
    boolean canPerform = false;
    Set<String> patterns = allRefPatterns(permission);
    if (patterns.contains(AccessSection.ALL)) {
      // Only possible if granted on the pattern that
      // matches every possible reference.  Check all
      // patterns also have the permission.
      //
      for (String pattern : patterns) {
        if (controlForRef(pattern).canPerform(permission)) {
          canPerform = true;
        } else if (ignore.contains(pattern)) {
          continue;
        } else {
          return false;
        }
      }
    }
    return canPerform;
  }

  private Set<String> allRefPatterns(String permissionName) {
    Set<String> all = new HashSet<>();
    for (SectionMatcher matcher : access()) {
      AccessSection section = matcher.getSection();
      Permission permission = section.getPermission(permissionName);
      if (permission != null) {
        all.add(section.getName());
      }
    }
    return all;
  }

  private List<SectionMatcher> access() {
    if (allSections == null) {
      allSections = getProjectAccessor().getAllSections();
    }
    return allSections;
  }

  private boolean match(PermissionRule rule) {
    return match(rule.getGroup().getUUID());
  }

  private boolean match(AccountGroup.UUID uuid) {
    return match(uuid, false);
  }

  private boolean match(AccountGroup.UUID uuid, boolean isChangeOwner) {
    if (SystemGroupBackend.PROJECT_OWNERS.equals(uuid)) {
      return isDeclaredOwner();
    } else if (SystemGroupBackend.CHANGE_OWNER.equals(uuid)) {
      return isChangeOwner;
    } else {
      return user.getEffectiveGroups().contains(uuid);
    }
  }

  private class ForProjectImpl extends ForProject {
    private DefaultRefFilter refFilter;
    private String resourcePath;

    @Override
    public CurrentUser user() {
      return getUser();
    }

    @Override
    public ForProject user(CurrentUser user) {
      return forUser(user).asForProject().database(db);
    }

    @Override
    public String resourcePath() {
      if (resourcePath == null) {
        resourcePath = "/projects/" + getProjectState().getName();
      }
      return resourcePath;
    }

    @Override
    public ForRef ref(String ref) {
      return controlForRef(ref).asForRef().database(db);
    }

    @Override
    public ForChange change(ChangeData cd) {
      try {
        checkProject(cd.change());
        return super.change(cd);
      } catch (OrmException e) {
        return FailedPermissionBackend.change("unavailable", e);
      }
    }

    @Override
    public ForChange change(ChangeNotes notes) {
      checkProject(notes.getChange());
      return super.change(notes);
    }

    private void checkProject(Change change) {
      Project.NameKey project = getProject().getNameKey();
      checkArgument(
          project.equals(change.getProject()),
          "expected change in project %s, not %s",
          project,
          change.getProject());
    }

    @Override
    public void check(ProjectPermission perm) throws AuthException, PermissionBackendException {
      if (!can(perm)) {
        throw new AuthException(perm.describeForException() + " not permitted");
      }
    }

    @Override
    public Set<ProjectPermission> test(Collection<ProjectPermission> permSet)
        throws PermissionBackendException {
      EnumSet<ProjectPermission> ok = EnumSet.noneOf(ProjectPermission.class);
      for (ProjectPermission perm : permSet) {
        if (can(perm)) {
          ok.add(perm);
        }
      }
      return ok;
    }

    @Override
    public Map<String, Ref> filter(Map<String, Ref> refs, Repository repo, RefFilterOptions opts)
        throws PermissionBackendException {
      if (refFilter == null) {
        refFilter = refFilterFactory.create(ProjectControl.this);
      }
      return refFilter.filter(refs, repo, opts);
    }

    private boolean can(ProjectPermission perm) throws PermissionBackendException {
      switch (perm) {
        case ACCESS:
          return user.isInternalUser() || isOwner() || canPerformOnAnyRef(Permission.READ);

        case READ:
          return allRefsAreVisible(Collections.emptySet());

        case CREATE_REF:
          return canAddRefs();
        case CREATE_CHANGE:
          return canCreateChanges();

        case RUN_RECEIVE_PACK:
          return canRunReceivePack();
        case RUN_UPLOAD_PACK:
          return canRunUploadPack();

        case PUSH_AT_LEAST_ONE_REF:
          return canPushToAtLeastOneRef();

        case READ_CONFIG:
          return controlForRef(RefNames.REFS_CONFIG).isVisible();

        case BAN_COMMIT:
        case READ_REFLOG:
        case WRITE_CONFIG:
          return isOwner();
      }
      throw new PermissionBackendException(perm + " unsupported");
    }
  }
}
