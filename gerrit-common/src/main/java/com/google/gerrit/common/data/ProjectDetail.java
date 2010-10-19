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

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Project;

import java.util.List;
import java.util.Map;

public class ProjectDetail {
  public Project project;
  public Map<AccountGroup.Id, AccountGroup> groups;
  public List<InheritedRefRight> rights;
  public boolean canModifyDescription;
  public boolean canModifyMergeType;
  public boolean canModifyAgreements;
  public boolean canModifyAccess;
  public boolean canChangeParent;

  public ProjectDetail() {
  }

  public void setProject(final Project p) {
    project = p;
  }

  public void setGroups(final Map<AccountGroup.Id, AccountGroup> g) {
    groups = g;
  }

  public void setRights(final List<InheritedRefRight> r) {
    rights = r;
  }

  public void setCanModifyDescription(final boolean cmd) {
    canModifyDescription = cmd;
  }

  public void setCanModifyMergeType(final boolean cmmt) {
    canModifyMergeType = cmmt;
  }

  public void setCanModifyAgreements(final boolean cma) {
    canModifyAgreements = cma;
  }

  public void setCanModifyAccess(final boolean cma) {
    canModifyAccess = cma;
  }

  public void setCanChangeParent(final boolean ccp) {
    canChangeParent = ccp;
  }
}
