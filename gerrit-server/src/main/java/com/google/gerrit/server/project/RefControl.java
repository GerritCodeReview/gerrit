// Copyright (C) 2010 The Android Open Source Project
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

import static com.google.gerrit.reviewdb.ApprovalCategory.OWN;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_HEAD;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_HEAD_CREATE;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_HEAD_REPLACE;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_HEAD_UPDATE;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_TAG;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_TAG_ANNOTATED;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_TAG_ANY;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_TAG_SIGNED;
import static com.google.gerrit.reviewdb.ApprovalCategory.READ;

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;


/** Manages access control for Git references (aka branches, tags). */
public class RefControl {
  private final ProjectControl projectControl;
  private final String refName;

  RefControl(final ProjectControl projectControl, final String refName) {
    this.projectControl = projectControl;
    this.refName = refName;
  }

  public String getRefName() {
    return refName;
  }

  public ProjectControl getProjectControl() {
    return projectControl;
  }

  public CurrentUser getCurrentUser() {
    return getProjectControl().getCurrentUser();
  }

  /** Is this user a ref owner? */
  public boolean isOwner() {
    return canPerform(OWN, (short) 1) || getProjectControl().isOwner();
  }

  /** Can this user see this reference exists? */
  public boolean isVisible() {
    return canPerform(READ, (short) 1);
  }

  /**
   * Determines whether the user can upload a change to the ref controlled by
   * this object.
   *
   * @return {@code true} if the user specified can upload a change to the Git
   *         ref
   */
  public boolean canUpload() {
    return canPerform(READ, (short) 2);
  }

  /** @return true if the user can update the reference as a fast-forward. */
  public boolean canUpdate() {
    return canPerform(PUSH_HEAD, PUSH_HEAD_UPDATE);
  }

  /** @return true if the user can rewind (force push) the reference. */
  public boolean canForceUpdate() {
    return canPerform(PUSH_HEAD, PUSH_HEAD_REPLACE) || canDelete();
  }

  /**
   * Determines whether the user can create a new Git ref.
   *
   * @param rw revision pool {@code object} was parsed in.
   * @param object the object the user will start the reference with.
   * @return {@code true} if the user specified can create a new Git ref
   */
  public boolean canCreate(RevWalk rw, RevObject object) {
    boolean owner;
    switch (getCurrentUser().getAccessPath()) {
      case WEB:
        owner = isOwner();
        break;

      default:
        owner = false;
    }

    if (object instanceof RevCommit) {
      return owner || canPerform(PUSH_HEAD, PUSH_HEAD_CREATE);

    } else if (object instanceof RevTag) {
      try {
        rw.parseBody(object);
      } catch (IOException e) {
        return false;
      }

      final RevTag tag = (RevTag) object;

      // Require the tagger to be present and match the current user's
      // email address, unless PUSH_ANY_TAG was granted.
      //
      final PersonIdent tagger = tag.getTaggerIdent();
      if (tagger == null || !(getCurrentUser() instanceof IdentifiedUser)) {
        return owner || canPerform(PUSH_TAG, PUSH_TAG_ANY);
      }
      final IdentifiedUser user = (IdentifiedUser) getCurrentUser();
      if (!user.getEmailAddresses().contains(tagger.getEmailAddress())) {
        return owner || canPerform(PUSH_TAG, PUSH_TAG_ANY);
      }

      // If the tag has a PGP signature, allow a lower level of permission
      // than if it doesn't have a PGP signature.
      //
      if (tag.getFullMessage().contains("-----BEGIN PGP SIGNATURE-----\n")) {
        return owner || canPerform(PUSH_TAG, PUSH_TAG_SIGNED);
      } else {
        return owner || canPerform(PUSH_TAG, PUSH_TAG_ANNOTATED);
      }

    } else {
      return false;
    }
  }

  /**
   * Determines whether the user can delete the Git ref controlled by this
   * object.
   *
   * @return {@code true} if the user specified can delete a Git ref.
   */
  public boolean canDelete() {
    switch (getCurrentUser().getAccessPath()) {
      case WEB:
        return isOwner() || canPerform(PUSH_HEAD, PUSH_HEAD_REPLACE);

      case SSH:
        return canPerform(PUSH_HEAD, PUSH_HEAD_REPLACE);

      default:
        return false;
    }
  }

  private boolean canPerform(ApprovalCategory.Id actionId, short level) {
    final Set<AccountGroup.Id> groups = getCurrentUser().getEffectiveGroups();
    int val = Integer.MIN_VALUE;
    for (final RefRight right : getLocalRights()) {
      if (right.getApprovalCategoryId().equals(actionId)
          && groups.contains(right.getAccountGroupId())) {
        if (val < 0 && right.getMaxValue() > 0) {
          // If one of the user's groups had denied them access, but
          // this group grants them access, prefer the grant over
          // the denial. We have to break the tie somehow and we
          // prefer being "more open" to being "more closed".
          //
          val = right.getMaxValue();
        } else {
          // Otherwise we use the largest value we can get.
          //
          val = Math.max(right.getMaxValue(), val);
        }
      }
    }

    if (val == Integer.MIN_VALUE && actionId.canInheritFromWildProject()) {
      for (final RefRight pr : getInheritedRights()) {
        if (actionId.equals(pr.getApprovalCategoryId())
            && groups.contains(pr.getAccountGroupId())) {
          val = Math.max(pr.getMaxValue(), val);
        }
      }
    }

    return val >= level;
  }

  private Collection<RefRight> getLocalRights() {
    return filter(projectControl.getProjectState().getLocalRights());
  }

  private Collection<RefRight> getInheritedRights() {
    return filter(projectControl.getProjectState().getInheritedRights());
  }

  private Collection<RefRight> filter(Collection<RefRight> all) {
    List<RefRight> mine = new ArrayList<RefRight>(all.size());
    for (RefRight right : all) {
      if (matches(getRefName(), right.getRefPattern())) {
        mine.add(right);
      }
    }
    return mine;
  }

  public static boolean matches(String refName, String refPattern) {
    if (refPattern.endsWith("/*")) {
      String prefix = refPattern.substring(0, refPattern.length() - 1);
      return refName.startsWith(prefix);

    } else {
      return refName.equals(refPattern);
    }
  }
}
