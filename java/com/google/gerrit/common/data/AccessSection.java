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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Project;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Portion of a {@link Project} describing access rules. */
public class AccessSection extends RefConfigSection implements Comparable<AccessSection> {
  /** Special name given to the global capabilities; not a valid reference. */
  public static final String GLOBAL_CAPABILITIES = "GLOBAL_CAPABILITIES";

  protected Map<String, Permission> permissions;

  protected AccessSection() {}

  public AccessSection(String refPattern) {
    super(refPattern);
  }

  public List<Permission> getPermissions() {
    return Collections.unmodifiableList(
        getPermissionMap().values().stream().collect(Collectors.toList()));
  }

  public void setPermissions(List<Permission> list) {
    Map<String, Permission> incoming = new HashMap<>();
    for (Permission p : list) {
      if (incoming.containsKey(p.getName())) {
        throw new IllegalArgumentException();
      }
      incoming.put(p.getName(), p);
    }

    permissions = incoming;
  }

  @Nullable
  public Permission getPermission(String name) {
    return getPermission(name, false);
  }

  @Nullable
  public Permission getPermission(String name, boolean create) {
    Map<String, Permission> perms = getPermissionMap();
    if (perms.containsKey(name)) {
      return perms.get(name);
    }

    if (create) {
      Permission p = new Permission(name);
      perms.put(p.getName(), p);
      return p;
    }

    return null;
  }

  public void addPermission(Permission permission) {
    Map<String, Permission> perms = getPermissionMap();
    if (perms.containsKey(permission.getName())) {
      throw new IllegalArgumentException();
    }

    perms.put(permission.getName(), permission);
  }

  public void remove(Permission permission) {
    if (permission != null) {
      removePermission(permission.getName());
    }
  }

  public void removePermission(String name) {
    if (permissions != null) {
      permissions.remove(name);
    }
  }

  public void mergeFrom(AccessSection section) {
    for (Permission src : section.getPermissions()) {
      Permission dst = getPermission(src.getName());
      if (dst != null) {
        dst.mergeFrom(src);
      } else {
        getPermissionMap().put(src.getName(), src);
      }
    }
  }

  @Override
  public int compareTo(AccessSection o) {
    return comparePattern().compareTo(o.comparePattern());
  }

  private String comparePattern() {
    if (getName().startsWith(REGEX_PREFIX)) {
      return getName().substring(REGEX_PREFIX.length());
    }
    return getName();
  }

  @Override
  public String toString() {
    return "AccessSection[" + getName() + "]";
  }

  @Override
  public boolean equals(Object obj) {
    if (!super.equals(obj) || !(obj instanceof AccessSection)) {
      return false;
    }
    return permissions.equals(((AccessSection) obj).permissions);
  }

  private Map<String, Permission> getPermissionMap() {
    if (permissions == null) {
      permissions = new HashMap<>();
    }
    return permissions;
  }
}
