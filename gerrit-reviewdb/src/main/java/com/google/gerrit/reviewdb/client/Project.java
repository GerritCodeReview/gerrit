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

import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.StringKey;

/** Projects match a source code repository managed by Gerrit */
public final class Project {
  /** Project name key */
  public static class NameKey extends StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected String name;

    protected NameKey() {}

    public NameKey(String n) {
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
    public static NameKey parse(String str) {
      final NameKey r = new NameKey();
      r.fromString(str);
      return r;
    }
  }

  protected NameKey name;

  protected String description;

  protected InheritableBoolean useContributorAgreements;

  protected InheritableBoolean useSignedOffBy;

  protected SubmitType submitType;

  protected ProjectState state;

  protected NameKey parent;

  protected InheritableBoolean requireChangeID;

  protected String maxObjectSizeLimit;

  protected InheritableBoolean useContentMerge;

  protected String defaultDashboardId;

  protected String localDefaultDashboardId;

  protected String themeName;

  protected InheritableBoolean createNewChangeForAllNotInTarget;

  protected InheritableBoolean enableSignedPush;
  protected InheritableBoolean requireSignedPush;

  protected InheritableBoolean rejectImplicitMerges;

  protected InheritableBoolean enableReviewerByEmail;

  protected InheritableBoolean matchAuthorDateWithCommitterDate;

  protected Project() {}

  public Project(Project.NameKey nameKey) {
    name = nameKey;
    submitType = SubmitType.MERGE_IF_NECESSARY;
    state = ProjectState.ACTIVE;
    useContributorAgreements = InheritableBoolean.INHERIT;
    useSignedOffBy = InheritableBoolean.INHERIT;
    requireChangeID = InheritableBoolean.INHERIT;
    useContentMerge = InheritableBoolean.INHERIT;
    createNewChangeForAllNotInTarget = InheritableBoolean.INHERIT;
    enableSignedPush = InheritableBoolean.INHERIT;
    requireSignedPush = InheritableBoolean.INHERIT;
    enableReviewerByEmail = InheritableBoolean.INHERIT;
    matchAuthorDateWithCommitterDate = InheritableBoolean.INHERIT;
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

  public InheritableBoolean getUseContributorAgreements() {
    return useContributorAgreements;
  }

  public InheritableBoolean getUseSignedOffBy() {
    return useSignedOffBy;
  }

  public InheritableBoolean getUseContentMerge() {
    return useContentMerge;
  }

  public InheritableBoolean getRequireChangeID() {
    return requireChangeID;
  }

  public String getMaxObjectSizeLimit() {
    return maxObjectSizeLimit;
  }

  public InheritableBoolean getRejectImplicitMerges() {
    return rejectImplicitMerges;
  }

  public InheritableBoolean getEnableReviewerByEmail() {
    return enableReviewerByEmail;
  }

  public void setEnableReviewerByEmail(InheritableBoolean enable) {
    enableReviewerByEmail = enable;
  }

  public InheritableBoolean getMatchAuthorDateWithCommitterDate() {
    return matchAuthorDateWithCommitterDate;
  }

  public void setMatchAuthorDateWithCommitterDate(InheritableBoolean match) {
    matchAuthorDateWithCommitterDate = match;
  }

  public void setUseContributorAgreements(InheritableBoolean u) {
    useContributorAgreements = u;
  }

  public void setUseSignedOffBy(InheritableBoolean sbo) {
    useSignedOffBy = sbo;
  }

  public void setUseContentMerge(InheritableBoolean cm) {
    useContentMerge = cm;
  }

  public void setRequireChangeID(InheritableBoolean cid) {
    requireChangeID = cid;
  }

  public InheritableBoolean getCreateNewChangeForAllNotInTarget() {
    return createNewChangeForAllNotInTarget;
  }

  public void setCreateNewChangeForAllNotInTarget(InheritableBoolean useAllNotInTarget) {
    this.createNewChangeForAllNotInTarget = useAllNotInTarget;
  }

  public InheritableBoolean getEnableSignedPush() {
    return enableSignedPush;
  }

  public void setEnableSignedPush(InheritableBoolean enable) {
    enableSignedPush = enable;
  }

  public InheritableBoolean getRequireSignedPush() {
    return requireSignedPush;
  }

  public void setRequireSignedPush(InheritableBoolean require) {
    requireSignedPush = require;
  }

  public void setMaxObjectSizeLimit(String limit) {
    maxObjectSizeLimit = limit;
  }

  public void setRejectImplicitMerges(InheritableBoolean check) {
    rejectImplicitMerges = check;
  }

  public SubmitType getSubmitType() {
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

  public String getThemeName() {
    return themeName;
  }

  public void setThemeName(String themeName) {
    this.themeName = themeName;
  }

  public void copySettingsFrom(Project update) {
    description = update.description;
    useContributorAgreements = update.useContributorAgreements;
    useSignedOffBy = update.useSignedOffBy;
    useContentMerge = update.useContentMerge;
    requireChangeID = update.requireChangeID;
    submitType = update.submitType;
    state = update.state;
    maxObjectSizeLimit = update.maxObjectSizeLimit;
    createNewChangeForAllNotInTarget = update.createNewChangeForAllNotInTarget;
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
   * @return name key of the parent project, {@code null} if this project is the wild project
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
    parent = n != null ? new NameKey(n) : null;
  }

  public void setParentName(NameKey n) {
    parent = n;
  }
}
