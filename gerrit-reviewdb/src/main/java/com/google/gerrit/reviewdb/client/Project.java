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

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.StringKey;

/** Projects match a source code repository managed by Gerrit */
public final class Project {
  /** Project name key */
  public static class NameKey extends
      StringKey<com.google.gwtorm.client.Key<?>>{
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected String name;

    protected NameKey() {
    }

    public NameKey(final String n) {
      name = n;
    }

    @Override
    public String get() {
      return name;
    }

    @Override
    protected void set(String newValue) {
      name = newValue;
    }

    @Override
    public int hashCode() {
      return get().hashCode();
    }

    @Override
    public boolean equals(Object b) {
      if (b instanceof NameKey) {
        return get().equals(((NameKey) b).get());
      }
      return false;
    }

    /** Parse a Project.NameKey out of a string representation. */
    public static NameKey parse(final String str) {
      final NameKey r = new NameKey();
      r.fromString(str);
      return r;
    }
  }

  public static enum SubmitType {
    FAST_FORWARD_ONLY,

    MERGE_IF_NECESSARY,

    REBASE_IF_NECESSARY,

    MERGE_ALWAYS,

    CHERRY_PICK;
  }

  public static enum State {
    ACTIVE,

    READ_ONLY,

    HIDDEN;
  }

  public static enum InheritedBoolean {
    TRUE,
    FALSE,
    INHERIT;
  }

  protected NameKey name;

  protected String description;

  protected InheritedBoolean useContributorAgreements;

  protected InheritedBoolean useSignedOffBy;

  protected SubmitType submitType;

  protected State state;

  protected NameKey parent;

  protected InheritedBoolean requireChangeID;

  protected InheritedBoolean useContentMerge;

  protected String defaultDashboardId;

  protected String localDefaultDashboardId;

  protected Project() {
  }

  public Project(Project.NameKey nameKey) {
    name = nameKey;
    submitType = SubmitType.MERGE_IF_NECESSARY;
    state = State.ACTIVE;
    useContributorAgreements = InheritedBoolean.INHERIT;
    useSignedOffBy = InheritedBoolean.INHERIT;
    requireChangeID = InheritedBoolean.INHERIT;
    useContentMerge = InheritedBoolean.INHERIT;
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

  public void setDescription(final String d) {
    description = d;
  }

  public InheritedBoolean getUseContributorAgreements() {
    return useContributorAgreements;
  }

  public InheritedBoolean getUseSignedOffBy() {
    return useSignedOffBy;
  }

  public InheritedBoolean getUseContentMerge() {
    return useContentMerge;
  }

  public InheritedBoolean getRequireChangeID() {
    return requireChangeID;
  }

  public void setUseContributorAgreements(final InheritedBoolean u) {
    useContributorAgreements = u;
  }

  public void setUseSignedOffBy(final InheritedBoolean sbo) {
    useSignedOffBy = sbo;
  }

  public void setUseContentMerge(final InheritedBoolean cm) {
    useContentMerge = cm;
  }

  public void setRequireChangeID(final InheritedBoolean cid) {
    requireChangeID = cid;
  }

  public SubmitType getSubmitType() {
    return submitType;
  }

  public void setSubmitType(final SubmitType type) {
    submitType = type;
  }

  public State getState() {
    return state;
  }

  public void setState(final State newState) {
    state = newState;
  }

  public String getDefaultDashboard() {
    return defaultDashboardId;
  }

  public void setDefaultDashboard(final String defaultDashboardId) {
    this.defaultDashboardId = defaultDashboardId;
  }

  public String getLocalDefaultDashboard() {
    return localDefaultDashboardId;
  }

  public void setLocalDefaultDashboard(final String localDefaultDashboardId) {
    this.localDefaultDashboardId = localDefaultDashboardId;
  }

  public void copySettingsFrom(final Project update) {
    description = update.description;
    useContributorAgreements = update.useContributorAgreements;
    useSignedOffBy = update.useSignedOffBy;
    useContentMerge = update.useContentMerge;
    requireChangeID = update.requireChangeID;
    submitType = update.submitType;
    state = update.state;
  }

  /**
   * Returns the name key of the parent project.
   *
   * @return name key of the parent project, <code>null</code> if this project
   *         is the wild project, <code>null</code> or the name key of the wild
   *         project if this project is a direct child of the wild project
   */
  public Project.NameKey getParent() {
    return parent;
  }

  /**
   * Returns the name key of the parent project.
   *
   * @param allProjectsName name key of the wild project
   * @return name key of the parent project, <code>null</code> if this project
   *         is the wild project
   */
  public Project.NameKey getParent(final Project.NameKey allProjectsName) {
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
    parent = n != null ? new NameKey(n) : null;
  }

  public void setParentName(NameKey n) {
    parent = n;
  }
}
