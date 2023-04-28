// Copyright (C) 2008 The Android Open Source Project
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
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.client.SubmitType;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Projects match a source code repository managed by Gerrit */
@AutoValue
public abstract class Project {
  /** Default submit type for new projects. */
  public static final SubmitType DEFAULT_SUBMIT_TYPE = SubmitType.MERGE_IF_NECESSARY;

  /** Default submit type for root project (All-Projects). */
  public static final SubmitType DEFAULT_ALL_PROJECTS_SUBMIT_TYPE = SubmitType.MERGE_IF_NECESSARY;

  public static NameKey nameKey(String name) {
    return new NameKey(name);
  }

  /**
   * Project name key.
   *
   * <p>This class has subclasses such as {@code AllProjectsName}, which make Guice injection more
   * convenient. Subclasses must compare equal if they have the same name, regardless of the
   * specific class. This implies that subclasses may not add additional fields.
   *
   * <p>Because of this unusual subclassing behavior, this class is not an {@code @AutoValue},
   * unlike other key types in this package. However, this is strictly an implementation detail; its
   * interface and semantics are otherwise analogous to the {@code @AutoValue} types.
   *
   * <p>This class is immutable and thread safe.
   */
  @Immutable
  public static class NameKey implements Serializable, Comparable<NameKey> {
    private static final long serialVersionUID = 1L;

    /** Parse a Project.NameKey out of a string representation. */
    public static NameKey parse(String str) {
      return nameKey(ProjectUtil.sanitizeProjectName(KeyUtil.decode(str)));
    }

    private final String name;

    protected NameKey(String name) {
      this.name = requireNonNull(name);
    }

    public String get() {
      return name;
    }

    @Override
    public final int hashCode() {
      return name.hashCode();
    }

    @Override
    public final boolean equals(Object b) {
      if (b instanceof NameKey) {
        return name.equals(((NameKey) b).get());
      }
      return false;
    }

    @Override
    public final int compareTo(NameKey o) {
      return name.compareTo(o.get());
    }

    @Override
    public final String toString() {
      return KeyUtil.encode(name);
    }
  }

  public abstract NameKey getNameKey();

  @Nullable
  public abstract String getDescription();

  public abstract ImmutableMap<BooleanProjectConfig, InheritableBoolean> getBooleanConfigs();

  /**
   * Submit type as configured in {@code project.config}.
   *
   * <p>Does not take inheritance into account, i.e. may return {@link SubmitType#INHERIT}.
   */
  public abstract SubmitType getSubmitType();

  public abstract ProjectState getState();

  /**
   * Name key of the parent project.
   *
   * <p>{@code null} if this project is the wild project, {@code null} or the name key of the wild
   * project if this project is a direct child of the wild project.
   */
  @Nullable
  public abstract NameKey getParent();

  @Nullable
  public abstract String getMaxObjectSizeLimit();

  @Nullable
  public abstract String getDefaultDashboard();

  @Nullable
  public abstract String getLocalDefaultDashboard();

  /** The {@code ObjectId} as 40 digit hex of {@code refs/meta/config}'s HEAD. */
  @Nullable
  public abstract String getConfigRefState();

  public static Builder builder(Project.NameKey nameKey) {
    Builder builder =
        new AutoValue_Project.Builder()
            .setNameKey(nameKey)
            .setSubmitType(SubmitType.MERGE_IF_NECESSARY)
            .setState(ProjectState.ACTIVE);
    ImmutableMap.Builder<BooleanProjectConfig, InheritableBoolean> booleans =
        ImmutableMap.builder();
    Arrays.stream(BooleanProjectConfig.values())
        .forEach(b -> booleans.put(b, InheritableBoolean.INHERIT));
    builder.setBooleanConfigs(booleans.build());
    return builder;
  }

  @Nullable
  public String getName() {
    return getNameKey() != null ? getNameKey().get() : null;
  }

  public InheritableBoolean getBooleanConfig(BooleanProjectConfig config) {
    return getBooleanConfigs().get(config);
  }

  /**
   * Returns the name key of the parent project.
   *
   * @param allProjectsName name key of the wild project
   * @return name key of the parent project, {@code null} if this project is the All-Projects
   *     project
   */
  @Nullable
  public Project.NameKey getParent(Project.NameKey allProjectsName) {
    if (getParent() != null) {
      return getParent();
    }

    if (getNameKey().equals(allProjectsName)) {
      return null;
    }

    return allProjectsName;
  }

  @Nullable
  public String getParentName() {
    return getParent() != null ? getParent().get() : null;
  }

  @Override
  public final String toString() {
    return Optional.ofNullable(getName()).orElse("<null>");
  }

  public abstract Builder toBuilder();

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setDescription(String description);

    public Builder setBooleanConfig(BooleanProjectConfig config, InheritableBoolean val) {
      Map<BooleanProjectConfig, InheritableBoolean> map = new HashMap<>(getBooleanConfigs());
      map.replace(config, val);
      setBooleanConfigs(ImmutableMap.copyOf(map));
      return this;
    }

    public abstract Builder setMaxObjectSizeLimit(String limit);

    public abstract Builder setSubmitType(SubmitType type);

    public abstract Builder setState(ProjectState newState);

    public abstract Builder setDefaultDashboard(String defaultDashboardId);

    public abstract Builder setLocalDefaultDashboard(String localDefaultDashboard);

    public abstract Builder setParent(NameKey n);

    public Builder setParent(String n) {
      return setParent(n != null ? nameKey(n) : null);
    }

    /** Sets the {@code ObjectId} as 40 digit hex of {@code refs/meta/config}'s HEAD. */
    public abstract Builder setConfigRefState(String state);

    public abstract Project build();

    protected abstract Builder setNameKey(Project.NameKey nameKey);

    protected abstract ImmutableMap<BooleanProjectConfig, InheritableBoolean> getBooleanConfigs();

    protected abstract Builder setBooleanConfigs(
        ImmutableMap<BooleanProjectConfig, InheritableBoolean> booleanConfigs);
  }
}
