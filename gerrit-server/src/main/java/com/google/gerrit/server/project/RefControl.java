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

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;


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
    if (canPerform(OWN, (short) 1)) {
      return true;
    }

    // We have to prevent infinite recursion here, the project control
    // calls us to find out if there is ownership of all references in
    // order to determine project level ownership.
    //
    if (!RefRight.ALL.equals(getRefName()) && getProjectControl().isOwner()) {
      return true;
    }

    return false;
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
    public int allowedValueForRef(Set<AccountGroup.Id> groups) {
      int val = Integer.MIN_VALUE;
      for (RefRight right : rights) {
        if (groups.contains(right.getAccountGroupId())) {
          val = Math.max(right.getMaxValue(), val);
        }
      }
      return val;
    }
  }

  boolean canPerform(ApprovalCategory.Id actionId, short level) {
    final Set<AccountGroup.Id> groups = getCurrentUser().getEffectiveGroups();
    int val = Integer.MIN_VALUE;

    List<RefRight> allRights = new ArrayList<RefRight>();
    allRights.addAll(getLocalRights(actionId));

    if (actionId.canInheritFromWildProject()) {
      allRights.addAll(getInheritedRights(actionId));
    }

    SortedMap<String, RefRightsForPattern> perPatternRights =
      sortedRightsByPattern(allRights);

    SortedMap<String, RefRightsForPattern> exclusives =
      retainExclusives(perPatternRights);

    if (!exclusives.isEmpty()) {
      for (RefRightsForPattern right : exclusives.values()) {
        val = Math.max(val, right.allowedValueForRef(groups));
        if (val >= level) {
          return val >= level;
        }
      }
      return val >= level;
    } else {
        if (!perPatternRights.isEmpty()) {
          RefRightsForPattern right =
              perPatternRights.get(perPatternRights.firstKey());
          val = Math.max(val, right.allowedValueForRef(groups));
        }
        return val >= level;
    }
  }

  public static final Comparator<String> DESCENDING_SORT =
      new Comparator<String>() {

    @Override
    public int compare(String a, String b) {
      int aLength = a.length();
      int bLength = b.length();
      if (bLength == aLength) {
        return a.compareTo(b);
      }
      return bLength - aLength;
    }
  };

  /**
   * Order the Ref Pattern by the most specific. This sort is done by:
   *
   * 1 - The minor value of Levenshtein string distance beetwen the branch name
   * and the regex string shortest example 2 - Finites first, infinities after.
   * 3 - Major value of transitions 4 - Minor value of the regex length
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
   * transitions, what after all turns it more specific.
   *
   */
  public final Comparator<String> BY_MOST_SPECIFIC_SORT =
      new Comparator<String>() {
        public int compare(final String o1, final String o2) {
          int cmp = 0;

          final String refPattern1 = o1.replace("*", "(.*)").replace(RefRight.REGEX_SYMBOL, "");
          final String refPattern2 = o2.replace("*", "(.*)").replace(RefRight.REGEX_SYMBOL, "");

          final RegExp regexp1 = new RegExp(refPattern1);
          final RegExp regexp2 = new RegExp(refPattern2);

          final Automaton automaton1 = regexp1.toAutomaton();
          final Automaton automaton2 = regexp2.toAutomaton();

          cmp = getLevenshteinDistance(automaton1.getShortestExample(true), getRefName())
          - getLevenshteinDistance(automaton2.getShortestExample(true), getRefName());

          if (cmp == 0) {
            if (automaton1.isFinite() && !automaton2.isFinite()) {
              cmp = -1;
            }
            if (!automaton1.isFinite() && automaton2.isFinite()) {
              cmp = 1;
            }
            if (automaton1.isFinite() == automaton2.isFinite()) {
              cmp = automaton1.getNumberOfTransitions() - automaton2.getNumberOfTransitions();
              if (cmp == 0) {
                cmp = refPattern1.length() - refPattern2.length();
              }
            }
          }
          return cmp;
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

  private List<RefRight> getLocalRights(ApprovalCategory.Id actionId) {
    return filter(getProjectState().getLocalRights(actionId));
  }

  private List<RefRight> getInheritedRights(ApprovalCategory.Id actionId) {
    return filter(getProjectState().getInheritedRights(actionId));
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
    l.addAll(getLocalRights(id));
    l.addAll(getInheritedRights(id));
    SortedMap<String, RefRightsForPattern> perPatternRights =
      sortedRightsByPattern(l);
    List<RefRight> applicable = new ArrayList<RefRight>();
    SortedMap<String, RefRightsForPattern> exclusives =
      retainExclusives(perPatternRights);

    // Is there exclusive ref patterns inside perPatternRights?
    if (!exclusives.isEmpty()) {
      // yes, so get all exclusive RefRights and puts into applicable list
      for (RefRightsForPattern patternRights : exclusives.values()) {
        applicable.addAll(patternRights.getRights());
      }
    } else {
      // no, so get all RefRights defined by the most specific ref pattern and
      // inserts into applicable
      if (!perPatternRights.isEmpty())
        applicable.addAll(perPatternRights.get(perPatternRights.firstKey())
          .getRights());
    }
    return Collections.unmodifiableList(applicable);
  }

  private SortedMap<String, RefRightsForPattern> retainExclusives(
      SortedMap<String, RefRightsForPattern> perPatternRights) {
    SortedMap<String, RefRightsForPattern> rights =
        new TreeMap<String, RefRightsForPattern>();

    for (Map.Entry<String, RefRightsForPattern> entry : perPatternRights.entrySet()) {
      RefRightsForPattern patternRights =
          (RefRightsForPattern) entry.getValue();
      if (patternRights.containsExclusive()) {
        rights.put((String) entry.getKey(), patternRights);
      }
    }
    return rights;
  }


  private List<RefRight> filter(Collection<RefRight> all) {
    List<RefRight> mine = new ArrayList<RefRight>(all.size());
    for (RefRight right : all) {
      if (right.getRefPattern().startsWith(RefRight.REGEX_SYMBOL)) {
        if (matches(getRefName(), right.getRefPattern().substring(1))) {
          mine.add(right);
        }
      }else {
        if (right.getRefPattern().equals(getRefName())) {
          mine.add(right);
        }
      }
    }
    return mine;
  }

  private ProjectState getProjectState() {
    return projectControl.getProjectState();
  }

  public static boolean matches(String refName, String refPattern) {
    return Pattern.matches(refPattern.replace("*", "(.*)"), refName);
  }
  //This code bellow is under apache license as you can see at
  //http://www.java2s.com/Code/Java/Data-Type/FindtheLevenshteindistancebetweentwoStrings.htm

  /**
   * <p>Find the Levenshtein distance between two Strings.</p>
   *
   * <p>This is the number of changes needed to change one String into
   * another, where each change is a single character modification (deletion,
   * insertion or substitution).</p>
   *
   * <p>The previous implementation of the Levenshtein distance algorithm
   * was from <a href="http://www.merriampark.com/ld.htm">http://www.merriampark.com/ld.htm</a></p>
   *
   * <p>Chas Emerick has written an implementation in Java, which avoids an OutOfMemoryError
   * which can occur when my Java implementation is used with very large strings.<br>
   * This implementation of the Levenshtein distance algorithm
   * is from <a href="http://www.merriampark.com/ldjava.htm">http://www.merriampark.com/ldjava.htm</a></p>
   *
   * <pre>
   * StringUtils.getLevenshteinDistance(null, *)             = IllegalArgumentException
   * StringUtils.getLevenshteinDistance(*, null)             = IllegalArgumentException
   * StringUtils.getLevenshteinDistance("","")               = 0
   * StringUtils.getLevenshteinDistance("","a")              = 1
   * StringUtils.getLevenshteinDistance("aaapppp", "")       = 7
   * StringUtils.getLevenshteinDistance("frog", "fog")       = 1
   * StringUtils.getLevenshteinDistance("fly", "ant")        = 3
   * StringUtils.getLevenshteinDistance("elephant", "hippo") = 7
   * StringUtils.getLevenshteinDistance("hippo", "elephant") = 7
   * StringUtils.getLevenshteinDistance("hippo", "zzzzzzzz") = 8
   * StringUtils.getLevenshteinDistance("hello", "hallo")    = 1
   * </pre>
   *
   * @param s  the first String, must not be null
   * @param t  the second String, must not be null
   * @return result distance
   * @throws IllegalArgumentException if either String input <code>null</code>
   */
  private static int getLevenshteinDistance(String s, String t) {
    if (s == null || t == null) {
      throw new IllegalArgumentException("Strings must not be null");
    }
    int n = s.length();
    int m = t.length();
    if (n == 0) {
      return m;
    } else if (m == 0) {
      return n;
    }

    if (n > m) {

      String tmp = s;
      s = t;
      t = tmp;
      n = m;
      m = t.length();
    }

    int p[] = new int[n + 1];
    int d[] = new int[n + 1];
    int _d[];


    int i;
    int j;

    char t_j;

    int cost;

    for (i = 0; i <= n; i++) {
      p[i] = i;
    }

    for (j = 1; j <= m; j++) {
      t_j = t.charAt(j - 1);
      d[0] = j;
      for (i = 1; i <= n; i++) {
        cost = s.charAt(i - 1) == t_j ? 0 : 1;
        d[i] = Math.min(Math.min(d[i - 1] + 1, p[i] + 1), p[i - 1] + cost);
      }

      _d = p;
      p = d;
      d = _d;
    }
    return p[n];
  }
}
