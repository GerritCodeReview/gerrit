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

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.AccountGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Caches active {@link GlobalCapability} set for a site. */
public class CapabilityCollection {
  private final Map<String, List<PermissionRule>> permissions;

  public final List<PermissionRule> administrateServer;
  public final List<PermissionRule> priority;
  public final List<PermissionRule> queryLimit;

  public CapabilityCollection(List<AccessSection> sections) {
    if (sections.isEmpty()) {
      sections.add(new AccessSection(AccessSection.GLOBAL_CAPABILITIES));
    }

    Map<String, List<PermissionRule>> tmp =
        new HashMap<String, List<PermissionRule>>();
    for (AccessSection section : sections) {
      for (Permission permission : section.getPermissions()) {
        for (PermissionRule rule : permission.getRules()) {
          if (rule.getAction() != PermissionRule.Action.DENY) {
            List<PermissionRule> r = tmp.get(permission.getName());
            if (r == null) {
              r = new ArrayList<PermissionRule>(2);
              tmp.put(permission.getName(), r);
            }
            r.add(rule);
          }
        }
      }
    }

    for (AccessSection section : sections) {
      configureDefaults(tmp, section);
    }

    Map<String, List<PermissionRule>> res =
        new HashMap<String, List<PermissionRule>>();
    for (Map.Entry<String, List<PermissionRule>> e : tmp.entrySet()) {
      List<PermissionRule> rules = e.getValue();
      if (rules.size() == 1) {
        res.put(e.getKey(), Collections.singletonList(rules.get(0)));
      } else {
        res.put(e.getKey(), Collections.unmodifiableList(
            Arrays.asList(rules.toArray(new PermissionRule[rules.size()]))));
      }
    }
    permissions = Collections.unmodifiableMap(res);

    administrateServer = getPermission(GlobalCapability.ADMINISTRATE_SERVER);
    priority = getPermission(GlobalCapability.PRIORITY);
    queryLimit = getPermission(GlobalCapability.QUERY_LIMIT);
  }

  public List<PermissionRule> getPermission(String permissionName) {
    List<PermissionRule> r = permissions.get(permissionName);
    return r != null ? r : Collections.<PermissionRule> emptyList();
  }

  private static final GroupReference anonymous = new GroupReference(
      AccountGroup.ANONYMOUS_USERS,
      "Anonymous Users");

  private static void configureDefaults(Map<String, List<PermissionRule>> out,
      AccessSection section) {
    configureDefault(out, section, GlobalCapability.QUERY_LIMIT, anonymous);
  }

  private static void configureDefault(Map<String, List<PermissionRule>> out,
      AccessSection section, String capName, GroupReference group) {
    if (doesNotDeclare(section, capName)) {
      PermissionRange.WithDefaults range = GlobalCapability.getRange(capName);
      if (range != null) {
        PermissionRule rule = new PermissionRule(group);
        rule.setRange(range.getDefaultMin(), range.getDefaultMax());
        out.put(capName, Collections.singletonList(rule));
      }
    }
  }

  private static boolean doesNotDeclare(AccessSection section, String capName) {
    return section.getPermission(capName) == null;
  }
}
