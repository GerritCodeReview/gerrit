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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

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
    for (Permission p : getPermissions()) {
      if (p.getName().equalsIgnoreCase(name)) {
        return p;
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
    b.getPermissions().stream().map(Permission::toBuilder).forEach(p -> b.addPermission(p));
    return b;
  }

  protected abstract Builder autoToBuilder();

  @AutoValue.Builder
  public abstract static class Builder {
    private final List<Permission.Builder> permissionBuilders;

    protected Builder() {
      permissionBuilders = new ArrayList<>();
    }

    public abstract Builder setName(String name);

    public abstract String getName();

    public Builder modifyPermissions(Consumer<List<Permission.Builder>> modification) {
      modification.accept(permissionBuilders);
      return this;
    }

    public Builder addPermission(Permission.Builder permission) {
      requireNonNull(permission, "permission must be non-null");
      return modifyPermissions(p -> p.add(permission));
    }

    public Builder remove(Permission.Builder permission) {
      requireNonNull(permission, "permission must be non-null");
      return removePermission(permission.getName());
    }

    public Builder removePermission(String name) {
      requireNonNull(name, "name must be non-null");
      return modifyPermissions(
          p -> p.removeIf(permissionBuilder -> name.equalsIgnoreCase(permissionBuilder.getName())));
    }

    public Permission.Builder upsertPermission(String permissionName) {
      requireNonNull(permissionName, "permissionName must be non-null");

      Optional<Permission.Builder> maybePermission =
          permissionBuilders.stream()
              .filter(p -> p.getName().equalsIgnoreCase(permissionName))
              .findAny();
      if (maybePermission.isPresent()) {
        return maybePermission.get();
      }

      Permission.Builder permission = Permission.builder(permissionName);
      modifyPermissions(p -> p.add(permission));
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

    abstract Builder setPermissions(ImmutableList<Permission> permissions);
  }
}
