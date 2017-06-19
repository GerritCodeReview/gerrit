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

import com.google.gerrit.reviewdb.client.AccountGroup;

/** Describes a group within a projects {@link AccessSection}s. */
public class GroupReference implements Comparable<GroupReference> {

  public static final String PREFIX = "group ";

  /** @return a new reference to the given group description. */
  public static GroupReference forGroup(AccountGroup group) {
    return new GroupReference(group.getGroupUUID(), group.getName());
  }

  public static GroupReference forGroup(GroupDescription.Basic group) {
    return new GroupReference(group.getGroupUUID(), group.getName());
  }

  public static boolean isGroupReference(String configValue) {
    return configValue != null && configValue.startsWith(PREFIX);
  }

  public static GroupReference fromString(String ref) {
    String name = ref.substring(ref.indexOf("[") + 1, ref.lastIndexOf("/")).trim();
    String uuid = ref.substring(ref.lastIndexOf("/") + 1, ref.lastIndexOf("]")).trim();
    return new GroupReference(new AccountGroup.UUID(uuid), name);
  }

  protected String uuid;
  protected String name;

  protected GroupReference() {}

  public GroupReference(AccountGroup.UUID uuid, String name) {
    setUUID(uuid);
    setName(name);
  }

  public AccountGroup.UUID getUUID() {
    return uuid != null ? new AccountGroup.UUID(uuid) : null;
  }

  public void setUUID(AccountGroup.UUID newUUID) {
    uuid = newUUID != null ? newUUID.get() : null;
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

  @Override
  public int hashCode() {
    return uuid(this).hashCode();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof GroupReference && compareTo((GroupReference) o) == 0;
  }

  @Override
  public String toString() {
    return "Group[" + getName() + " / " + getUUID() + "]";
  }
}
