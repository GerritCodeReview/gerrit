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
import static com.google.gerrit.reviewdb.ApprovalCategory.FORGE_SERVER;
import static com.google.gerrit.reviewdb.ApprovalCategory.OWN;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_HEAD;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_HEAD_CREATE;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_HEAD_REPLACE;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_HEAD_UPDATE;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_TAG;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_TAG_ANNOTATED;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_TAG_SIGNED;
import static com.google.gerrit.reviewdb.ApprovalCategory.READ;

import com.google.gerrit.common.data.ParamertizedString;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gerrit.reviewdb.SystemConfig;
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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;


/** Manages access control for Git references (aka branches, tags). */
public class RefControl {
  public interface Factory {
    RefControl create(ProjectControl projectControl, String ref);
  }

  private final SystemConfig systemConfig;
  private final ProjectControl projectControl;
  private final String refName;

  private Boolean canForgeAuthor;
  private Boolean canForgeCommitter;

  @Inject
  protected RefControl(final SystemConfig systemConfig,
      @Assisted final ProjectControl projectControl,
      @Assisted String ref) {
    this.systemConfig = systemConfig;
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
    if (canPerform(OWN, (short) 1)) {
      return true;
    }

    // We have to prevent infinite recursion here, the project control
    // calls us to find out if there is ownership of all references in
    // order to determine project level ownership.
    //
    if (getRefName().equals(
        RefRight.ALL.substring(0, RefRight.ALL.length() - 1))) {
      return getCurrentUser().isAdministrator();
    } else {
      return getProjectControl().isOwner();
    }
  }

  /** Can this user see this reference exists? */
  public boolean isVisible() {
    return getProjectControl().visibleForReplication()
        || canPerform(READ, (short) 1);
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

  /** @return true if this user can submit merge patch sets to this ref */
  public boolean canUploadMerges() {
    return canPerform(READ, (short) 3);
  }

  /** @return true if this user can submit patch sets to this ref */
  public boolean canSubmit() {
    return canPerform(ApprovalCategory.SUBMIT, (short) 1);
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

  /** @return true if this user can forge the server on the committer line. */
  public boolean canForgeGerritServerIdentity() {
    return canPerform(FORGE_IDENTITY, FORGE_SERVER);
  }

  public short normalize(ApprovalCategory.Id category, short score) {
    short minAllowed = 0, maxAllowed = 0;
    for (RefRight r : getApplicableRights(category)) {
      if (getCurrentUser().getEffectiveGroups().contains(r.getAccountGroupId())) {
        minAllowed = (short) Math.min(minAllowed, r.getMinValue());
        maxAllowed = (short) Math.max(maxAllowed, r.getMaxValue());
      }
    }

    if (score < minAllowed) {
      score = minAllowed;
    }
    if (score > maxAllowed) {
      score = maxAllowed;
    }
    return score;
  }

  /**
   * Convenience holder class used to map a ref pattern to the list of
   * {@code RefRight}s that use it in the database.
   */
  public final static class RefRightsForPattern {
    private final List<RefRight> rights;
    private boolean containsExclusive;

    public RefRightsForPattern() {
      rights = new ArrayList<RefRight>();
      containsExclusive = false;
    }

    public void addRight(RefRight right) {
      rights.add(right);
      if (right.isExclusive()) {
        containsExclusive = true;
      }
    }

    public List<RefRight> getRights() {
      return Collections.unmodifiableList(rights);
    }

    public boolean containsExclusive() {
      return containsExclusive;
    }

    /**
     * Returns The max allowed value for this ref pattern for all specified
     * groups.
     *
     * @param groups The groups of the user
     * @return The allowed value for this ref for all the specified groups
     */
    private boolean allowedValueForRef(Set<AccountGroup.Id> groups, short level) {
      for (RefRight right : rights) {
        if (groups.contains(right.getAccountGroupId())
            && right.getMaxValue() >= level) {
          return true;
        }
      }
      return false;
    }
  }

  boolean canPerform(ApprovalCategory.Id actionId, short level) {
    final Set<AccountGroup.Id> groups = getCurrentUser().getEffectiveGroups();

    List<RefRight> allRights = new ArrayList<RefRight>();
    allRights.addAll(getAllRights(actionId));

    SortedMap<String, RefRightsForPattern> perPatternRights =
      sortedRightsByPattern(allRights);

    for (RefRightsForPattern right : perPatternRights.values()) {
      if (right.allowedValueForRef(groups, level)) {
        return true;
      }
      if (right.containsExclusive() && !actionId.equals(OWN)) {
        break;
      }
    }
    return false;
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
  private final Comparator<String> BY_MOST_SPECIFIC_SORT =
      new Comparator<String>() {
        public int compare(final String pattern1, final String pattern2) {
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

          } else if (pattern.equals(getRefName())) {
            return 0;

          } else {
            return Math.max(pattern.length(), getRefName().length());
          }
          return StringUtils.getLevenshteinDistance(example, getRefName());
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
      };

  /**
   * Sorts all given rights into a map, ordered by descending length of
   * ref pattern.
   *
   * For example, if given the following rights in argument:
   *
   * ["refs/heads/master", group1, -1, +1],
   * ["refs/heads/master", group2, -2, +2],
   * ["refs/heads/*", group3, -1, +1]
   * ["refs/heads/stable", group2, -1, +1]
   *
   * Then the following map is returned:
   * "refs/heads/master" => {
   *      ["refs/heads/master", group1, -1, +1],
   *      ["refs/heads/master", group2, -2, +2]
   *  }
   * "refs/heads/stable" => {["refs/heads/stable", group2, -1, +1]}
   * "refs/heads/*" => {["refs/heads/*", group3, -1, +1]}
   *
   * @param actionRights
   * @return A sorted map keyed off the ref pattern of all rights.
   */
  private SortedMap<String, RefRightsForPattern> sortedRightsByPattern(
      List<RefRight> actionRights) {
    SortedMap<String, RefRightsForPattern> rights =
      new TreeMap<String, RefRightsForPattern>(BY_MOST_SPECIFIC_SORT);
    for (RefRight actionRight : actionRights) {
      RefRightsForPattern patternRights =
        rights.get(actionRight.getRefPattern());
      if (patternRights == null) {
        patternRights = new RefRightsForPattern();
        rights.put(actionRight.getRefPattern(), patternRights);
      }
      patternRights.addRight(actionRight);
    }
    return rights;
  }

  private List<RefRight> getAllRights(ApprovalCategory.Id actionId) {
    final List<RefRight> allRefRights = filter(getProjectState().getAllRights(actionId, true));
    return resolveOwnerGroups(allRefRights);
  }

  /**
   * Returns all applicable rights for a given approval category.
   *
   * Applicable rights are defined as the list of {@code RefRight}s which match
   * the ref for which this object was created, stopping the ref wildcard
   * matching when an exclusive ref right was encountered, for the given
   * approval category.
   * @param id The {@link ApprovalCategory.Id}.
   * @return All applicable rights.
   */
  public List<RefRight> getApplicableRights(final ApprovalCategory.Id id) {
    List<RefRight> l = new ArrayList<RefRight>();
    l.addAll(getAllRights(id));
    SortedMap<String, RefRightsForPattern> perPatternRights =
      sortedRightsByPattern(l);
    List<RefRight> applicable = new ArrayList<RefRight>();
    for (RefRightsForPattern patternRights : perPatternRights.values()) {
      applicable.addAll(patternRights.getRights());
      if (patternRights.containsExclusive()) {
        break;
      }
    }
    return Collections.unmodifiableList(applicable);
  }

  /**
   * Resolves all refRights which assign privileges to the 'Project Owners'
   * group. All other refRights stay unchanged.
   *
   * @param refRights refRights to be resolved
   * @return the resolved refRights
   */
  private List<RefRight> resolveOwnerGroups(final List<RefRight> refRights) {
    final List<RefRight> resolvedRefRights =
        new ArrayList<RefRight>(refRights.size());
    for (final RefRight refRight : refRights) {
      resolvedRefRights.addAll(resolveOwnerGroups(refRight));
    }
    return resolvedRefRights;
  }

  /**
   * Checks if the given refRight assigns privileges to the 'Project Owners'
   * group.
   * If yes, resolves the 'Project Owners' group to the concrete groups that
   * own the project and creates new refRights for the concrete owner groups
   * which are returned.
   * If no, the given refRight is returned unchanged.
   *
   * @param refRight refRight
   * @return the resolved refRights
   */
  private Set<RefRight> resolveOwnerGroups(final RefRight refRight) {
    final Set<RefRight> resolvedRefRights = new HashSet<RefRight>();
    if (refRight.getAccountGroupId().equals(systemConfig.ownerGroupId)) {
      for (final AccountGroup.Id ownerGroup : getProjectState().getOwners()) {
        if (!ownerGroup.equals(systemConfig.ownerGroupId)) {
          resolvedRefRights.add(new RefRight(refRight, ownerGroup));
        }
      }
    } else {
      resolvedRefRights.add(refRight);
    }
    return resolvedRefRights;
  }

  private List<RefRight> filter(Collection<RefRight> all) {
    List<RefRight> mine = new ArrayList<RefRight>(all.size());
    for (RefRight right : all) {
      if (matches(right.getRefPattern())) {
        mine.add(right);
      }
    }
    return mine;
  }

  private ProjectState getProjectState() {
    return projectControl.getProjectState();
  }

  private boolean matches(String refPattern) {
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
    return refPattern.startsWith(RefRight.REGEX_PREFIX);
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
}
