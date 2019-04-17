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

import static java.util.Objects.requireNonNull;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.AccountGroup;

/** Describes a group within a projects {@link AccessSection}s. */
public class GroupReference implements Comparable<GroupReference> {

  private static final String PREFIX = "group ";

  public static GroupReference forGroup(GroupDescription.Basic group) {
    return new GroupReference(group.getGroupUUID(), group.getName());
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

  protected String uuid;
  protected String name;

  protected GroupReference() {}

  /**
   * Create a group reference.
   *
   * @param uuid UUID of the group, must not be {@code null}
   * @param name the group name, must not be {@code null}
   */
  public GroupReference(AccountGroup.UUID uuid, String name) {
    setUUID(requireNonNull(uuid));
    setName(name);
  }

  /**
   * Create a group reference where the group's name couldn't be resolved.
   *
   * @param name the group name, must not be {@code null}
   */
  public GroupReference(String name) {
    setUUID(null);
    setName(name);
  }

  @Nullable
  public AccountGroup.UUID getUUID() {
    return uuid != null ? AccountGroup.uuid(uuid) : null;
  }

  public void setUUID(@Nullable AccountGroup.UUID newUUID) {
    uuid = newUUID != null ? newUUID.get() : null;
  }

  public String getName() {
    return name;
  }

  public void setName(String newName) {
    if (newName == null) {
      throw new NullPointerException();
    }
    this.name = newName;
  }

  @Override
  public int compareTo(GroupReference o) {
    return uuid(this).compareTo(uuid(o));
  }

  private static String uuid(GroupReference a) {
    if (a.getUUID() != null && a.getUUID().get() != null) {
      return a.getUUID().get();
    }

    return "?";
  }

  @Override
  public int hashCode() {
    return uuid(this).hashCode();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof GroupReference && compareTo((GroupReference) o) == 0;
  }

  public String toConfigValue() {
    return PREFIX + name;
  }

  @Override
  public String toString() {
    return "Group[" + getName() + " / " + getUUID() + "]";
  }
}
