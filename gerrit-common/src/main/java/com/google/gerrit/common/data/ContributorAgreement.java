// Copyright (C) 2012 The Android Open Source Project
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

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class ContributorAgreement implements Comparable<ContributorAgreement> {
  private String name;
  private String description;
  private boolean active = true;
  private List<PermissionRule> accepted;
  private boolean requireContactInformation;
  private GroupReference autoVerify;
  private String agreementUrl;

  protected ContributorAgreement() {
  }

  public ContributorAgreement(String name) {
    setName(name);
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public List<PermissionRule> getAccepted() {
    if (accepted == null) {
      accepted = new ArrayList<PermissionRule>();
    }
    return accepted;
  }

  public void setAccepted(List<PermissionRule> accepted) {
    this.accepted = accepted;
  }

  public boolean isRequireContactInformation() {
    return requireContactInformation;
  }

  public void setRequireContactInformation(boolean requireContactInformation) {
    this.requireContactInformation = requireContactInformation;
  }

  public GroupReference getAutoVerify() {
    return autoVerify;
  }

  public void setAutoVerify(GroupReference autoVerify) {
    this.autoVerify = autoVerify;
  }

  public String getAgreementUrl() {
    return agreementUrl;
  }

  public void setAgreementUrl(String agreementUrl) {
    this.agreementUrl = agreementUrl;
  }

  @Override
  public int compareTo(ContributorAgreement o) {
    return getName().compareTo(o.getName());
  }

  @Override
  public String toString() {
    return "ContributorAgreement[" + getName() + "]";
  }
}
