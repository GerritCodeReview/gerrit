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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Portion of a {@link Project} describing access rules. */
@AutoValue
public abstract class AccessSection implements Comparable<AccessSection> {
  /** Special name given to the global capabilities; not a valid reference. */
  public static final String GLOBAL_CAPABILITIES = "GLOBAL_CAPABILITIES";
  /** Pattern that matches all references in a project. */
  public static final String ALL = "refs/*";

  /** Pattern that matches all branches in a project. */
  public static final String HEADS = "refs/heads/*";

  /** Prefix that triggers a regular expression pattern. */
  public static final String REGEX_PREFIX = "^";

  /** Name of the access section. It could be a ref pattern or something else. */
  public abstract String getName();

  public abstract ImmutableList<Permission> getPermissions();

  public static AccessSection create(String name) {
    return builder(name).build();
  }

  public static Builder builder(String name) {
    return new AutoValue_AccessSection.Builder().setName(name).setPermissions(ImmutableList.of());
  }

  /** @return true if the name is likely to be a valid reference section name. */
  public static boolean isValidRefSectionName(String name) {
    return name.startsWith("refs/") || name.startsWith("^refs/");
  }

  @Nullable
  public Permission getPermission(String name) {
    requireNonNull(name);
    if (getPermissions() != null) {
      for (Permission p : getPermissions()) {
        if (p.getName().equalsIgnoreCase(name)) {
          return p;
        }
      }
    }
    return null;
  }

  @Override
  public final int compareTo(AccessSection o) {
    return comparePattern().compareTo(o.comparePattern());
  }

  private String comparePattern() {
    if (getName().startsWith(REGEX_PREFIX)) {
      return getName().substring(REGEX_PREFIX.length());
    }
    return getName();
  }

  @Override
  public final String toString() {
    return "AccessSection[" + getName() + "]";
  }

  public Builder toBuilder() {
    Builder b = autoToBuilder();
    b.setPermissionBuilders(
        b.getPermissions().stream().map(Permission::toBuilder).collect(toImmutableList()));
    return b;
  }

  protected abstract Builder autoToBuilder();

  @AutoValue.Builder
  public abstract static class Builder {
    private final List<Permission.Builder> permissionBuilders;

    public Builder() {
      permissionBuilders = new ArrayList<>();
    }

    public abstract Builder setName(String name);

    public abstract String getName();

    public List<Permission.Builder> getPermissionBuilders() {
      return permissionBuilders;
    }

    public Builder setPermissionBuilders(ImmutableList<Permission.Builder> permissions) {
      permissionBuilders.clear();
      permissionBuilders.addAll(permissions);
      return this;
    }

    public Builder addPermission(Permission.Builder permission) {
      if (permission == null) {
        throw new IllegalArgumentException("permission must be non-null");
      }

      for (Permission.Builder p : getPermissionBuilders()) {
        if (p.getName().equalsIgnoreCase(permission.getName())) {
          throw new IllegalArgumentException();
        }
      }

      return setPermissionBuilders(
          ImmutableList.<Permission.Builder>builder()
              .addAll(getPermissionBuilders())
              .add(permission)
              .build());
    }

    public Builder remove(Permission.Builder permission) {
      if (permission == null) {
        throw new IllegalArgumentException("permission must be non-null");
      }
      return removePermission(permission.getName());
    }

    public Builder removePermission(String name) {
      if (name == null) {
        throw new IllegalArgumentException("permission name must be non-null");
      }

      return setPermissionBuilders(
          getPermissionBuilders().stream()
              .filter(p -> !name.equalsIgnoreCase(p.getName()))
              .collect(toImmutableList()));
    }

    public Permission.Builder getPermission(String permissionName) {
      if (permissionName == null) {
        throw new IllegalArgumentException("permission name must be non-null");
      }

      Optional<Permission.Builder> maybePermission =
          getPermissionBuilders().stream()
              .filter(p -> p.getName().equalsIgnoreCase(permissionName))
              .findAny();
      if (maybePermission.isPresent()) {
        return maybePermission.get();
      }

      Permission.Builder permission = Permission.builder(permissionName);
      addPermission(permission);
      return permission;
    }

    public AccessSection build() {
      setPermissions(
          permissionBuilders.stream().map(Permission.Builder::build).collect(toImmutableList()));
      if (getPermissions().size()
          > getPermissions().stream()
              .map(Permission::getName)
              .map(String::toLowerCase)
              .distinct()
              .count()) {
        throw new IllegalArgumentException("duplicate permissions: " + getPermissions());
      }
      return autoBuild();
    }

    protected abstract AccessSection autoBuild();

    protected abstract ImmutableList<Permission> getPermissions();

    protected abstract Builder setPermissions(ImmutableList<Permission> permissions);
  }
}
