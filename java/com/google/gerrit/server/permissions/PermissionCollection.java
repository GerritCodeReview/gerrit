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
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
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

      List<AccessSection> sectionsTopDown = findSectionsTopDown(sectionToProject, sections);
      Set<String> permissionNames = new HashSet<>();
      for (AccessSection s : sections) {
        for (Permission p : s.getPermissions()) {
          permissionNames.add(p.getName());
        }
      }
      Map<String, List<PermissionRule>> rulesByPermission = new HashMap<>();
      Map<String, List<BlockAccessSection>> blockByPermission = new HashMap<>();

      for (String pn : permissionNames) {
        List<BlockAccessSection> blocks = findBlocks(sectionsTopDown, pn);
        if (!blocks.isEmpty()) {
          blockByPermission.put(pn, blocks);
        }

        List<PermissionRule> rules = findRules(sections, pn);
        if (!rules.isEmpty()) {
          rulesByPermission.put(pn, rules);
        }
      }

      return new PermissionCollection(rulesByPermission, blockByPermission, perUser);
    }
  }

  // For block processing, we want to maintain the ref matching order, but want entries for
  // the top project first.
  static List<AccessSection> findSectionsTopDown(
      Map<AccessSection, Project.NameKey> sectionToProject, List<AccessSection> sections) {
    List<List<AccessSection>> grouped = new ArrayList<>(4);
    Project.NameKey last = null;
    List<AccessSection> cur = new ArrayList<>();
    for (AccessSection s : sections) {
      Project.NameKey p = sectionToProject.get(s);
      if (!p.equals(last) && !cur.isEmpty()) {
        grouped.add(cur);
        cur = new ArrayList<>();
      }
      cur.add(s);
      last = p;
    }
    if (!cur.isEmpty()) {
      grouped.add(cur);
    }

    return Lists.reverse(grouped).stream().flatMap(List::stream).collect(toList());
  }

  static List<PermissionRule> findRules(List<AccessSection> sections, String permName) {
    List<PermissionRule> r = new ArrayList<>();
    for (AccessSection s : sections) {
      Permission p = s.getPermission(permName);
      if (p == null) {
        continue;
      }
      for (PermissionRule pr : p.getRules()) {
        r.add(pr);
      }
      if (p.getExclusiveGroup()) {
        // We found an exclusive permission, so no need to further go up the hierarchy.
        break;
      }
    }
    return r;
  }

  // NOSUBMIT: fix unblockMoreSpecificRefWithExclusiveFlag
  static List<BlockAccessSection> findBlocks(List<AccessSection> sections, String permName) {
    List<Permission> blockPerms = new ArrayList<>();

    for (AccessSection s : sections) {
      Permission p = s.getPermission(permName);
      if (p == null) {
        continue;
      }

      for (PermissionRule r : p.getRules()) {
        if (r.getAction() == BLOCK) {
          blockPerms.add(p);
          break;
        }
      }
    }

    List<BlockAccessSection> result = new ArrayList<>();
    for (Permission p : blockPerms) {
      BlockAccessSection sect = new BlockAccessSection();

      for (PermissionRule r : p.getRules()) {
        if (r.getAction() == BLOCK) {
          sect.blocks.add(r);
        } else {
          sect.overrides.add(r);
        }
      }
      result.add(sect);
    }

    return result;
  }

  static class BlockAccessSection {
    List<PermissionRule> blocks;
    List<PermissionRule> overrides;

    BlockAccessSection() {
      blocks = new ArrayList<>();
      overrides = new ArrayList<>();
    }
  };

  private final Map<String, List<PermissionRule>> rulesByPermission;
  private final Map<String, List<BlockAccessSection>> blockByPermission;
  private final boolean perUser;

  public List<PermissionRule> getRules(String perm) {
    return rulesByPermission.getOrDefault(perm, ImmutableList.of());
  }

  public List<BlockAccessSection> getBlocks(String perm) {
    return blockByPermission.getOrDefault(perm, ImmutableList.of());
  }

  private PermissionCollection(
      Map<String, List<PermissionRule>> rulesByPermission,
      Map<String, List<BlockAccessSection>> blockByPermission,
      boolean perUser) {
    this.rulesByPermission = rulesByPermission;
    this.blockByPermission = blockByPermission;
    this.perUser = perUser;
  }

  /**
   * @return true if a "${username}" pattern might need to be expanded to build this collection,
   *     making the results user specific.
   */
  public boolean isUserSpecific() {
    return perUser;
  }
}
