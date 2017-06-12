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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

/** A single permission within an {@link AccessSection} of a project. */
public class Permission implements Comparable<Permission> {
  public static final String ABANDON = "abandon";
  public static final String ADD_PATCH_SET = "addPatchSet";
  public static final String CREATE = "create";
  public static final String DELETE = "delete";
  public static final String CREATE_TAG = "createTag";
  public static final String CREATE_SIGNED_TAG = "createSignedTag";
  public static final String DELETE_DRAFTS = "deleteDrafts";
  public static final String EDIT_HASHTAGS = "editHashtags";
  public static final String EDIT_ASSIGNEE = "editAssignee";
  public static final String EDIT_TOPIC_NAME = "editTopicName";
  public static final String FORGE_AUTHOR = "forgeAuthor";
  public static final String FORGE_COMMITTER = "forgeCommitter";
  public static final String FORGE_SERVER = "forgeServerAsCommitter";
  public static final String LABEL = "label-";
  public static final String LABEL_AS = "labelAs-";
  public static final String OWNER = "owner";
  public static final String PUBLISH_DRAFTS = "publishDrafts";
  public static final String PUSH = "push";
  public static final String PUSH_MERGE = "pushMerge";
  public static final String READ = "read";
  public static final String REBASE = "rebase";
  public static final String REMOVE_REVIEWER = "removeReviewer";
  public static final String SUBMIT = "submit";
  public static final String SUBMIT_AS = "submitAs";
  public static final String VIEW_DRAFTS = "viewDrafts";

  private static final List<String> NAMES_LC;
  private static final int LABEL_INDEX;
  private static final int LABEL_AS_INDEX;

  static {
    NAMES_LC = new ArrayList<>();
    NAMES_LC.add(OWNER.toLowerCase());
    NAMES_LC.add(READ.toLowerCase());
    NAMES_LC.add(ABANDON.toLowerCase());
    NAMES_LC.add(ADD_PATCH_SET.toLowerCase());
    NAMES_LC.add(CREATE.toLowerCase());
    NAMES_LC.add(CREATE_TAG.toLowerCase());
    NAMES_LC.add(CREATE_SIGNED_TAG.toLowerCase());
    NAMES_LC.add(DELETE.toLowerCase());
    NAMES_LC.add(FORGE_AUTHOR.toLowerCase());
    NAMES_LC.add(FORGE_COMMITTER.toLowerCase());
    NAMES_LC.add(FORGE_SERVER.toLowerCase());
    NAMES_LC.add(PUSH.toLowerCase());
    NAMES_LC.add(PUSH_MERGE.toLowerCase());
    NAMES_LC.add(LABEL.toLowerCase());
    NAMES_LC.add(LABEL_AS.toLowerCase());
    NAMES_LC.add(REBASE.toLowerCase());
    NAMES_LC.add(REMOVE_REVIEWER.toLowerCase());
    NAMES_LC.add(SUBMIT.toLowerCase());
    NAMES_LC.add(SUBMIT_AS.toLowerCase());
    NAMES_LC.add(VIEW_DRAFTS.toLowerCase());
    NAMES_LC.add(EDIT_TOPIC_NAME.toLowerCase());
    NAMES_LC.add(EDIT_HASHTAGS.toLowerCase());
    NAMES_LC.add(EDIT_ASSIGNEE.toLowerCase());
    NAMES_LC.add(DELETE_DRAFTS.toLowerCase());
    NAMES_LC.add(PUBLISH_DRAFTS.toLowerCase());

    LABEL_INDEX = NAMES_LC.indexOf(Permission.LABEL);
    LABEL_AS_INDEX = NAMES_LC.indexOf(Permission.LABEL_AS.toLowerCase());
  }

  /** @return true if the name is recognized as a permission name. */
  public static boolean isPermission(String varName) {
    return isLabel(varName) || isLabelAs(varName) || NAMES_LC.contains(varName.toLowerCase());
  }

  public static boolean hasRange(String varName) {
    return isLabel(varName) || isLabelAs(varName);
  }

  /** @return true if the permission name is actually for a review label. */
  public static boolean isLabel(String varName) {
    return varName.startsWith(LABEL) && LABEL.length() < varName.length();
  }

  /** @return true if the permission is for impersonated review labels. */
  public static boolean isLabelAs(String var) {
    return var.startsWith(LABEL_AS) && LABEL_AS.length() < var.length();
  }

  /** @return permission name for the given review label. */
  public static String forLabel(String labelName) {
    return LABEL + labelName;
  }

  /** @return permission name to apply a label for another user. */
  public static String forLabelAs(String labelName) {
    return LABEL_AS + labelName;
  }

  public static String extractLabel(String varName) {
    if (isLabel(varName)) {
      return varName.substring(LABEL.length());
    } else if (isLabelAs(varName)) {
      return varName.substring(LABEL_AS.length());
    }
    return null;
  }

  public static boolean canBeOnAllProjects(String ref, String permissionName) {
    if (AccessSection.ALL.equals(ref)) {
      return !OWNER.equals(permissionName);
    }
    return true;
  }

  protected String name;
  protected boolean exclusiveGroup;
  protected List<PermissionRule> rules;

  protected Permission() {}

  public Permission(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public String getLabel() {
    return extractLabel(getName());
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
      for (Iterator<PermissionRule> itr = rules.iterator(); itr.hasNext(); ) {
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
    }
    return null;
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
      rules = new ArrayList<>(4);
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
    if (isLabel(a.getName())) {
      return LABEL_INDEX;
    } else if (isLabelAs(a.getName())) {
      return LABEL_AS_INDEX;
    }

    int index = NAMES_LC.indexOf(a.getName().toLowerCase());
    return 0 <= index ? index : NAMES_LC.size();
  }

  @Override
  public boolean equals(final Object obj) {
    if (!(obj instanceof Permission)) {
      return false;
    }

    final Permission other = (Permission) obj;
    if (!name.equals(other.name) || exclusiveGroup != other.exclusiveGroup) {
      return false;
    }
    return new HashSet<>(getRules()).equals(new HashSet<>(other.getRules()));
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public String toString() {
    StringBuilder bldr = new StringBuilder();
    bldr.append(name).append(" ");
    if (exclusiveGroup) {
      bldr.append("[exclusive] ");
    }
    bldr.append("[");
    Iterator<PermissionRule> it = getRules().iterator();
    while (it.hasNext()) {
      bldr.append(it.next());
      if (it.hasNext()) {
        bldr.append(", ");
      }
    }
    bldr.append("]");
    return bldr.toString();
  }
}
