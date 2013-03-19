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

package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.client.InheritedBoolean;
import com.google.gerrit.reviewdb.client.Project;

public class ProjectDetail {
  public Project project;
  public boolean canModifyDescription;
  public boolean canModifyMergeType;
  public boolean canModifyAgreements;
  public boolean canModifyAccess;
  public boolean canModifyState;
  public boolean canModifyTemplate;
  public boolean isPermissionOnly;
  public InheritedBoolean useContributorAgreements;
  public InheritedBoolean useSignedOffBy;
  public InheritedBoolean useContentMerge;
  public InheritedBoolean requireChangeID;
  public InheritedBoolean isTemplate;
  public Project.NameKey parent;
  public String templateProjectNamePrefix;

  public ProjectDetail() {
  }

  public void setProject(final Project p) {
    project = p;
  }

  public void setCanModifyDescription(final boolean cmd) {
    canModifyDescription = cmd;
  }

  public void setCanModifyMergeType(final boolean cmmt) {
    canModifyMergeType = cmmt;
  }

  public void setCanModifyState(final boolean cms) {
    canModifyState = cms;
  }

  public void setCanModifyAgreements(final boolean cma) {
    canModifyAgreements = cma;
  }

  public void setCanModifyAccess(final boolean cma) {
    canModifyAccess = cma;
  }

  public void setCanModifyTemplate(final boolean cmt) {
    canModifyTemplate = cmt;
  }

  public void setPermissionOnly(final boolean ipo) {
    isPermissionOnly = ipo;
  }

  public void setUseContributorAgreements(final InheritedBoolean uca) {
    useContributorAgreements = uca;
  }

  public void setUseSignedOffBy(final InheritedBoolean usob) {
    useSignedOffBy = usob;
  }

  public void setUseContentMerge(final InheritedBoolean ucm) {
    useContentMerge = ucm;
  }

  public void setRequireChangeID(final InheritedBoolean rcid) {
    requireChangeID = rcid;
  }

  public void setIsTemplate(InheritedBoolean it) {
    this.isTemplate = it;
  }

  public void setParent(Project.NameKey p) {
    this.parent = p;
  }

  public void setTemplateProjectNamePrefix(String prefix) {
    this.templateProjectNamePrefix = prefix;
  }
}
