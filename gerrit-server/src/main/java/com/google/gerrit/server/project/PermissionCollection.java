// Copyright (C) 2011 The Android Open Source Project
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

import static com.google.gerrit.server.project.RefControl.isRE;
import static com.google.gerrit.server.project.RefControl.shortestExample;
import static com.google.gerrit.server.project.RefControl.toRegExp;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.AccountGroup;

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Effective permissions applied to a reference in a project.
 * <p>
 * A collection may be user specific if a matching {@link AccessSection} uses
 * "${username}" in its name. The permissions granted in that section may only
 * be granted to the username that appears in the reference name, and also only
 * if the user is a member of the relevant group.
 */
public class PermissionCollection {
  /**
   * Get all permissions that apply to a reference.
   *
   * @param matcherList collection of sections that should be considered, in
   *        priority order (project specific definitions must appear before
   *        inherited ones).
   * @param ref reference being accessed.
   * @param username if the reference is a per-user reference, access sections
   *        using the parameter variable "${username}" will first have {@code
   *        username} inserted into them before seeing if they apply to the
   *        reference named by {@code ref}. If null, per-user references are
   *        ignored.
   * @return map of permissions that apply to this reference, keyed by
   *         permission name.
   */
  static PermissionCollection filter(Iterable<SectionMatcher> matcherList,
      String ref, String username) {
    if (isRE(ref)) {
      ref = RefControl.shortestExample(ref);
    } else if (ref.endsWith("/*")) {
      ref = ref.substring(0, ref.length() - 1);
    }

    boolean perUser = false;
    List<AccessSection> sections = new ArrayList<AccessSection>();
    for (SectionMatcher matcher : matcherList) {
      // If the matcher has to expand parameters and its prefix matches the
      // reference there is a very good chance the reference is actually user
      // specific, even if the matcher does not match the reference. Since its
      // difficult to prove this is true all of the time, use an approximation
      // to prevent reuse of collections across users accessing the same
      // reference at the same time.
      //
      // This check usually gets caching right, as most per-user references
      // use a common prefix like "refs/sandbox/" or "refs/heads/users/"
      // that will never be shared with non-user references, and the per-user
      // references are usually less frequent than the non-user references.
      //
      if (username != null && !perUser
          && matcher instanceof SectionMatcher.ExpandParameters) {
        perUser = ((SectionMatcher.ExpandParameters) matcher).matchPrefix(ref);
      }

      if (matcher.match(ref, username)) {
        sections.add(matcher.section);
      }
    }
    Collections.sort(sections, new MostSpecificComparator(ref));

    Set<SeenRule> seen = new HashSet<SeenRule>();
    Set<String> exclusiveGroupPermissions = new HashSet<String>();

    HashMap<String, List<PermissionRule>> permissions =
        new HashMap<String, List<PermissionRule>>();
    for (AccessSection section : sections) {
      for (Permission permission : section.getPermissions()) {
        if (exclusiveGroupPermissions.contains(permission.getName())) {
          continue;
        }

        for (PermissionRule rule : permission.getRules()) {
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

        if (permission.getExclusiveGroup()) {
          exclusiveGroupPermissions.add(permission.getName());
        }
      }
    }

    return new PermissionCollection(ref, permissions, perUser ? username : null);
  }

  private final String ref;
  private final Map<String, List<PermissionRule>> rules;
  private final String username;

  private PermissionCollection(String ref,
      Map<String, List<PermissionRule>> rules, String username) {
    this.ref = ref;
    this.rules = rules;
    this.username = username;
  }

  /**
   * @return true if a "${username}" pattern might need to be expanded to build
   *         this collection, making the results user specific.
   */
  public boolean isUserSpecific() {
    return username != null;
  }

  /**
   * Obtain all permission rules for a given type of permission.
   *
   * @param permissionName type of permission.
   * @return all rules that apply to this reference, for any group. Never null;
   *         the empty list is returned when there are no rules for the requested
   *         permission name.
   */
  public List<PermissionRule> getPermission(String permissionName) {
    List<PermissionRule> r = rules.get(permissionName);
    return r != null ? r : Collections.<PermissionRule> emptyList();
  }

  /**
   * Obtain all declared permission rules that match the reference.
   *
   * @return all rules. The collection will iterate a permission if it was
   *         declared in the project configuration, either directly or
   *         inherited. If the project owner did not use a known permission (for
   *         example {@link Permission#FORGE_SERVER}, then it will not be
   *         represented in the result even if {@link #getPermission(String)}
   *         returns an empty list for the same permission.
   */
  public Iterable<Map.Entry<String, List<PermissionRule>>> getDeclaredPermissions() {
    return rules.entrySet();
  }

  /** Tracks whether or not a permission has been overridden. */
  private static class SeenRule {
    final String refPattern;
    final String permissionName;
    final AccountGroup.UUID group;

    SeenRule(AccessSection section, Permission permission, PermissionRule rule) {
      refPattern = section.getName();
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
      return compare(a.getName(), b.getName());
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
