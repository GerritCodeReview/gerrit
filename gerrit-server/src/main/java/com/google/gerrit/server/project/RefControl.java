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

import static com.google.gerrit.reviewdb.ApprovalCategory.FORGE_AUTHOR;
import static com.google.gerrit.reviewdb.ApprovalCategory.FORGE_COMMITTER;
import static com.google.gerrit.reviewdb.ApprovalCategory.FORGE_IDENTITY;
import static com.google.gerrit.reviewdb.ApprovalCategory.OWN;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_HEAD;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_HEAD_CREATE;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_HEAD_REPLACE;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_HEAD_UPDATE;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_TAG;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_TAG_ANNOTATED;
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
import java.util.Collections;
import java.util.List;
import java.util.Set;


/** Manages access control for Git references (aka branches, tags). */
public class RefControl {
  private final ProjectControl projectControl;
  private final String refName;

  private Boolean canForgeAuthor;
  private Boolean canForgeCommitter;

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

  public RefControl forAnonymousUser() {
    return getProjectControl().forAnonymousUser().controlForRef(getRefName());
  }

  public RefControl forUser(final CurrentUser who) {
    return getProjectControl().forUser(who).controlForRef(getRefName());
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
      case WEB_UI:
        owner = isOwner();
        break;

      default:
        owner = false;
    }

    if (object instanceof RevCommit) {
      return owner || canPerform(PUSH_HEAD, PUSH_HEAD_CREATE);

    } else if (object instanceof RevTag) {
      final RevTag tag = (RevTag) object;
      try {
        rw.parseBody(tag);
      } catch (IOException e) {
        return false;
      }

      // If tagger is present, require it matches the user's email.
      //
      final PersonIdent tagger = tag.getTaggerIdent();
      if (tagger != null) {
        boolean valid;
        if (getCurrentUser() instanceof IdentifiedUser) {
          final IdentifiedUser user = (IdentifiedUser) getCurrentUser();
          final String addr = tagger.getEmailAddress();
          valid = user.getEmailAddresses().contains(addr);
        } else {
          valid = false;
        }
        if (!valid && !owner && !canPerform(FORGE_IDENTITY, FORGE_COMMITTER)) {
          return false;
        }
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
      case WEB_UI:
        return isOwner() || canPerform(PUSH_HEAD, PUSH_HEAD_REPLACE);

      case GIT:
        return canPerform(PUSH_HEAD, PUSH_HEAD_REPLACE);

      default:
        return false;
    }
  }

  /** @return true if this user can forge the author line in a commit. */
  public boolean canForgeAuthor() {
    if (canForgeAuthor == null) {
      canForgeAuthor = canPerform(FORGE_IDENTITY, FORGE_AUTHOR);
    }
    return canForgeAuthor;
  }

  /** @return true if this user can forge the committer line in a commit. */
  public boolean canForgeCommitter() {
    if (canForgeCommitter == null) {
      canForgeCommitter = canPerform(FORGE_IDENTITY, FORGE_COMMITTER);
    }
    return canForgeCommitter;
  }

  private boolean canPerform(ApprovalCategory.Id actionId, short level) {
    final Set<AccountGroup.Id> groups = getCurrentUser().getEffectiveGroups();
    int val = Integer.MIN_VALUE;

    List<RefRight> allRights = new ArrayList<RefRight>();
    allRights.addAll(getLocalRights(actionId));

    if (actionId.canInheritFromWildProject()) {
      allRights.addAll(getInheritedRights(actionId));
    }

    // Sort in descending refPattern length
    Collections.sort(allRights, RefRight.REF_PATTERN_ORDER);

    for (RefRight right : filterMostSpecific(allRights)) {
      if (groups.contains(right.getAccountGroupId())) {
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
    return val >= level;
  }

  public static List<RefRight> filterMostSpecific(List<RefRight> actionRights) {
    // Grab the first set of RefRight which have the same refPattern
    // those are the most specific RefRights we have, and are the
    // we will consider to verify if this action can be performed.
    // We do this so that one can override the ref rights for a specific
    // project on a specific branch
    boolean sameRefPattern = true;
    List<RefRight> mostSpecific = new ArrayList<RefRight>();
    String currentRefPattern = null;
    int i = 0;
    while (sameRefPattern && i < actionRights.size()) {
      if (currentRefPattern == null) {
        currentRefPattern = actionRights.get(i).getRefPattern();
        mostSpecific.add(actionRights.get(i));
        i++;
      } else {
        if (currentRefPattern.equals(actionRights.get(i).getRefPattern())) {
          mostSpecific.add(actionRights.get(i));
          i++;
        } else {
          sameRefPattern = false;
        }
      }
    }
    return mostSpecific;
  }

  private List<RefRight> getLocalRights(ApprovalCategory.Id actionId) {
    return filter(projectControl.getProjectState().getLocalRights(), actionId);
  }

  private List<RefRight> getInheritedRights(ApprovalCategory.Id actionId) {
    return filter(projectControl.getProjectState().getInheritedRights(), actionId);
  }

  public List<RefRight> getAllRights(final ApprovalCategory.Id id) {
    List<RefRight> l = new ArrayList<RefRight>();
    l.addAll(getLocalRights(id));
    l.addAll(getInheritedRights(id));
    Collections.sort(l, RefRight.REF_PATTERN_ORDER);
    return Collections.unmodifiableList(RefControl.filterMostSpecific(l));
  }

  private List<RefRight> filter(Collection<RefRight> all,
      ApprovalCategory.Id actionId) {
    List<RefRight> mine = new ArrayList<RefRight>(all.size());
    for (RefRight right : all) {
      if (matches(getRefName(), right.getRefPattern()) &&
          right.getApprovalCategoryId().equals(actionId)) {
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
