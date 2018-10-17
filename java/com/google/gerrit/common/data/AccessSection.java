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
import com.google.gerrit.reviewdb.client.Project;
import java.util.ArrayList;
import java.util.HashSet;
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

  // TODO(ekempin): Make this method return an ImmutableList once the GWT UI is gone.
  public List<Permission> getPermissions() {
    if (permissions == null) {
      return new ArrayList<>();
    }
    return new ArrayList<>(permissions);
  }

  public void setPermissions(List<Permission> list) {
    requireNonNull(list);

    Set<String> names = new HashSet<>();
    for (Permission p : list) {
      if (!names.add(p.getName().toLowerCase())) {
        throw new IllegalArgumentException();
      }
    }

    permissions = new ArrayList<>(list);
  }

  @Nullable
  public Permission getPermission(String name) {
    return getPermission(name, false);
  }

  @Nullable
  public Permission getPermission(String name, boolean create) {
    requireNonNull(name);

    if (permissions != null) {
      for (Permission p : permissions) {
        if (p.getName().equalsIgnoreCase(name)) {
          return p;
        }
      }
    }

    if (create) {
      if (permissions == null) {
        permissions = new ArrayList<>();
      }

      Permission p = new Permission(name);
      permissions.add(p);
      return p;
    }

    return null;
  }

  public void addPermission(Permission permission) {
    requireNonNull(permission);

    if (permissions == null) {
      permissions = new ArrayList<>();
    }

    for (Permission p : permissions) {
      if (p.getName().equalsIgnoreCase(permission.getName())) {
        throw new IllegalArgumentException();
      }
    }

    permissions.add(permission);
  }

  public void remove(Permission permission) {
    requireNonNull(permission);
    removePermission(permission.getName());
  }

  public void removePermission(String name) {
    requireNonNull(name);

    if (permissions != null) {
      permissions.removeIf(permission -> name.equalsIgnoreCase(permission.getName()));
    }
  }

  public void mergeFrom(AccessSection section) {
    requireNonNull(section);

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

  @Override
  public int hashCode() {
    int hashCode = super.hashCode();
    if (permissions != null) {
      for (Permission permission : permissions) {
        hashCode += permission.hashCode();
      }
    }
    return hashCode;
  }
}
