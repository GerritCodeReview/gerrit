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

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.data.PermissionRule.Action;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;

import dk.brics.automaton.RegExp;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/** Manages access control for Git references (aka branches, tags). */
public class RefControl {
  private final ProjectControl projectControl;
  private final String refName;

  /** All permissions that apply to this reference. */
  private final PermissionCollection relevant;

  /** Cached set of permissions matching this user. */
  private final Map<String, List<PermissionRule>> effective;

  private Boolean owner;
  private Boolean canForgeAuthor;
  private Boolean canForgeCommitter;

  RefControl(ProjectControl projectControl, String ref,
      PermissionCollection relevant) {
    this.projectControl = projectControl;
    this.refName = ref;
    this.relevant = relevant;
    this.effective = new HashMap<String, List<PermissionRule>>();
  }

  public String getRefName() {
    return refName;
  }

  public ProjectControl getProjectControl() {
    return projectControl;
  }

  public CurrentUser getCurrentUser() {
    return projectControl.getCurrentUser();
  }

  public RefControl forUser(CurrentUser who) {
    ProjectControl newCtl = projectControl.forUser(who);
    if (relevant.isUserSpecific()) {
      return newCtl.controlForRef(getRefName());
    } else {
      return new RefControl(newCtl, getRefName(), relevant);
    }
  }

  /** Is this user a ref owner? */
  public boolean isOwner() {
    if (owner == null) {
      if (canPerform(Permission.OWNER)) {
        owner = true;

      } else {
        owner = projectControl.isOwner();
      }
    }
    return owner;
  }

  /** Can this user see this reference exists? */
  public boolean isVisible() {
    return projectControl.visibleForReplication()
        || canPerform(Permission.READ);
  }

  /**
   * Determines whether the user can upload a change to the ref controlled by
   * this object.
   *
   * @return {@code true} if the user specified can upload a change to the Git
   *         ref
   */
  public boolean canUpload() {
    return projectControl
      .controlForRef("refs/for/" + getRefName())
      .canPerform(Permission.PUSH);
  }

  /** @return true if this user can submit merge patch sets to this ref */
  public boolean canUploadMerges() {
    return projectControl
      .controlForRef("refs/for/" + getRefName())
      .canPerform(Permission.PUSH_MERGE);
  }

  /** @return true if this user can submit patch sets to this ref */
  public boolean canSubmit() {
    if (GitRepositoryManager.REF_CONFIG.equals(refName)) {
      // Always allow project owners to submit configuration changes.
      // Submitting configuration changes modifies the access control
      // rules. Allowing this to be done by a non-project-owner opens
      // a security hole enabling editing of access rules, and thus
      // granting of powers beyond submitting to the configuration.
      return projectControl.isOwner();
    }
    return canPerform(Permission.SUBMIT);
  }

  /** @return true if the user can update the reference as a fast-forward. */
  public boolean canUpdate() {
    if (GitRepositoryManager.REF_CONFIG.equals(refName)
        && !projectControl.isOwner()) {
      // Pushing requires being at least project owner, in addition to push.
      // Pushing configuration changes modifies the access control
      // rules. Allowing this to be done by a non-project-owner opens
      // a security hole enabling editing of access rules, and thus
      // granting of powers beyond pushing to the configuration.
      return false;
    }
    return canPerform(Permission.PUSH);
  }

  /** @return true if the user can rewind (force push) the reference. */
  public boolean canForceUpdate() {
    return canPushWithForce() || canDelete();
  }

  private boolean canPushWithForce() {
    if (GitRepositoryManager.REF_CONFIG.equals(refName)
        && !projectControl.isOwner()) {
      // Pushing requires being at least project owner, in addition to push.
      // Pushing configuration changes modifies the access control
      // rules. Allowing this to be done by a non-project-owner opens
      // a security hole enabling editing of access rules, and thus
      // granting of powers beyond pushing to the configuration.
      return false;
    }
    boolean result = false;
    for (PermissionRule rule : access(Permission.PUSH)) {
      if (rule.isBlock()) {
        return false;
      }
      if (rule.getForce()) {
        result = true;
      }
    }
    return result;
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
      return owner || canPerform(Permission.CREATE);

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
        if (!valid && !owner && !canForgeCommitter()) {
          return false;
        }
      }

      // If the tag has a PGP signature, allow a lower level of permission
      // than if it doesn't have a PGP signature.
      //
      if (tag.getFullMessage().contains("-----BEGIN PGP SIGNATURE-----\n")) {
        return owner || canPerform(Permission.PUSH_TAG);
      } else {
        return owner || canPerform(Permission.PUSH_TAG);
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
    if (GitRepositoryManager.REF_CONFIG.equals(refName)) {
      // Never allow removal of the refs/meta/config branch.
      // Deleting the branch would destroy all Gerrit specific
      // metadata about the project, including its access rules.
      // If a project is to be removed from Gerrit, its repository
      // should be removed first.
      return false;
    }

    switch (getCurrentUser().getAccessPath()) {
      case WEB_UI:
        return isOwner() || canPushWithForce();

      case GIT:
        return canPushWithForce();

      default:
        return false;
    }
  }

  /** @return true if this user can forge the author line in a commit. */
  public boolean canForgeAuthor() {
    if (canForgeAuthor == null) {
      canForgeAuthor = canPerform(Permission.FORGE_AUTHOR);
    }
    return canForgeAuthor;
  }

  /** @return true if this user can forge the committer line in a commit. */
  public boolean canForgeCommitter() {
    if (canForgeCommitter == null) {
      canForgeCommitter = canPerform(Permission.FORGE_COMMITTER);
    }
    return canForgeCommitter;
  }

  /** @return true if this user can forge the server on the committer line. */
  public boolean canForgeGerritServerIdentity() {
    return canPerform(Permission.FORGE_SERVER);
  }

  /** All value ranges of any allowed label permission. */
  public List<PermissionRange> getLabelRanges() {
    List<PermissionRange> r = new ArrayList<PermissionRange>();
    for (Map.Entry<String, List<PermissionRule>> e : relevant.getDeclaredPermissions()) {
      if (Permission.isLabel(e.getKey())) {
        int min = 0;
        int max = 0;
        for (PermissionRule rule : e.getValue()) {
          if (projectControl.match(rule)) {
            min = Math.min(min, rule.getMin());
            max = Math.max(max, rule.getMax());
          }
        }
        if (min != 0 || max != 0) {
          r.add(new PermissionRange(e.getKey(), min, max));
        }
      }
    }
    return r;
  }

  /** The range of permitted values associated with a label permission. */
  public PermissionRange getRange(String permission) {
    if (Permission.isLabel(permission)) {
      return toRange(permission, access(permission));
    }
    return null;
  }

  private static PermissionRange toRange(String permissionName,
      List<PermissionRule> ruleList) {
    int min = 0;
    int max = 0;
    for (PermissionRule rule : ruleList) {
      min = Math.min(min, rule.getMin());
      max = Math.max(max, rule.getMax());
    }
    return new PermissionRange(permissionName, min, max);
  }

  /** True if the user has this permission. Works only for non labels. */
  boolean canPerform(String permissionName) {
    List<PermissionRule> access = access(permissionName);
    for (PermissionRule rule : access) {
      if (rule.isBlock() && !rule.getForce()) {
        return false;
      }
    }
    return !access(permissionName).isEmpty();
  }

  /** Rules for the given permission, or the empty list. */
  private List<PermissionRule> access(String permissionName) {
    List<PermissionRule> rules = effective.get(permissionName);
    if (rules != null) {
      return rules;
    }

    rules = relevant.getPermission(permissionName);

    if (rules.isEmpty()) {
      effective.put(permissionName, rules);
      return rules;
    }

    if (rules.size() == 1) {
      if (!projectControl.match(rules.get(0))) {
        rules = Collections.emptyList();
      }
      effective.put(permissionName, rules);
      return rules;
    }

    List<PermissionRule> mine = new ArrayList<PermissionRule>(rules.size());
    for (PermissionRule rule : rules) {
      if (projectControl.match(rule)) {
        mine.add(rule);
      }
    }

    if (mine.isEmpty()) {
      mine = Collections.emptyList();
    }
    effective.put(permissionName, mine);
    return mine;
  }

  static boolean isRE(String refPattern) {
    return refPattern.startsWith(AccessSection.REGEX_PREFIX);
  }

  public static String shortestExample(String pattern) {
    if (isRE(pattern)) {
      return toRegExp(pattern).toAutomaton().getShortestExample(true);
    } else if (pattern.endsWith("/*")) {
      return pattern.substring(0, pattern.length() - 1) + '1';
    } else {
      return pattern;
    }
  }

  static RegExp toRegExp(String refPattern) {
    if (isRE(refPattern)) {
      refPattern = refPattern.substring(1);
    }
    return new RegExp(refPattern, RegExp.NONE);
  }
}
