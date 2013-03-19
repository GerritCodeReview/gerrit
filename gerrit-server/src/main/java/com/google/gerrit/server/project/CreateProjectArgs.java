// Copyright (C) 2011 The Android Open Source Project
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
// limitations under the License

package com.google.gerrit.server.project;

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.InheritableBoolean;
import com.google.gerrit.reviewdb.client.Project.SubmitType;

import java.util.List;

public class CreateProjectArgs {

  private Project.NameKey projectName;
  public ProjectControl template;
  public List<AccountGroup.UUID> ownerIds;
  public ProjectControl newParent;
  public String projectDescription;
  public SubmitType submitType;
  public InheritableBoolean contributorAgreements;
  public InheritableBoolean signedOffBy;
  public boolean permissionsOnly;
  public List<String> branch;
  public InheritableBoolean contentMerge;
  public InheritableBoolean changeIdRequired;
  public boolean createEmptyCommit;

  public CreateProjectArgs() {
    contributorAgreements = InheritableBoolean.INHERIT;
    signedOffBy = InheritableBoolean.INHERIT;
    contentMerge = InheritableBoolean.INHERIT;
    changeIdRequired = InheritableBoolean.INHERIT;
    submitType = SubmitType.MERGE_IF_NECESSARY;
  }

  public Project.NameKey getProject() {
    return projectName;
  }

  public String getProjectName() {
    return projectName != null ? projectName.get() : null;
  }

  public void setProjectName(String n) {
    projectName = n != null ? new Project.NameKey(n) : null;
  }

  public void setProjectName(Project.NameKey n) {
    projectName = n;
  }
}
