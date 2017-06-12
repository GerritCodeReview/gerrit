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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.gerrit.server.project.RefPattern.isRE;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
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
     * Get all permissions that apply to a reference.
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

      boolean perUser = false;
      Map<AccessSection, Project.NameKey> sectionToProject = new LinkedHashMap<>();
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
        //
        if (sm.matcher instanceof RefPatternMatcher.ExpandParameters) {
          if (!((RefPatternMatcher.ExpandParameters) sm.matcher).matchPrefix(ref)) {
            continue;
          }
          perUser = true;
          if (sm.match(ref, user)) {
            sectionToProject.put(sm.section, sm.project);
            break;
          }
        } else if (sm.match(ref, null)) {
          sectionToProject.put(sm.section, sm.project);
        }
      }
      List<AccessSection> sections = Lists.newArrayList(sectionToProject.keySet());
      sorter.sort(ref, sections);

      Set<SeenRule> seen = new HashSet<>();
      Set<String> exclusiveGroupPermissions = new HashSet<>();

      HashMap<String, List<PermissionRule>> permissions = new HashMap<>();
      HashMap<String, List<PermissionRule>> overridden = new HashMap<>();
      Map<PermissionRule, ProjectRef> ruleProps = Maps.newIdentityHashMap();
      Multimap<Project.NameKey, String> exclusivePermissionsByProject = ArrayListMultimap.create();
      for (AccessSection section : sections) {
        Project.NameKey project = sectionToProject.get(section);
        for (Permission permission : section.getPermissions()) {
          boolean exclusivePermissionExists =
              exclusiveGroupPermissions.contains(permission.getName());

          for (PermissionRule rule : permission.getRules()) {
            SeenRule s = SeenRule.create(section, permission, rule);
            boolean addRule;
            if (rule.isBlock()) {
              addRule = !exclusivePermissionsByProject.containsEntry(project, permission.getName());
            } else {
              addRule = seen.add(s) && !rule.isDeny() && !exclusivePermissionExists;
            }

            HashMap<String, List<PermissionRule>> p = null;
            if (addRule) {
              p = permissions;
            } else if (!rule.isDeny() && !exclusivePermissionExists) {
              p = overridden;
            }

            if (p != null) {
              List<PermissionRule> r = p.get(permission.getName());
              if (r == null) {
                r = new ArrayList<>(2);
                p.put(permission.getName(), r);
              }
              r.add(rule);
              ruleProps.put(rule, ProjectRef.create(project, section.getName()));
            }
          }

          if (permission.getExclusiveGroup()) {
            exclusivePermissionsByProject.put(project, permission.getName());
            exclusiveGroupPermissions.add(permission.getName());
          }
        }
      }

      return new PermissionCollection(permissions, overridden, ruleProps, perUser);
    }
  }

  private final Map<String, List<PermissionRule>> rules;
  private final Map<String, List<PermissionRule>> overridden;
  private final Map<PermissionRule, ProjectRef> ruleProps;
  private final boolean perUser;

  private PermissionCollection(
      Map<String, List<PermissionRule>> rules,
      Map<String, List<PermissionRule>> overridden,
      Map<PermissionRule, ProjectRef> ruleProps,
      boolean perUser) {
    this.rules = rules;
    this.overridden = overridden;
    this.ruleProps = ruleProps;
    this.perUser = perUser;
  }

  /**
   * @return true if a "${username}" pattern might need to be expanded to build this collection,
   *     making the results user specific.
   */
  public boolean isUserSpecific() {
    return perUser;
  }

  /**
   * Obtain all permission rules for a given type of permission.
   *
   * @param permissionName type of permission.
   * @return all rules that apply to this reference, for any group. Never null; the empty list is
   *     returned when there are no rules for the requested permission name.
   */
  public List<PermissionRule> getPermission(String permissionName) {
    List<PermissionRule> r = rules.get(permissionName);
    return r != null ? r : Collections.<PermissionRule>emptyList();
  }

  List<PermissionRule> getOverridden(String permissionName) {
    return firstNonNull(overridden.get(permissionName), Collections.<PermissionRule>emptyList());
  }

  ProjectRef getRuleProps(PermissionRule rule) {
    return ruleProps.get(rule);
  }

  /**
   * Obtain all declared permission rules that match the reference.
   *
   * @return all rules. The collection will iterate a permission if it was declared in the project
   *     configuration, either directly or inherited. If the project owner did not use a known
   *     permission (for example {@link Permission#FORGE_SERVER}, then it will not be represented in
   *     the result even if {@link #getPermission(String)} returns an empty list for the same
   *     permission.
   */
  public Iterable<Map.Entry<String, List<PermissionRule>>> getDeclaredPermissions() {
    return rules.entrySet();
  }

  /** Tracks whether or not a permission has been overridden. */
  @AutoValue
  abstract static class SeenRule {
    public abstract String refPattern();

    public abstract String permissionName();

    @Nullable
    public abstract AccountGroup.UUID group();

    static SeenRule create(
        AccessSection section, Permission permission, @Nullable PermissionRule rule) {
      AccountGroup.UUID group =
          rule != null && rule.getGroup() != null ? rule.getGroup().getUUID() : null;
      return new AutoValue_PermissionCollection_SeenRule(
          section.getName(), permission.getName(), group);
    }
  }
}
