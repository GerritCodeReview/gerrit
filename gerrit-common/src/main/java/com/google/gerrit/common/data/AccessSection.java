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

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Project;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Portion of a {@link Project} describing access rules. */
public class AccessSection implements Comparable<AccessSection> {
  /** Pattern that matches all references in a project. */
  public static final String ALL = "refs/*";

  /** Pattern that matches all branches in a project. */
  public static final String HEADS = "refs/heads/*";

  /** Prefix that triggers a regular expression pattern. */
  public static final String REGEX_PREFIX = "^";

  public static boolean isAccessSection(String name) {
    return name.startsWith("refs/") || name.startsWith("^refs/");
  }

  protected String refPattern;

  protected List<Permission> permissions;

  protected AccessSection() {
  }

  public AccessSection(String refPattern) {
    this.refPattern = refPattern;
  }

  public String getRefPattern() {
    return refPattern;
  }

  public List<Permission> getPermissions() {
    if (permissions == null) {
      permissions = new ArrayList<Permission>();
    }
    return permissions;
  }

  public Permission getPermission(String name) {
    return getPermission(name, false);
  }

  public Permission getPermission(String name, boolean create) {
    for (Permission p : getPermissions()) {
      if (p.getName().equals(name)) {
        return p;
      }
    }

    if (create) {
      Permission p = new Permission(name);
      permissions.add(p);
      return p;
    } else {
      return null;
    }
  }

  public void remove(Permission permission) {
    if (permission != null) {
      removePermission(permission.getName());
    }
  }

  public void removePermission(String name) {
    if (permissions != null) {
      for (Iterator<Permission> itr = permissions.iterator(); itr.hasNext();) {
        if (name.equals(itr.next().getName())) {
          itr.remove();
        }
      }
    }
  }

  @Override
  public int compareTo(AccessSection o) {
    return getRefPattern().compareTo(o.getRefPattern());
  }

  public static class Permission implements Comparable<Permission> {
    public static final String CREATE = "create";
    public static final String FORGE_AUTHOR = "forgeAuthor";
    public static final String FORGE_COMMITTER = "forgeCommitter";
    public static final String FORGE_SERVER = "forgeServerAsCommitter";
    public static final String LABEL = "label-";
    public static final String OWNER = "owner";
    public static final String PUSH = "push";
    public static final String PUSH_TAG = "pushTag";
    public static final String READ = "read";
    public static final String SUBMIT = "submit";
    public static final String UPLOAD = "upload";

    private static final List<String> NAMES_LC;

    static {
      NAMES_LC = new ArrayList<String>();
      NAMES_LC.add(OWNER.toLowerCase());
      NAMES_LC.add(READ.toLowerCase());
      NAMES_LC.add(CREATE.toLowerCase());
      NAMES_LC.add(FORGE_AUTHOR.toLowerCase());
      NAMES_LC.add(FORGE_COMMITTER.toLowerCase());
      NAMES_LC.add(FORGE_SERVER.toLowerCase());
      NAMES_LC.add(PUSH.toLowerCase());
      NAMES_LC.add(PUSH_TAG.toLowerCase());
      NAMES_LC.add(UPLOAD.toLowerCase());
      NAMES_LC.add(LABEL.toLowerCase());
      NAMES_LC.add(SUBMIT.toLowerCase());
    }

    public static boolean isPermission(String varName) {
      String lc = varName.toLowerCase();
      if (lc.startsWith(LABEL)) {
        return LABEL.length() < lc.length();
      }
      return NAMES_LC.contains(lc);
    }

    public static boolean isLabel(String varName) {
      return varName.startsWith(LABEL) && LABEL.length() < varName.length();
    }

    public static String forLabel(String labelName) {
      return LABEL + labelName;
    }

    protected String name;

    protected boolean inherit;

    protected List<Rule> rules;

    protected Permission() {
    }

    public Permission(String name) {
      this.name = name;
      this.inherit = true;
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

    public boolean isInherit() {
      // We always inherit owner permission, otherwise project owners
      // lose access over subspaces they have delegated control to.
      //
      return inherit || OWNER.equals(getName());
    }

    public void setInherit(boolean newInherit) {
      inherit = newInherit;
    }

    public List<Rule> getRules() {
      initRules();
      return rules;
    }

    public void add(Rule rule) {
      initRules();
      rules.add(rule);
    }

    public void remove(Rule rule) {
      if (rule != null) {
        removeRule(rule.getGroup());
      }
    }

    public void removeRule(GroupReference group) {
      if (rules != null) {
        for (Iterator<Rule> itr = rules.iterator(); itr.hasNext();) {
          if (sameGroup(itr.next(), group)) {
            itr.remove();
          }
        }
      }
    }

    public Rule getRule(GroupReference group) {
      return getRule(group, false);
    }

    public Rule getRule(GroupReference group, boolean create) {
      initRules();

      for (Rule r : rules) {
        if (sameGroup(r, group)) {
          return r;
        }
      }

      if (create) {
        Rule r = new Rule(group);
        rules.add(r);
        return r;
      } else {
        return null;
      }
    }

    private static boolean sameGroup(Rule rule, GroupReference group) {
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
        rules = new ArrayList<Rule>(4);
      }
    }

    @Override
    public int compareTo(Permission b) {
      int cmp = index(this) - index(b);
      if (cmp == 0) getName().compareTo(b.getName());
      return cmp;
    }

    private static int index(Permission a) {
      String lc = a.isLabel() ? Permission.LABEL : a.getName().toLowerCase();
      int index = NAMES_LC.indexOf(lc);
      return 0 <= index ? index : NAMES_LC.size();
    }
  }

  public static class GroupReference implements Comparable<GroupReference> {
    public static GroupReference forGroup(AccountGroup group) {
      return new GroupReference(group.getGroupUUID(), group.getName());
    }

    protected AccountGroup.UUID uuid;
    protected String name;

    protected GroupReference() {
    }

    public GroupReference(AccountGroup.UUID uuid, String name) {
      this.uuid = uuid;
      this.name = name;
    }

    public AccountGroup.UUID getUUID() {
      return uuid;
    }

    public void setUUID(AccountGroup.UUID newUUID) {
      uuid = newUUID;
    }

    public String getName() {
      return name;
    }

    public void setName(String newName) {
      this.name = newName;
    }

    @Override
    public int compareTo(GroupReference o) {
      return uuid(this).compareTo(uuid(o));
    }

    private static String uuid(GroupReference a) {
      return a.getUUID() != null ? a.getUUID().get() : "?";
    }
  }

  public static class Rule implements Comparable<Rule> {
    protected boolean deny;
    protected boolean force;
    protected int min;
    protected int max;
    protected GroupReference group;

    public Rule() {
    }

    public Rule(GroupReference group) {
      this.group = group;
    }

    public boolean isDeny() {
      return deny;
    }

    public void setDeny(boolean newDeny) {
      deny = newDeny;
    }

    public boolean isForce() {
      return force;
    }

    public void setForce(boolean newForce) {
      force = newForce;
    }

    public int getMin() {
      return min;
    }

    public int getMax() {
      return max;
    }

    public void setRange(int newMin, int newMax) {
      if (newMax < newMin) {
        min = newMax;
        max = newMin;
      } else {
        min = newMin;
        max = newMax;
      }
    }

    public GroupReference getGroup() {
      return group;
    }

    public void setGroup(GroupReference newGroup) {
      group = newGroup;
    }

    @Override
    public int compareTo(Rule o) {
      int cmp = deny(this) - deny(o);
      if (cmp == 0) cmp = group(this).compareTo(group(o));
      return cmp;
    }

    private static int deny(Rule a) {
      return a.isDeny() ? 1 : 0;
    }

    private static String group(Rule a) {
      return a.getGroup().getName() != null ? a.getGroup().getName() : "";
    }

    public String asString(boolean useRange) {
      StringBuilder r = new StringBuilder();

      if (isDeny()) {
        r.append("deny ");
      }

      if (isForce()) {
        r.append("+force ");
      }

      if (useRange) {
        if (getMin() == 0 && getMax() == 1) {
        } else if (getMin() == 1 && getMax() == 1) {

        } else if (getMin() < 0 && getMax() == 0) {
          r.append(getMin());
          r.append(' ');

        } else {
          if (getMin() != getMax()) {
            if (0 <= getMin()) r.append('+');
            r.append(getMin());
            r.append("..");
          }
          if (0 <= getMax()) r.append('+');
          r.append(getMax());
          r.append(' ');
        }
      }

      r.append("group ");
      r.append(getGroup().getName());

      return r.toString();
    }

    public static Rule fromString(String src, boolean useRange) {
      final String orig = src;
      final Rule rule = new Rule();

      src = src.trim();

      if (src.startsWith("deny ")) {
        rule.setDeny(true);
        src = src.substring(5).trim();
      }

      if (src.startsWith("+force ")) {
        rule.setForce(true);
        src = src.substring("+force ".length()).trim();
      }

      if (useRange) {
        if (src.startsWith("group ")) {
          rule.setRange(0, 1);

        } else {
          int sp = src.indexOf(' ');
          String range = src.substring(0, sp);

          if (range.matches("^([+-]\\d+)\\.\\.([+-]\\d)$")) {
            int dotdot = range.indexOf("..");
            int min = parseInt(range.substring(0, dotdot));
            int max = parseInt(range.substring(dotdot + 2));
            rule.setRange(min, max);

          } else if (range.matches("^([+-]\\d)$")) {
            int v = parseInt(range);
            rule.setRange(v, v);

          } else {
            throw new IllegalArgumentException("Invalid range in rule: " + orig);
          }

          src = src.substring(sp + 1).trim();
        }
      }

      if (src.startsWith("group ")) {
        src = src.substring(6).trim();
        GroupReference group = new GroupReference();
        group.setName(src);
        rule.setGroup(group);
      } else {
        throw new IllegalArgumentException("Rule must include group: " + orig);
      }

      return rule;
    }

    private static int parseInt(String value) {
      if (value.startsWith("+")) {
        value = value.substring(1);
      }
      return Integer.parseInt(value);
    }
  }

  public static class Range implements Comparable<Range> {
    protected String name;
    protected int min;
    protected int max;

    protected Range() {
    }

    public Range(String name, int min, int max) {
      this.name = name;

      if (min <= max) {
        this.min = min;
        this.max = max;
      } else {
        this.min = max;
        this.max = min;
      }
    }

    public String getName() {
      return name;
    }

    public boolean isLabel() {
      return Permission.isLabel(getName());
    }

    public String getLabel() {
      return isLabel() ? getName().substring(Permission.LABEL.length()) : null;
    }

    public int getMin() {
      return min;
    }

    public int getMax() {
      return max;
    }

    /** True if the value is within the range. */
    public boolean contains(int value) {
      return getMin() <= value && value <= getMax();
    }

    /** Normalize the value to fit within the bounds of the range. */
    public int squash(int value) {
      return Math.min(Math.max(getMin(), value), getMax());
    }

    /** True both {@link #getMin()} and {@link #getMax()} are 0. */
    public boolean isEmpty() {
      return getMin() == 0 && getMax() == 0;
    }

    @Override
    public int compareTo(Range o) {
      return getName().compareTo(o.getName());
    }

    @Override
    public String toString() {
      StringBuilder r = new StringBuilder();
      if (getMin() < 0 && getMax() == 0) {
        r.append(getMin());
        r.append(' ');
      } else {
        if (getMin() != getMax()) {
          if (0 <= getMin()) r.append('+');
          r.append(getMin());
          r.append("..");
        }
        if (0 <= getMax()) r.append('+');
        r.append(getMax());
        r.append(' ');
      }
      return r.toString();
    }
  }
}
