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

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;

/** Describes a group within a projects {@link AccessSection}s. */
@AutoValue
public abstract class GroupReference implements Comparable<GroupReference> {

  private static final String PREFIX = "group ";

  public static GroupReference forGroup(GroupDescription.Basic group) {
    return GroupReference.create(group.getGroupUUID(), group.getName());
  }

  public static boolean isGroupReference(String configValue) {
    return configValue != null && configValue.startsWith(PREFIX);
  }

  @Nullable
  public static String extractGroupName(String configValue) {
    if (!isGroupReference(configValue)) {
      return null;
    }
    return configValue.substring(PREFIX.length()).trim();
  }

  @Nullable
  public abstract AccountGroup.UUID getUUID();

  public abstract String getName();

  /**
   * Create a group reference.
   *
   * @param uuid UUID of the group, must not be {@code null}
   * @param name the group name, must not be {@code null}
   */
  public static GroupReference create(AccountGroup.UUID uuid, String name) {
    return new AutoValue_GroupReference(requireNonNull(uuid), requireNonNull(name));
  }

  /**
   * Create a group reference where the group's name couldn't be resolved.
   *
   * @param name the group name, must not be {@code null}
   */
  public static GroupReference create(String name) {
    return new AutoValue_GroupReference(null, name);
  }

  @Override
  public final int compareTo(GroupReference o) {
    return uuid(this).compareTo(uuid(o));
  }

  private static String uuid(GroupReference a) {
    if (a.getUUID() != null && a.getUUID().get() != null) {
      return a.getUUID().get();
    }

    return "?";
  }

  @Override
  public final int hashCode() {
    return uuid(this).hashCode();
  }

  @Override
  public final boolean equals(Object o) {
    return o instanceof GroupReference && compareTo((GroupReference) o) == 0;
  }

  @Override
  public final String toString() {
    return "Group[" + getName() + " / " + getUUID() + "]";
  }

  public String toConfigValue() {
    return PREFIX + getName();
  }
}
