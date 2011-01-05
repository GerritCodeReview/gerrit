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
  protected boolean deny;
  protected boolean force;
  protected int min;
  protected int max;
  protected GroupReference group;

  public PermissionRule() {
  }

  public PermissionRule(GroupReference group) {
    this.group = group;
  }

  public boolean getDeny() {
    return deny;
  }

  public void setDeny(boolean newDeny) {
    deny = newDeny;
  }

  public boolean getForce() {
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
  public int compareTo(PermissionRule o) {
    int cmp = deny(this) - deny(o);
    if (cmp == 0) cmp = group(this).compareTo(group(o));
    return cmp;
  }

  private static int deny(PermissionRule a) {
    return a.getDeny() ? 1 : 0;
  }

  private static String group(PermissionRule a) {
    return a.getGroup().getName() != null ? a.getGroup().getName() : "";
  }

  @Override
  public String toString() {
    return asString(true);
  }

  public String asString(boolean useRange) {
    StringBuilder r = new StringBuilder();

    if (getDeny()) {
      r.append("deny ");
    }

    if (getForce()) {
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

  public static PermissionRule fromString(String src, boolean useRange) {
    final String orig = src;
    final PermissionRule rule = new PermissionRule();

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
