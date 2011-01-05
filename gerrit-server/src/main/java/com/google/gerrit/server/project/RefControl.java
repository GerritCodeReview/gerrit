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

import com.google.gerrit.common.CollectionsUtil;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.ParamertizedString;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import dk.brics.automaton.RegExp;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;


/** Manages access control for Git references (aka branches, tags). */
public class RefControl {
  public interface Factory {
    RefControl create(ProjectControl projectControl, String ref);
  }

  private final ProjectControl projectControl;
  private final String refName;

  private Map<String, List<PermissionRule>> permissions;

  private Boolean owner;
  private Boolean canForgeAuthor;
  private Boolean canForgeCommitter;

  @Inject
  protected RefControl(@Assisted final ProjectControl projectControl,
      @Assisted String ref) {
    if (isRE(ref)) {
      ref = shortestExample(ref);

    } else if (ref.endsWith("/*")) {
      ref = ref.substring(0, ref.length() - 1);

    }

    this.projectControl = projectControl;
    this.refName = ref;
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
    if (owner == null) {
      if (canPerform(Permission.OWNER)) {
        owner = true;

      } else if (getRefName().equals(
          AccessSection.ALL.substring(0, AccessSection.ALL.length() - 1))) {
        // We have to prevent infinite recursion here, the project control
        // calls us to find out if there is ownership of all references in
        // order to determine project level ownership.
        //
        owner = getCurrentUser().isAdministrator();

      } else {
        owner = getProjectControl().isOwner();
      }
    }
    return owner;
  }

  /** Can this user see this reference exists? */
  public boolean isVisible() {
    return getProjectControl().visibleForReplication()
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
    return getProjectControl()
        .controlForRef("refs/for/" + getRefName())
        .canPerform(Permission.PUSH);
  }

  /** @return true if this user can submit merge patch sets to this ref */
  public boolean canUploadMerges() {
    return getProjectControl()
      .controlForRef("refs/for/" + getRefName())
      .canPerform(Permission.PUSH_MERGE);
  }

  /** @return true if this user can submit patch sets to this ref */
  public boolean canSubmit() {
    return canPerform(Permission.SUBMIT);
  }

  /** @return true if the user can update the reference as a fast-forward. */
  public boolean canUpdate() {
    return canPerform(Permission.PUSH);
  }

  /** @return true if the user can rewind (force push) the reference. */
  public boolean canForceUpdate() {
    return canPushWithForce() || canDelete();
  }

  private boolean canPushWithForce() {
    for (PermissionRule rule : access(Permission.PUSH)) {
      if (rule.getForce()) {
        return true;
      }
    }
    return false;
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
    for (Map.Entry<String, List<PermissionRule>> e : permissions().entrySet()) {
      if (Permission.isLabel(e.getKey())) {
        r.add(toRange(e.getKey(), e.getValue()));
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

  private static PermissionRange toRange(String permissionName, List<PermissionRule> ruleList) {
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
    return !access(permissionName).isEmpty();
  }

  /** Rules for the given permission, or the empty list. */
  private List<PermissionRule> access(String permissionName) {
    List<PermissionRule> r = permissions().get(permissionName);
    return r != null ? r : Collections.<PermissionRule> emptyList();
  }

  /** All rules that pertain to this user, on this reference. */
  private Map<String, List<PermissionRule>> permissions() {
    if (permissions == null) {
      List<AccessSection> sections = new ArrayList<AccessSection>();
      for (AccessSection section : projectControl.access()) {
        if (appliesToRef(section)) {
          sections.add(section);
        }
      }
      Collections.sort(sections, new MostSpecificComparator(getRefName()));

      Set<SeenRule> seen = new HashSet<SeenRule>();
      Set<String> exclusiveGroupPermissions = new HashSet<String>();

      permissions = new HashMap<String, List<PermissionRule>>();
      for (AccessSection section : sections) {
        for (Permission permission : section.getPermissions()) {
          if (exclusiveGroupPermissions.contains(permission.getName())) {
            continue;
          }

          for (PermissionRule rule : permission.getRules()) {
            if (matchGroup(rule.getGroup().getUUID())) {
              SeenRule s = new SeenRule(section, permission, rule);
              if (seen.add(s) && !rule.getDeny()) {
                List<PermissionRule> r = permissions.get(permission.getName());
                if (r == null) {
                  r = new ArrayList<PermissionRule>(2);
                  permissions.put(permission.getName(), r);
                }
                r.add(rule);
              }
            }
          }

          if (permission.getExclusiveGroup()) {
            exclusiveGroupPermissions.add(permission.getName());
          }
        }
      }
    }
    return permissions;
  }

  private boolean appliesToRef(AccessSection section) {
    String refPattern = section.getRefPattern();

    if (isTemplate(refPattern)) {
      ParamertizedString template = new ParamertizedString(refPattern);
      HashMap<String, String> p = new HashMap<String, String>();

      if (getCurrentUser() instanceof IdentifiedUser) {
        p.put("username", ((IdentifiedUser) getCurrentUser()).getUserName());
      } else {
        // Right now we only template the username. If not available
        // this rule cannot be matched at all.
        //
        return false;
      }

      if (isRE(refPattern)) {
        for (Map.Entry<String, String> ent : p.entrySet()) {
          ent.setValue(escape(ent.getValue()));
        }
      }

      refPattern = template.replace(p);
    }

    if (isRE(refPattern)) {
      return Pattern.matches(refPattern, getRefName());

    } else if (refPattern.endsWith("/*")) {
      String prefix = refPattern.substring(0, refPattern.length() - 1);
      return getRefName().startsWith(prefix);

    } else {
      return getRefName().equals(refPattern);
    }
  }

  private boolean matchGroup(AccountGroup.UUID uuid) {
    Set<AccountGroup.UUID> userGroups = getCurrentUser().getEffectiveGroups();

    if (AccountGroup.PROJECT_OWNERS.equals(uuid)) {
      ProjectState state = projectControl.getProjectState();
      return CollectionsUtil.isAnyIncludedIn(state.getOwners(), userGroups);

    } else {
      return userGroups.contains(uuid);
    }
  }

  private static boolean isTemplate(String refPattern) {
    return 0 <= refPattern.indexOf("${");
  }

  private static String escape(String value) {
    // Right now the only special character allowed in a
    // variable value is a . in the username.
    //
    return value.replace(".", "\\.");
  }

  private static boolean isRE(String refPattern) {
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

  private static RegExp toRegExp(String refPattern) {
    if (isRE(refPattern)) {
      refPattern = refPattern.substring(1);
    }
    return new RegExp(refPattern, RegExp.NONE);
  }

  /** Tracks whether or not a permission has been overridden. */
  private static class SeenRule {
    final String refPattern;
    final String permissionName;
    final AccountGroup.UUID group;

    SeenRule(AccessSection section, Permission permission, PermissionRule rule) {
      refPattern = section.getRefPattern();
      permissionName = permission.getName();
      group = rule.getGroup().getUUID();
    }

    @Override
    public int hashCode() {
      int hc = refPattern.hashCode();
      hc = hc * 31 + permissionName.hashCode();
      if (group != null) {
        hc = hc * 31 + group.hashCode();
      }
      return hc;
    }

    @Override
    public boolean equals(Object other) {
      if (other instanceof SeenRule) {
        SeenRule a = this;
        SeenRule b = (SeenRule) other;
        return a.refPattern.equals(b.refPattern) //
            && a.permissionName.equals(b.permissionName) //
            && eq(a.group, b.group);
      }
      return false;
    }

    private boolean eq(AccountGroup.UUID a, AccountGroup.UUID b) {
      return a != null && b != null && a.equals(b);
    }
  }

  /**
   * Order the Ref Pattern by the most specific. This sort is done by:
   * <ul>
   * <li>1 - The minor value of Levenshtein string distance between the branch
   * name and the regex string shortest example. A shorter distance is a more
   * specific match.
   * <li>2 - Finites first, infinities after.
   * <li>3 - Number of transitions.
   * <li>4 - Length of the expression text.
   * </ul>
   *
   * Levenshtein distance is a measure of the similarity between two strings.
   * The distance is the number of deletions, insertions, or substitutions
   * required to transform one string into another.
   *
   * For example, if given refs/heads/m* and refs/heads/*, the distances are 5
   * and 6. It means that refs/heads/m* is more specific because it's closer to
   * refs/heads/master than refs/heads/*.
   *
   * Another example could be refs/heads/* and refs/heads/[a-zA-Z]*, the
   * distances are both 6. Both are infinite, but refs/heads/[a-zA-Z]* has more
   * transitions, which after all turns it more specific.
   */
  private static final class MostSpecificComparator implements
      Comparator<AccessSection> {
    private final String refName;

    MostSpecificComparator(String refName) {
      this.refName = refName;
    }

    public int compare(AccessSection a, AccessSection b) {
      return compare(a.getRefPattern(), b.getRefPattern());
    }

    private int compare(final String pattern1, final String pattern2) {
      int cmp = distance(pattern1) - distance(pattern2);
      if (cmp == 0) {
        boolean p1_finite = finite(pattern1);
        boolean p2_finite = finite(pattern2);

        if (p1_finite && !p2_finite) {
          cmp = -1;
        } else if (!p1_finite && p2_finite) {
          cmp = 1;
        } else /* if (f1 == f2) */{
          cmp = 0;
        }
      }
      if (cmp == 0) {
        cmp = transitions(pattern1) - transitions(pattern2);
      }
      if (cmp == 0) {
        cmp = pattern2.length() - pattern1.length();
      }
      return cmp;
    }

    private int distance(String pattern) {
      String example;
      if (isRE(pattern)) {
        example = shortestExample(pattern);

      } else if (pattern.endsWith("/*")) {
        example = pattern.substring(0, pattern.length() - 1) + '1';

      } else if (pattern.equals(refName)) {
        return 0;

      } else {
        return Math.max(pattern.length(), refName.length());
      }
      return StringUtils.getLevenshteinDistance(example, refName);
    }

    private boolean finite(String pattern) {
      if (isRE(pattern)) {
        return toRegExp(pattern).toAutomaton().isFinite();

      } else if (pattern.endsWith("/*")) {
        return false;

      } else {
        return true;
      }
    }

    private int transitions(String pattern) {
      if (isRE(pattern)) {
        return toRegExp(pattern).toAutomaton().getNumberOfTransitions();

      } else if (pattern.endsWith("/*")) {
        return pattern.length();

      } else {
        return pattern.length();
      }
    }
  }
}
