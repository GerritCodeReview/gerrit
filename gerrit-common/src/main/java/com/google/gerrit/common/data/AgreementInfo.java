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

import com.google.gerrit.reviewdb.AccountAgreement;
import com.google.gerrit.reviewdb.AccountGroupAgreement;
import com.google.gerrit.reviewdb.ContributorAgreement;

import java.util.List;
import java.util.Map;

public class AgreementInfo {
  public List<AccountAgreement> userAccepted;
  public List<AccountGroupAgreement> groupAccepted;
  public Map<ContributorAgreement.Id, ContributorAgreement> agreements;

  public AgreementInfo() {
  }

  public void setUserAccepted(List<AccountAgreement> a) {
    userAccepted = a;
  }

  public void setGroupAccepted(List<AccountGroupAgreement> a) {
    groupAccepted = a;
  }

  public void setAgreements(Map<ContributorAgreement.Id, ContributorAgreement> a) {
    agreements = a;
  }
}
