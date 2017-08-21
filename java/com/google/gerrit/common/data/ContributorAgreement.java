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

import com.google.gerrit.reviewdb.client.Project;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Portion of a {@link Project} describing a single contributor agreement. */
public class ContributorAgreement implements Comparable<ContributorAgreement> {
  protected String name;
  protected String description;
  protected List<PermissionRule> accepted;
  protected GroupReference autoVerify;
  protected String agreementUrl;

  protected ContributorAgreement() {}

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

  public List<PermissionRule> getAccepted() {
    if (accepted == null) {
      accepted = new ArrayList<>();
    }
    return accepted;
  }

  public void setAccepted(List<PermissionRule> accepted) {
    this.accepted = accepted;
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

  public ContributorAgreement forUi() {
    ContributorAgreement ca = new ContributorAgreement(name);
    ca.description = description;
    ca.accepted = Collections.emptyList();
    if (autoVerify != null) {
      ca.autoVerify = new GroupReference();
    }
    ca.agreementUrl = agreementUrl;
    return ca;
  }
}
