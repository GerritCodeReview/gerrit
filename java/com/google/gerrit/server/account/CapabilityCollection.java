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

package com.google.gerrit.server.account;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.PermissionRange;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.server.config.AdministrateServerGroups;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Caches active {@link GlobalCapability} set for a site. */
public class CapabilityCollection {
  public interface Factory {
    CapabilityCollection create(Optional<AccessSection> section);
  }

  private final SystemGroupBackend systemGroupBackend;
  private final ImmutableMap<String, ImmutableList<PermissionRule>> permissions;

  public final ImmutableList<PermissionRule> administrateServer;
  public final ImmutableList<PermissionRule> batchChangesLimit;
  public final ImmutableList<PermissionRule> emailReviewers;
  public final ImmutableList<PermissionRule> priority;
  public final ImmutableList<PermissionRule> readAs;
  public final ImmutableList<PermissionRule> queryLimit;
  public final ImmutableList<PermissionRule> createGroup;

  @Inject
  CapabilityCollection(
      SystemGroupBackend systemGroupBackend,
      @AdministrateServerGroups ImmutableSet<GroupReference> admins,
      @Assisted Optional<AccessSection> maybeSection) {
    this.systemGroupBackend = systemGroupBackend;

    AccessSection section =
        maybeSection.orElse(AccessSection.create(AccessSection.GLOBAL_CAPABILITIES));
    Map<String, List<PermissionRule>> tmp = new HashMap<>();
    for (Permission permission : section.getPermissions()) {
      for (PermissionRule rule : permission.getRules()) {
        if (!permission.getName().equals(GlobalCapability.EMAIL_REVIEWERS)
            && rule.getAction() == PermissionRule.Action.DENY) {
          continue;
        }

        List<PermissionRule> r = tmp.get(permission.getName());
        if (r == null) {
          r = new ArrayList<>(2);
          tmp.put(permission.getName(), r);
        }
        r.add(rule);
      }
    }
    configureDefaults(tmp, section);
    if (!tmp.containsKey(GlobalCapability.ADMINISTRATE_SERVER) && !admins.isEmpty()) {
      tmp.put(GlobalCapability.ADMINISTRATE_SERVER, ImmutableList.of());
    }

    ImmutableMap.Builder<String, ImmutableList<PermissionRule>> m = ImmutableMap.builder();
    for (Map.Entry<String, List<PermissionRule>> e : tmp.entrySet()) {
      List<PermissionRule> rules = e.getValue();
      if (GlobalCapability.ADMINISTRATE_SERVER.equals(e.getKey())) {
        rules = mergeAdmin(admins, rules);
      }
      m.put(e.getKey(), ImmutableList.copyOf(rules));
    }
    permissions = m.build();

    administrateServer = getPermission(GlobalCapability.ADMINISTRATE_SERVER);
    batchChangesLimit = getPermission(GlobalCapability.BATCH_CHANGES_LIMIT);
    emailReviewers = getPermission(GlobalCapability.EMAIL_REVIEWERS);
    priority = getPermission(GlobalCapability.PRIORITY);
    readAs = getPermission(GlobalCapability.READ_AS);
    queryLimit = getPermission(GlobalCapability.QUERY_LIMIT);
    createGroup = getPermission(GlobalCapability.CREATE_GROUP);
  }

  private static List<PermissionRule> mergeAdmin(
      Set<GroupReference> admins, List<PermissionRule> rules) {
    if (admins.isEmpty()) {
      return rules;
    }

    List<PermissionRule> r = new ArrayList<>(admins.size() + rules.size());
    for (GroupReference g : admins) {
      r.add(PermissionRule.create(g));
    }
    for (PermissionRule rule : rules) {
      if (!admins.contains(rule.getGroup())) {
        r.add(rule);
      }
    }
    return r;
  }

  public ImmutableList<PermissionRule> getPermission(String permissionName) {
    ImmutableList<PermissionRule> r = permissions.get(permissionName);
    return r != null ? r : ImmutableList.of();
  }

  private void configureDefaults(Map<String, List<PermissionRule>> out, AccessSection section) {
    configureDefault(
        out,
        section,
        GlobalCapability.QUERY_LIMIT,
        systemGroupBackend.getGroup(SystemGroupBackend.ANONYMOUS_USERS));
  }

  private static void configureDefault(
      Map<String, List<PermissionRule>> out,
      AccessSection section,
      String capName,
      GroupReference group) {
    if (doesNotDeclare(section, capName)) {
      PermissionRange.WithDefaults range = GlobalCapability.getRange(capName);
      if (range != null) {
        PermissionRule.Builder rule = PermissionRule.builder(group);
        rule.setRange(range.getDefaultMin(), range.getDefaultMax());
        out.put(capName, Collections.singletonList(rule.build()));
      }
    }
  }

  private static boolean doesNotDeclare(AccessSection section, String capName) {
    return section.getPermission(capName) == null;
  }
}
