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

import com.google.gerrit.reviewdb.client.Project;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Portion of a {@link Project} describing access rules. */
public class AccessSection extends RefConfigSection implements Comparable<AccessSection> {
  /** Special name given to the global capabilities; not a valid reference. */
  public static final String GLOBAL_CAPABILITIES = "GLOBAL_CAPABILITIES";

  protected List<Permission> permissions;

  protected AccessSection() {}

  public AccessSection(String refPattern) {
    super(refPattern);
  }

  public List<Permission> getPermissions() {
    if (permissions == null) {
      permissions = new ArrayList<>();
    }
    return permissions;
  }

  public void setPermissions(List<Permission> list) {
    Set<String> names = new HashSet<>();
    for (Permission p : list) {
      if (!names.add(p.getName().toLowerCase())) {
        throw new IllegalArgumentException();
      }
    }

    permissions = list;
  }

  public Permission getPermission(String name) {
    return getPermission(name, false);
  }

  public Permission getPermission(String name, boolean create) {
    for (Permission p : getPermissions()) {
      if (p.getName().equalsIgnoreCase(name)) {
        return p;
      }
    }

    if (create) {
      Permission p = new Permission(name);
      permissions.add(p);
      return p;
    }

    return null;
  }

  public void addPermission(Permission p) {
    getPermissions().add(p);
  }

  public void remove(Permission permission) {
    if (permission != null) {
      removePermission(permission.getName());
    }
  }

  public void removePermission(String name) {
    if (permissions != null) {
      for (Iterator<Permission> itr = permissions.iterator(); itr.hasNext(); ) {
        if (name.equalsIgnoreCase(itr.next().getName())) {
          itr.remove();
        }
      }
    }
  }

  public void mergeFrom(AccessSection section) {
    for (Permission src : section.getPermissions()) {
      Permission dst = getPermission(src.getName());
      if (dst != null) {
        dst.mergeFrom(src);
      } else {
        permissions.add(src);
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
    return new HashSet<>(getPermissions())
        .equals(new HashSet<>(((AccessSection) obj).getPermissions()));
  }
}
