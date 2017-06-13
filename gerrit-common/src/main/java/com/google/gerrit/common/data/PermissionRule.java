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

public class PermissionRule implements Comparable<PermissionRule> {
  public static final String FORCE_PUSH = "Force Push";
  public static final String FORCE_EDIT = "Force Edit";

  public enum Action {
    ALLOW,
    DENY,
    BLOCK,

    INTERACTIVE,
    BATCH
  }

  protected Action action = Action.ALLOW;
  protected boolean force;
  protected int min;
  protected int max;
  protected GroupReference group;

  public PermissionRule() {}

  public PermissionRule(GroupReference group) {
    this.group = group;
  }

  public Action getAction() {
    return action;
  }

  public void setAction(Action action) {
    if (action == null) {
      throw new NullPointerException("action");
    }
    this.action = action;
  }

  public boolean isDeny() {
    return action == Action.DENY;
  }

  public void setDeny() {
    action = Action.DENY;
  }

  public boolean isBlock() {
    return action == Action.BLOCK;
  }

  public void setBlock() {
    action = Action.BLOCK;
  }

  public Boolean getForce() {
    return force;
  }

  public void setForce(Boolean newForce) {
    force = newForce;
  }

  public Integer getMin() {
    return min;
  }

  public void setMin(Integer min) {
    this.min = min;
  }

  public void setMax(Integer max) {
    this.max = max;
  }

  public Integer getMax() {
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

  void mergeFrom(PermissionRule src) {
    if (getAction() != src.getAction()) {
      if (getAction() == Action.BLOCK || src.getAction() == Action.BLOCK) {
        setAction(Action.BLOCK);

      } else if (getAction() == Action.DENY || src.getAction() == Action.DENY) {
        setAction(Action.DENY);

      } else if (getAction() == Action.BATCH || src.getAction() == Action.BATCH) {
        setAction(Action.BATCH);
      }
    }

    setForce(getForce() || src.getForce());
    setRange(Math.min(getMin(), src.getMin()), Math.max(getMax(), src.getMax()));
  }

  @Override
  public int compareTo(PermissionRule o) {
    int cmp = action(this) - action(o);
    if (cmp == 0) {
      cmp = range(o) - range(this);
    }
    if (cmp == 0) {
      cmp = group(this).compareTo(group(o));
    }
    return cmp;
  }

  private static int action(PermissionRule a) {
    switch (a.getAction()) {
      case DENY:
        return 0;
      case ALLOW:
      case BATCH:
      case BLOCK:
      case INTERACTIVE:
      default:
        return 1 + a.getAction().ordinal();
    }
  }

  private static int range(PermissionRule a) {
    return Math.abs(a.getMin()) + Math.abs(a.getMax());
  }

  private static String group(PermissionRule a) {
    return a.getGroup().getName() != null ? a.getGroup().getName() : "";
  }

  @Override
  public String toString() {
    return asString(true);
  }

  public String asString(boolean canUseRange) {
    StringBuilder r = new StringBuilder();

    switch (getAction()) {
      case ALLOW:
        break;

      case DENY:
        r.append("deny ");
        break;

      case BLOCK:
        r.append("block ");
        break;

      case INTERACTIVE:
        r.append("interactive ");
        break;

      case BATCH:
        r.append("batch ");
        break;
    }

    if (getForce()) {
      r.append("+force ");
    }

    if (canUseRange && (getMin() != 0 || getMax() != 0)) {
      if (0 <= getMin()) {
        r.append('+');
      }
      r.append(getMin());
      r.append("..");
      if (0 <= getMax()) {
        r.append('+');
      }
      r.append(getMax());
      r.append(' ');
    }

    r.append("group ");
    r.append(getGroup().getName());

    return r.toString();
  }

  public static PermissionRule fromString(String src, boolean mightUseRange) {
    final String orig = src;
    final PermissionRule rule = new PermissionRule();

    src = src.trim();

    if (src.startsWith("deny ")) {
      rule.setAction(Action.DENY);
      src = src.substring("deny ".length()).trim();

    } else if (src.startsWith("block ")) {
      rule.setAction(Action.BLOCK);
      src = src.substring("block ".length()).trim();

    } else if (src.startsWith("interactive ")) {
      rule.setAction(Action.INTERACTIVE);
      src = src.substring("interactive ".length()).trim();

    } else if (src.startsWith("batch ")) {
      rule.setAction(Action.BATCH);
      src = src.substring("batch ".length()).trim();
    }

    if (src.startsWith("+force ")) {
      rule.setForce(true);
      src = src.substring("+force ".length()).trim();
    }

    if (mightUseRange && !src.startsWith("group ")) {
      int sp = src.indexOf(' ');
      String range = src.substring(0, sp);

      if (range.matches("^([+-]?\\d+)\\.\\.([+-]?\\d+)$")) {
        int dotdot = range.indexOf("..");
        int min = parseInt(range.substring(0, dotdot));
        int max = parseInt(range.substring(dotdot + 2));
        rule.setRange(min, max);
      } else {
        throw new IllegalArgumentException("Invalid range in rule: " + orig);
      }

      src = src.substring(sp + 1).trim();
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

  public boolean hasRange() {
    return (!(getMin() == null || getMin() == 0)) || (!(getMax() == null || getMax() == 0));
  }

  public static int parseInt(String value) {
    if (value.startsWith("+")) {
      value = value.substring(1);
    }
    return Integer.parseInt(value);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof PermissionRule)) {
      return false;
    }
    final PermissionRule other = (PermissionRule) obj;
    return action.equals(other.action)
        && force == other.force
        && min == other.min
        && max == other.max
        && group.equals(other.group);
  }

  @Override
  public int hashCode() {
    return group.hashCode();
  }
}
