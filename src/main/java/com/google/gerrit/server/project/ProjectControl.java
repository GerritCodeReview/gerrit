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

import static com.google.gerrit.client.reviewdb.ApprovalCategory.PUSH_HEAD;
import static com.google.gerrit.client.reviewdb.ApprovalCategory.PUSH_HEAD_CREATE;
import static com.google.gerrit.client.reviewdb.ApprovalCategory.PUSH_HEAD_REPLACE;
import static com.google.gerrit.client.reviewdb.ApprovalCategory.PUSH_TAG;

import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.server.CurrentUser;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.spearce.jgit.lib.Constants;

import java.util.Set;

/** Access control management for a user accessing a project's data. */
public class ProjectControl {
  public static final int VISIBLE = 1 << 0;
  public static final int OWNER = 1 << 1;

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

  private final CurrentUser user;
  private final ProjectState state;

  ProjectControl(final CurrentUser who, final ProjectState ps) {
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
    return new ChangeControl(this, change);
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
    return canPerform(ApprovalCategory.READ, (short) 1);
  }

  /** Is this user a project owner? Ownership does not imply {@link #isVisible()} */
  public boolean isOwner() {
    return canPerform(ApprovalCategory.OWN, (short) 1)
        || getCurrentUser().isAdministrator();
  }

  /** Can this user create the given ref through this access path? */
  public boolean canCreateRef(final String refname) {
    switch (user.getAccessPath()) {
      case WEB:
        if (isOwner()) {
          return true;
        }
        if (isHead(refname) && canPerform(PUSH_HEAD, PUSH_HEAD_CREATE)) {
          return true;
        }
        return false;

      case SSH:
        if (isHead(refname) && canPerform(PUSH_HEAD, PUSH_HEAD_CREATE)) {
          return true;
        }
        if (isTag(refname) && canPerform(PUSH_TAG, (short) 1)) {
          return true;
        }
        return false;

      default:
        return false;
    }
  }

  /** Can this user delete the given ref through this access path? */
  public boolean canDeleteRef(final String refname) {
    switch (user.getAccessPath()) {
      case WEB:
        if (isOwner()) {
          return true;
        }
        if (isHead(refname) && canPerform(PUSH_HEAD, PUSH_HEAD_REPLACE)) {
          return true;
        }
        return false;

      case SSH:
        if (isHead(refname) && canPerform(PUSH_HEAD, PUSH_HEAD_REPLACE)) {
          return true;
        }
        return false;

      default:
        return false;
    }
  }

  /**
   * Can this user perform the action in this project, at the level asked?
   * <p>
   * This method checks the project rights against the user's effective groups.
   * If no right for the given category was granted to any of the user's
   * effective groups, then the rights from the wildcard project are checked.
   *
   * @param actionId unique action id.
   * @param requireValue minimum value the application needs to perform this
   *        action.
   * @return true if the action can be performed; false if the user lacks the
   *         necessary permission.
   */
  public boolean canPerform(final ApprovalCategory.Id actionId,
      final short requireValue) {
    final Set<AccountGroup.Id> groups = user.getEffectiveGroups();
    int val = Integer.MIN_VALUE;
    for (final ProjectRight pr : state.getLocalRights()) {
      if (actionId.equals(pr.getApprovalCategoryId())
          && groups.contains(pr.getAccountGroupId())) {
        if (val < 0 && pr.getMaxValue() > 0) {
          // If one of the user's groups had denied them access, but
          // this group grants them access, prefer the grant over
          // the denial. We have to break the tie somehow and we
          // prefer being "more open" to being "more closed".
          //
          val = pr.getMaxValue();
        } else {
          // Otherwise we use the largest value we can get.
          //
          val = Math.max(pr.getMaxValue(), val);
        }
      }
    }
    if (val == Integer.MIN_VALUE && actionId.canInheritFromWildProject()) {
      for (final ProjectRight pr : state.projectCache.getWildcardRights()) {
        if (actionId.equals(pr.getApprovalCategoryId())
            && groups.contains(pr.getAccountGroupId())) {
          val = Math.max(pr.getMaxValue(), val);
        }
      }
    }

    return val >= requireValue;
  }

  private static boolean isHead(final String refname) {
    return refname.startsWith(Constants.R_HEADS);
  }

  private static boolean isTag(final String refname) {
    return refname.startsWith(Constants.R_TAGS);
  }
}
