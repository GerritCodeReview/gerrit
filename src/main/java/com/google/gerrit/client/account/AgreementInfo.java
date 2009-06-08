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

package com.google.gerrit.client.account;

import com.google.gerrit.client.data.AccountInfoCache;
import com.google.gerrit.client.data.AccountInfoCacheFactory;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountAgreement;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.AccountGroupAgreement;
import com.google.gerrit.client.reviewdb.ContributorAgreement;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.Common;
import com.google.gwtorm.client.OrmException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgreementInfo {
  protected AccountInfoCache accounts;
  protected List<AccountAgreement> userAccepted;
  protected List<AccountGroupAgreement> groupAccepted;
  protected Map<ContributorAgreement.Id, ContributorAgreement> agreements;

  public AgreementInfo() {
  }

  public void load(final Account.Id me, final ReviewDb db) throws OrmException {
    final AccountInfoCacheFactory acc = new AccountInfoCacheFactory(db);

    userAccepted = db.accountAgreements().byAccount(me).toList();
    groupAccepted = new ArrayList<AccountGroupAgreement>();
    for (final AccountGroup.Id groupId : Common.getGroupCache()
        .getEffectiveGroups(me)) {
      groupAccepted.addAll(db.accountGroupAgreements().byGroup(groupId)
          .toList());
    }

    agreements = new HashMap<ContributorAgreement.Id, ContributorAgreement>();
    for (final AccountAgreement a : userAccepted) {
      acc.want(a.getReviewedBy());
      if (!agreements.containsKey(a.getAgreementId())) {
        agreements.put(a.getAgreementId(), db.contributorAgreements().get(
            a.getAgreementId()));
      }
    }
    for (final AccountGroupAgreement a : groupAccepted) {
      acc.want(a.getReviewedBy());
      if (!agreements.containsKey(a.getAgreementId())) {
        agreements.put(a.getAgreementId(), db.contributorAgreements().get(
            a.getAgreementId()));
      }
    }

    accounts = acc.create();
  }
}
