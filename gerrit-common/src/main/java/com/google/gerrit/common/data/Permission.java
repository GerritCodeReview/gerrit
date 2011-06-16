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

package com.google.gerrit.common.data;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** A single permission within an {@link AccessSection} of a project. */
public class Permission implements Comparable<Permission> {
  public static final String CREATE = "create";
  public static final String FORGE_AUTHOR = "forgeAuthor";
  public static final String FORGE_COMMITTER = "forgeCommitter";
  public static final String FORGE_SERVER = "forgeServerAsCommitter";
  public static final String LABEL = "label-";
  public static final String OWNER = "owner";
  public static final String PUSH = "push";
  public static final String PUSH_MERGE = "pushMerge";
  public static final String PUSH_TAG = "pushTag";
  public static final String READ = "read";
  public static final String SUBMIT = "submit";

  private static final List<String> NAMES_LC;
  private static final int labelIndex;

  static {
    NAMES_LC = new ArrayList<String>();
    NAMES_LC.add(OWNER.toLowerCase());
    NAMES_LC.add(READ.toLowerCase());
    NAMES_LC.add(CREATE.toLowerCase());
    NAMES_LC.add(FORGE_AUTHOR.toLowerCase());
    NAMES_LC.add(FORGE_COMMITTER.toLowerCase());
    NAMES_LC.add(FORGE_SERVER.toLowerCase());
    NAMES_LC.add(PUSH.toLowerCase());
    NAMES_LC.add(PUSH_MERGE.toLowerCase());
    NAMES_LC.add(PUSH_TAG.toLowerCase());
    NAMES_LC.add(LABEL.toLowerCase());
    NAMES_LC.add(SUBMIT.toLowerCase());

    labelIndex = NAMES_LC.indexOf(Permission.LABEL);
  }

  /** @return true if the name is recognized as a permission name. */
  public static boolean isPermission(String varName) {
    String lc = varName.toLowerCase();
    if (lc.startsWith(LABEL)) {
      return LABEL.length() < lc.length();
    }
    return NAMES_LC.contains(lc);
  }

  /** @return true if the permission name is actually for a review label. */
  public static boolean isLabel(String varName) {
    return varName.startsWith(LABEL) && LABEL.length() < varName.length();
  }

  /** @return permission name for the given review label. */
  public static String forLabel(String labelName) {
    return LABEL + labelName;
  }

  protected String name;
  protected boolean exclusiveGroup;
  protected List<PermissionRule> rules;

  protected Permission() {
  }

  public Permission(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public boolean isLabel() {
    return isLabel(getName());
  }

  public String getLabel() {
    if (isLabel()) {
      return getName().substring(LABEL.length());
    }
    return null;
  }

  public Boolean getExclusiveGroup() {
    // Only permit exclusive group behavior on non OWNER permissions,
    // otherwise an owner might lose access to a delegated subspace.
    //
    return exclusiveGroup && !OWNER.equals(getName());
  }

  public void setExclusiveGroup(Boolean newExclusiveGroup) {
    exclusiveGroup = newExclusiveGroup;
  }

  public List<PermissionRule> getRules() {
    initRules();
    return rules;
  }

  public void setRules(List<PermissionRule> list) {
    rules = list;
  }

  public void add(PermissionRule rule) {
    initRules();
    rules.add(rule);
  }

  public void remove(PermissionRule rule) {
    if (rule != null) {
      removeRule(rule.getGroup());
    }
  }

  public void removeRule(GroupReference group) {
    if (rules != null) {
      for (Iterator<PermissionRule> itr = rules.iterator(); itr.hasNext();) {
        if (sameGroup(itr.next(), group)) {
          itr.remove();
        }
      }
    }
  }

  public PermissionRule getRule(GroupReference group) {
    return getRule(group, false);
  }

  public PermissionRule getRule(GroupReference group, boolean create) {
    initRules();

    for (PermissionRule r : rules) {
      if (sameGroup(r, group)) {
        return r;
      }
    }

    if (create) {
      PermissionRule r = new PermissionRule(group);
      rules.add(r);
      return r;
    } else {
      return null;
    }
  }

  void mergeFrom(Permission src) {
    for (PermissionRule srcRule : src.getRules()) {
      PermissionRule dstRule = getRule(srcRule.getGroup());
      if (dstRule != null) {
        dstRule.mergeFrom(srcRule);
      } else {
        add(srcRule);
      }
    }
  }

  private static boolean sameGroup(PermissionRule rule, GroupReference group) {
    if (group.getUUID() != null) {
      return group.getUUID().equals(rule.getGroup().getUUID());

    } else if (group.getName() != null) {
      return group.getName().equals(rule.getGroup().getName());

    } else {
      return false;
    }
  }

  private void initRules() {
    if (rules == null) {
      rules = new ArrayList<PermissionRule>(4);
    }
  }

  @Override
  public int compareTo(Permission b) {
    int cmp = index(this) - index(b);
    if (cmp == 0) {
      cmp = getName().compareTo(b.getName());
    }
    return cmp;
  }

  private static int index(Permission a) {
    if (a.isLabel()) {
      return labelIndex;
    }

    int index = NAMES_LC.indexOf(a.getName().toLowerCase());
    return 0 <= index ? index : NAMES_LC.size();
  }
}
