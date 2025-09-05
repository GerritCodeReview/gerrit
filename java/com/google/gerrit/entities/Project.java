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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.gerrit.common.ConvertibleToProto;
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
    return new GeneralProjectName(name);
  }

  /**
   * Project name key.
   *
   * <p>This interface has a few implementations. Callers should normally only interact with the
   * interface directly, and create an instance using {@link #nameKey}.
   *
   * <p>The main implementation is {@code GeneralProjectName}, which supports any project name.
   *
   * <p>Other implementations are specific for reserved repo names, such as {@code AllProjectsName}.
   * Having them as separate types makes the Guice injection more convenient. Implementors must
   * compare equal if they have the same name, regardless of the specific class. This implies that
   * implementors may not add additional fields besides `name`, and that they must override {@code
   * equals} and {@code hashCode} to use the {@link #projectNameEquals} and {@link
   * #projectNameHashCode} methods provided by this interface. All implementors must be immutable
   * and ThreadSafe, please use {@code record} for new implementors.
   *
   * <p>Why was it implemented this way? We needed the implementations to be distinguished record
   * types. As records do not support inheritance, the cleanest way to share the common behavior
   * between them was this interface. Interfaces cannot override {@link Object}methods, and
   * therefore the implementors must wrap these on their own.
   */
  @Immutable
  @ConvertibleToProto
  public interface NameKey extends Serializable, Comparable<NameKey> {
    long serialVersionUID = 1L;

    /** Parse a Project.NameKey out of a string representation. */
    static NameKey parse(String str) {
      return nameKey(ProjectUtil.sanitizeProjectName(KeyUtil.decode(str)));
    }

    String name();

    default String get() {
      return name();
    }

    default int projectNameHashCode() {
      return name().hashCode();
    }

    default boolean projectNameEquals(Object b) {
      if (b instanceof NameKey) {
        return name().equals(((NameKey) b).get());
      }
      return false;
    }

    default String projectNameToString() {
      return KeyUtil.encode(name());
    }

    @Override
    default int compareTo(NameKey o) {
      return name().compareTo(o.get());
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

    @CanIgnoreReturnValue
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

    @CanIgnoreReturnValue
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
