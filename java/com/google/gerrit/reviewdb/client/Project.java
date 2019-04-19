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

package com.google.gerrit.reviewdb.client;

import static java.util.Objects.requireNonNull;

import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.client.SubmitType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Projects match a source code repository managed by Gerrit */
public final class Project {
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
   */
  public static class NameKey implements Comparable<NameKey> {
    /** Parse a Project.NameKey out of a string representation. */
    public static NameKey parse(String str) {
      return nameKey(KeyUtil.decode(str));
    }

    public static String asStringOrNull(NameKey key) {
      return key == null ? null : key.get();
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
      return get().hashCode();
    }

    @Override
    public final boolean equals(Object b) {
      if (b instanceof NameKey) {
        return get().equals(((NameKey) b).get());
      }
      return false;
    }

    @Override
    public final int compareTo(NameKey o) {
      return get().compareTo(o.get());
    }

    @Override
    public final String toString() {
      return KeyUtil.encode(get());
    }
  }

  protected NameKey name;

  protected String description;

  protected Map<BooleanProjectConfig, InheritableBoolean> booleanConfigs;

  protected SubmitType submitType;

  protected ProjectState state;

  protected NameKey parent;

  protected String maxObjectSizeLimit;

  protected String defaultDashboardId;

  protected String localDefaultDashboardId;

  protected String configRefState;

  protected Project() {}

  public Project(Project.NameKey nameKey) {
    name = nameKey;
    submitType = SubmitType.MERGE_IF_NECESSARY;
    state = ProjectState.ACTIVE;

    booleanConfigs = new HashMap<>();
    Arrays.stream(BooleanProjectConfig.values())
        .forEach(c -> booleanConfigs.put(c, InheritableBoolean.INHERIT));
  }

  public Project.NameKey getNameKey() {
    return name;
  }

  public String getName() {
    return name != null ? name.get() : null;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String d) {
    description = d;
  }

  public String getMaxObjectSizeLimit() {
    return maxObjectSizeLimit;
  }

  public InheritableBoolean getBooleanConfig(BooleanProjectConfig config) {
    return booleanConfigs.get(config);
  }

  public void setBooleanConfig(BooleanProjectConfig config, InheritableBoolean val) {
    booleanConfigs.replace(config, val);
  }

  public void setMaxObjectSizeLimit(String limit) {
    maxObjectSizeLimit = limit;
  }

  /**
   * Submit type as configured in {@code project.config}.
   *
   * <p>Does not take inheritance into account, i.e. may return {@link SubmitType#INHERIT}.
   *
   * @return submit type.
   */
  public SubmitType getConfiguredSubmitType() {
    return submitType;
  }

  public void setSubmitType(SubmitType type) {
    submitType = type;
  }

  public ProjectState getState() {
    return state;
  }

  public void setState(ProjectState newState) {
    state = newState;
  }

  public String getDefaultDashboard() {
    return defaultDashboardId;
  }

  public void setDefaultDashboard(String defaultDashboardId) {
    this.defaultDashboardId = defaultDashboardId;
  }

  public String getLocalDefaultDashboard() {
    return localDefaultDashboardId;
  }

  public void setLocalDefaultDashboard(String localDefaultDashboardId) {
    this.localDefaultDashboardId = localDefaultDashboardId;
  }

  /**
   * Returns the name key of the parent project.
   *
   * @return name key of the parent project, {@code null} if this project is the wild project,
   *     {@code null} or the name key of the wild project if this project is a direct child of the
   *     wild project
   */
  public Project.NameKey getParent() {
    return parent;
  }

  /**
   * Returns the name key of the parent project.
   *
   * @param allProjectsName name key of the wild project
   * @return name key of the parent project, {@code null} if this project is the All-Projects
   *     project
   */
  public Project.NameKey getParent(Project.NameKey allProjectsName) {
    if (parent != null) {
      return parent;
    }

    if (name.equals(allProjectsName)) {
      return null;
    }

    return allProjectsName;
  }

  public String getParentName() {
    return parent != null ? parent.get() : null;
  }

  public void setParentName(String n) {
    parent = n != null ? nameKey(n) : null;
  }

  public void setParentName(NameKey n) {
    parent = n;
  }

  /** Returns the {@code ObjectId} as 40 digit hex of {@code refs/meta/config}'s HEAD. */
  public String getConfigRefState() {
    return configRefState;
  }

  /** Sets the {@code ObjectId} as 40 digit hex of {@code refs/meta/config}'s HEAD. */
  public void setConfigRefState(String state) {
    configRefState = state;
  }
}
