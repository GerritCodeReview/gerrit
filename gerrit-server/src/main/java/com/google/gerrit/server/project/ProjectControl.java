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

package com.google.gerrit.server.project;

import static com.google.gerrit.common.CollectionsUtil.*;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.ReplicationUser;
import com.google.gerrit.server.config.GitReceivePackGroups;
import com.google.gerrit.server.config.GitUploadPackGroups;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import java.util.HashSet;
import java.util.Set;

/** Access control management for a user accessing a project's data. */
public class ProjectControl {
  public static final int VISIBLE = 1 << 0;
  public static final int OWNER = 1 << 1;

  public static class GenericFactory {
    private final ProjectCache projectCache;

    @Inject
    GenericFactory(final ProjectCache pc) {
      projectCache = pc;
    }

    public ProjectControl controlFor(Project.NameKey nameKey, CurrentUser user)
        throws NoSuchProjectException {
      final ProjectState p = projectCache.get(nameKey);
      if (p == null) {
        throw new NoSuchProjectException(nameKey);
      }
      return p.controlFor(user);
    }
  }

  public static class Factory {
    private final ProjectCache projectCache;
    private final Provider<CurrentUser> user;

    @Inject
    Factory(final ProjectCache pc, final Provider<CurrentUser> cu) {
      projectCache = pc;
      user = cu;
    }

    public ProjectControl controlFor(final Project.NameKey nameKey)
        throws NoSuchProjectException {
      final ProjectState p = projectCache.get(nameKey);
      if (p == null) {
        throw new NoSuchProjectException(nameKey);
      }
      return p.controlFor(user.get());
    }

    public ProjectControl validateFor(final Project.NameKey nameKey)
        throws NoSuchProjectException {
      return validateFor(nameKey, VISIBLE);
    }

    public ProjectControl ownerFor(final Project.NameKey nameKey)
        throws NoSuchProjectException {
      return validateFor(nameKey, OWNER);
    }

    public ProjectControl validateFor(final Project.NameKey nameKey,
        final int need) throws NoSuchProjectException {
      final ProjectControl c = controlFor(nameKey);
      if ((need & VISIBLE) == VISIBLE && c.isVisible()) {
        return c;
      }
      if ((need & OWNER) == OWNER && c.isOwner()) {
        return c;
      }
      throw new NoSuchProjectException(nameKey);
    }
  }

  interface AssistedFactory {
    ProjectControl create(CurrentUser who, ProjectState ps);
  }

  private final Set<AccountGroup.Id> uploadGroups;
  private final Set<AccountGroup.Id> receiveGroups;

  private final RefControl.Factory refControlFactory;
  private final CurrentUser user;
  private final ProjectState state;

  @Inject
  ProjectControl(@GitUploadPackGroups Set<AccountGroup.Id> uploadGroups,
      @GitReceivePackGroups Set<AccountGroup.Id> receiveGroups,
      final RefControl.Factory refControlFactory,
      @Assisted CurrentUser who, @Assisted ProjectState ps) {
    this.uploadGroups = uploadGroups;
    this.receiveGroups = receiveGroups;
    this.refControlFactory = refControlFactory;
    user = who;
    state = ps;
  }

  public ProjectControl forAnonymousUser() {
    return state.controlForAnonymousUser();
  }

  public ProjectControl forUser(final CurrentUser who) {
    return state.controlFor(who);
  }

  public ChangeControl controlFor(final Change change) {
    return new ChangeControl(controlForRef(change.getDest()), change);
  }

  public RefControl controlForRef(Branch.NameKey ref) {
    return controlForRef(ref.get());
  }

  public RefControl controlForRef(String refName) {
    return refControlFactory.create(this, refName);
  }

  public CurrentUser getCurrentUser() {
    return user;
  }

  public ProjectState getProjectState() {
    return state;
  }

  public Project getProject() {
    return getProjectState().getProject();
  }

  /** Can this user see this project exists? */
  public boolean isVisible() {
    return visibleForReplication()
        || canPerformOnAnyRef(ApprovalCategory.READ, (short) 1);
  }

  public boolean canAddRefs() {
    return (canPerformOnAnyRef(ApprovalCategory.PUSH_HEAD, ApprovalCategory.PUSH_HEAD_CREATE)
        || canPerformOnAnyRef(ApprovalCategory.BRANCH_ADMIN, ApprovalCategory.BRANCH_ADMIN_ADD)
        || isOwnerAnyRef());
  }

  /** Can this user see all the refs in this projects? */
  public boolean allRefsAreVisible() {
    return visibleForReplication()
        || canPerformOnAllRefs(ApprovalCategory.READ, (short) 1);
  }

  /** Is this project completely visible for replication? */
  boolean visibleForReplication() {
    return getCurrentUser() instanceof ReplicationUser
        && ((ReplicationUser) getCurrentUser()).isEverythingVisible();
  }

  /** Is this user a project owner? Ownership does not imply {@link #isVisible()} */
  public boolean isOwner() {
    return controlForRef(RefRight.ALL).isOwner()
        || getCurrentUser().isAdministrator();
  }

  /** Does this user have ownership on at least one reference name? */
  public boolean isOwnerAnyRef() {
    return canPerformOnAnyRef(ApprovalCategory.OWN, (short) 1)
        || getCurrentUser().isAdministrator();
  }

  /** @return true if the user can upload to at least one reference */
  public boolean canPushToAtLeastOneRef() {
    return canPerformOnAnyRef(ApprovalCategory.READ, (short) 2)
        || canPerformOnAnyRef(ApprovalCategory.PUSH_HEAD, (short) 1)
        || canPerformOnAnyRef(ApprovalCategory.PUSH_TAG, (short) 1);
  }

  // TODO (anatol.pomazau): Try to merge this method with similar RefRightsForPattern#canPerform
  private boolean canPerformOnAnyRef(ApprovalCategory.Id actionId,
      short requireValue) {
    final Set<AccountGroup.Id> groups = user.getEffectiveGroups();

    for (final RefRight pr : state.getAllRights(actionId, true)) {
      if (groups.contains(pr.getAccountGroupId())
          && pr.getMaxValue() >= requireValue) {
        return true;
      }
    }

    return false;
  }

  private boolean canPerformOnAllRefs(ApprovalCategory.Id actionId,
      short requireValue) {
    boolean canPerform = false;
    final Set<String> patterns = allRefPatterns(actionId);
    if (patterns.contains(RefRight.ALL)) {
      // Only possible if granted on the pattern that
      // matches every possible reference.  Check all
      // patterns also have the permission.
      //
      for (final String pattern : patterns) {
        if (controlForRef(pattern).canPerform(actionId, requireValue)) {
          canPerform = true;
        } else {
          return false;
        }
      }
    }
    return canPerform;
  }

  private Set<String> allRefPatterns(ApprovalCategory.Id actionId) {
    final Set<String> all = new HashSet<String>();
    for (final RefRight pr : state.getAllRights(actionId, true)) {
      all.add(pr.getRefPattern());
    }
    return all;
  }

  public boolean canRunUploadPack() {
    return isAnyIncludedIn(uploadGroups, user.getEffectiveGroups());
  }

  public boolean canRunReceivePack() {
    return isAnyIncludedIn(receiveGroups, user.getEffectiveGroups());
  }
}
