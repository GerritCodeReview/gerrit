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
// limitations under the License

package com.google.gerrit.server.project;

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Project.SubmitType;

import java.util.List;

public class CreateProjectArgs {

  private String projectName;
  private List<AccountGroup.UUID> ownerIds;
  private String newParent;
  private String projectDescription;
  private SubmitType submitType;
  private boolean contributorAgreements;
  private boolean signedOffBy;
  private boolean permissionsOnly;
  private String branch;
  private boolean contentMerge;
  private boolean changeIdRequired;
  private boolean createEmptyCommit;

  public CreateProjectArgs() {
  }

  public String getProjectName() {
    return projectName;
  }

  public void setProjectName(String projectName) {
    this.projectName = projectName;
  }

  public List<AccountGroup.UUID> getOwnerIds() {
    return ownerIds;
  }

  public void setOwnerIds(List<AccountGroup.UUID> ownerIds) {
    this.ownerIds = ownerIds;
  }

  public String getNewParent() {
    return newParent;
  }

  public void setNewParent(String newParent) {
    this.newParent = newParent;
  }

  public String getProjectDescription() {
    return projectDescription;
  }

  public void setProjectDescription(String projectDescription) {
    this.projectDescription = projectDescription;
  }

  public SubmitType getSubmitType() {
    return submitType;
  }

  public void setSubmitType(SubmitType submitType) {
    this.submitType = submitType;
  }

  public boolean isContributorAgreements() {
    return contributorAgreements;
  }

  public void setContributorAgreements(boolean contributorAgreements) {
    this.contributorAgreements = contributorAgreements;
  }

  public boolean isSignedOffBy() {
    return signedOffBy;
  }

  public void setSignedOffBy(boolean signedOffBy) {
    this.signedOffBy = signedOffBy;
  }

  public boolean isPermissionsOnly() {
    return permissionsOnly;
  }

  public void setPermissionsOnly(boolean permissionsOnly) {
    this.permissionsOnly = permissionsOnly;
  }

  public String getBranch() {
    return branch;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }

  public boolean isContentMerge() {
    return contentMerge;
  }

  public void setContentMerge(boolean contentMerge) {
    this.contentMerge = contentMerge;
  }

  public boolean isChangeIdRequired() {
    return changeIdRequired;
  }

  public void setChangeIdRequired(boolean changeIdRequired) {
    this.changeIdRequired = changeIdRequired;
  }

  public boolean shouldCreateEmptyCommit() {
    return createEmptyCommit;
  }

  public void setCreateEmptyCommit(boolean createEmptyCommit) {
    this.createEmptyCommit = createEmptyCommit;
  }
}
