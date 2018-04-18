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

package com.google.gerrit.server.permissions;

import static com.google.gerrit.common.data.PermissionRule.Action.BLOCK;
import static com.google.gerrit.server.project.RefPattern.isRE;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.data.PermissionRule.Action;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.RefPattern;
import com.google.gerrit.server.project.RefPatternMatcher.ExpandParameters;
import com.google.gerrit.server.project.SectionMatcher;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Effective permissions applied to a reference in a project.
 *
 * <p>A collection may be user specific if a matching {@link AccessSection} uses "${username}" in
 * its name. The permissions granted in that section may only be granted to the username that
 * appears in the reference name, and also only if the user is a member of the relevant group.
 */
public class PermissionCollection {
  @Singleton
  public static class Factory {
    private final SectionSortCache sorter;

    @Inject
    Factory(SectionSortCache sorter) {
      this.sorter = sorter;
    }

    /**
     * Drop the SectionMatchers that don't apply to the current ref. The user is only used for
     * expanding per-user ref patterns, and not for checking group memberships.
     *
     * @param matcherList the input sections.
     * @param ref the ref name for which to filter.
     * @param user Only used for expanding per-user ref patterns.
     * @param out the filtered sections.
     * @return true if the result is only valid for this user.
     */
    private static boolean filterRefMatchingSections(
        Iterable<SectionMatcher> matcherList,
        String ref,
        CurrentUser user,
        Map<AccessSection, Project.NameKey> out) {
      boolean perUser = false;
      for (SectionMatcher sm : matcherList) {
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
        if (sm.getMatcher() instanceof ExpandParameters) {
          if (!((ExpandParameters) sm.getMatcher()).matchPrefix(ref)) {
            continue;
          }
          perUser = true;
          if (sm.match(ref, user)) {
            out.put(sm.getSection(), sm.getProject());
          }
        } else if (sm.match(ref, null)) {
          out.put(sm.getSection(), sm.getProject());
        }
      }
      return perUser;
    }

    /**
     * Get all permissions that apply to a reference. The user is only used for per-user ref names,
     * so the return value may include permissions for groups the user is not part of.
     *
     * @param matcherList collection of sections that should be considered, in priority order
     *     (project specific definitions must appear before inherited ones).
     * @param ref reference being accessed.
     * @param user if the reference is a per-user reference, e.g. access sections using the
     *     parameter variable "${username}" will have each username inserted into them to see if
     *     they apply to the reference named by {@code ref}.
     * @return map of permissions that apply to this reference, keyed by permission name.
     */
    PermissionCollection filter(
        Iterable<SectionMatcher> matcherList, String ref, CurrentUser user) {
      if (isRE(ref)) {
        ref = RefPattern.shortestExample(ref);
      } else if (ref.endsWith("/*")) {
        ref = ref.substring(0, ref.length() - 1);
      }

      // LinkedHashMap to maintain input ordering.
      Map<AccessSection, Project.NameKey> sectionToProject = new LinkedHashMap<>();
      boolean perUser = filterRefMatchingSections(matcherList, ref, user, sectionToProject);
      List<AccessSection> sections = Lists.newArrayList(sectionToProject.keySet());

      // Sort by ref pattern specificity. For equally specific patterns, the sections from the
      // project closer to the current one come first.
      sorter.sort(ref, sections);

      // For block permissions, we want a different order: first, we want to go from parent to
      // child.
      List<Map.Entry<AccessSection, Project.NameKey>> accessDescending =
          Lists.reverse(Lists.newArrayList(sectionToProject.entrySet()));

      Map<Project.NameKey, List<AccessSection>> accessByProject =
          accessDescending
              .stream()
              .collect(
                  Collectors.groupingBy(
                      e -> e.getValue(), LinkedHashMap::new, mapping(e -> e.getKey(), toList())));
      // Within each project, sort by ref specificity.
      for (List<AccessSection> secs : accessByProject.values()) {
        sorter.sort(ref, secs);
      }

      return new PermissionCollection(
          Lists.newArrayList(accessByProject.values()), sections, perUser);
    }
  }

  /** Returns permissions in the right order for evaluating BLOCK status. */
  List<List<Permission>> getBlockRules(String perm) {
    List<List<Permission>> ps = blockPerProjectByPermission.get(perm);
    if (ps == null) {
      ps = calculateBlockRules(perm);
      blockPerProjectByPermission.put(perm, ps);
    }
    return ps;
  }

  /** Returns permissions in the right order for evaluating ALLOW/DENY status. */
  List<PermissionRule> getAllowRules(String perm) {
    List<PermissionRule> ps = rulesByPermission.get(perm);
    if (ps == null) {
      ps = calculateAllowRules(perm);
      rulesByPermission.put(perm, ps);
    }
    return ps;
  }

  /** calculates permissions for ALLOW processing. */
  private List<PermissionRule> calculateAllowRules(String permName) {
    Set<SeenRule> seen = new HashSet<>();

    List<PermissionRule> r = new ArrayList<>();
    for (AccessSection s : accessSectionsUpward) {
      Permission p = s.getPermission(permName);
      if (p == null) {
        continue;
      }
      for (PermissionRule pr : p.getRules()) {
        SeenRule sr = SeenRule.create(s, pr);
        if (seen.contains(sr)) {
          // We allow only one rule per (ref-pattern, group) tuple. This is used to implement DENY:
          // If we see a DENY before an ALLOW rule, that causes the ALLOW rule to be skipped here,
          // negating access.
          continue;
        }
        seen.add(sr);

        if (pr.getAction() == BLOCK) {
          // Block rules are handled elsewhere.
          continue;
        }

        if (pr.getAction() == PermissionRule.Action.DENY) {
          // DENY rules work by not adding ALLOW rules. Nothing else to do.
          continue;
        }
        r.add(pr);
      }
      if (p.getExclusiveGroup()) {
        // We found an exclusive permission, so no need to further go up the hierarchy.
        break;
      }
    }
    return r;
  }

  // Calculates the inputs for determining BLOCK status, grouped by project.
  private List<List<Permission>> calculateBlockRules(String permName) {
    List<List<Permission>> result = new ArrayList<>();
    for (List<AccessSection> secs : this.accessSectionsPerProjectDownward) {
      List<Permission> perms = new ArrayList<>();
      boolean blockFound = false;
      for (AccessSection sec : secs) {
        Permission p = sec.getPermission(permName);
        if (p == null) {
          continue;
        }
        for (PermissionRule pr : p.getRules()) {
          if (blockFound || pr.getAction() == Action.BLOCK) {
            blockFound = true;
            break;
          }
        }

        perms.add(p);
      }

      if (blockFound) {
        result.add(perms);
      }
    }
    return result;
  }

  private List<List<AccessSection>> accessSectionsPerProjectDownward;
  private List<AccessSection> accessSectionsUpward;

  private final Map<String, List<PermissionRule>> rulesByPermission;
  private final Map<String, List<List<Permission>>> blockPerProjectByPermission;
  private final boolean perUser;

  private PermissionCollection(
      List<List<AccessSection>> accessSectionsDownward,
      List<AccessSection> accessSectionsUpward,
      boolean perUser) {
    this.accessSectionsPerProjectDownward = accessSectionsDownward;
    this.accessSectionsUpward = accessSectionsUpward;
    this.rulesByPermission = new HashMap<>();
    this.blockPerProjectByPermission = new HashMap<>();
    this.perUser = perUser;
  }

  /**
   * @return true if a "${username}" pattern might need to be expanded to build this collection,
   *     making the results user specific.
   */
  public boolean isUserSpecific() {
    return perUser;
  }

  /** (ref, permission, group) tuple. */
  @AutoValue
  abstract static class SeenRule {
    public abstract String refPattern();

    @Nullable
    public abstract AccountGroup.UUID group();

    static SeenRule create(AccessSection section, @Nullable PermissionRule rule) {
      AccountGroup.UUID group =
          rule != null && rule.getGroup() != null ? rule.getGroup().getUUID() : null;
      return new AutoValue_PermissionCollection_SeenRule(section.getName(), group);
    }
  }
}
