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

package com.google.gerrit.entities;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class PermissionRule implements Comparable<PermissionRule> {
  public enum Action {
    ALLOW,
    DENY,
    BLOCK,

    INTERACTIVE,
    BATCH
  }

  public abstract Action getAction();

  public abstract boolean getForce();

  public abstract int getMin();

  public abstract int getMax();

  public abstract GroupReference getGroup();

  public static PermissionRule.Builder builder(GroupReference group) {
    return builder().setGroup(group);
  }

  public static PermissionRule create(GroupReference group) {
    return builder().setGroup(group).build();
  }

  protected static Builder builder() {
    return new AutoValue_PermissionRule.Builder()
        .setMin(0)
        .setMax(0)
        .setAction(Action.ALLOW)
        .setForce(false);
  }

  static PermissionRule merge(PermissionRule src, PermissionRule dest) {
    PermissionRule.Builder result = dest.toBuilder();
    if (dest.getAction() != src.getAction()) {
      if (dest.getAction() == Action.BLOCK || src.getAction() == Action.BLOCK) {
        result.setAction(Action.BLOCK);

      } else if (dest.getAction() == Action.DENY || src.getAction() == Action.DENY) {
        result.setAction(Action.DENY);

      } else if (dest.getAction() == Action.BATCH || src.getAction() == Action.BATCH) {
        result.setAction(Action.BATCH);
      }
    }

    result.setForce(dest.getForce() || src.getForce());
    result.setRange(Math.min(dest.getMin(), src.getMin()), Math.max(dest.getMax(), src.getMax()));
    return result.build();
  }

  public boolean isDeny() {
    return getAction() == Action.DENY;
  }

  public boolean isBlock() {
    return getAction() == Action.BLOCK;
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
  public final String toString() {
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

    r.append(getGroup().toConfigValue());

    return r.toString();
  }

  public static PermissionRule fromString(String src, boolean mightUseRange) {
    final String orig = src;
    final PermissionRule.Builder rule = PermissionRule.builder();

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

    if (mightUseRange && !GroupReference.isGroupReference(src)) {
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

    String groupName = GroupReference.extractGroupName(src);
    if (groupName != null) {
      GroupReference group = GroupReference.create(groupName);
      rule.setGroup(group);
    } else {
      throw new IllegalArgumentException("Rule must include group: " + orig);
    }

    return rule.build();
  }

  public boolean hasRange() {
    return getMin() != 0 || getMax() != 0;
  }

  public static int parseInt(String value) {
    if (value.startsWith("+")) {
      value = value.substring(1);
    }
    return Integer.parseInt(value);
  }

  public abstract Builder toBuilder();

  @AutoValue.Builder
  public abstract static class Builder {
    public Builder setDeny() {
      return setAction(Action.DENY);
    }

    public Builder setBlock() {
      return setAction(Action.BLOCK);
    }

    public Builder setRange(int newMin, int newMax) {
      if (newMax < newMin) {
        setMin(newMax);
        setMax(newMin);
      } else {
        setMin(newMin);
        setMax(newMax);
      }
      return this;
    }

    public abstract Builder setAction(Action action);

    public abstract Builder setGroup(GroupReference groupReference);

    public abstract Builder setForce(boolean newForce);

    public abstract Builder setMin(int min);

    public abstract Builder setMax(int max);

    public abstract GroupReference getGroup();

    public abstract PermissionRule build();
  }
}
