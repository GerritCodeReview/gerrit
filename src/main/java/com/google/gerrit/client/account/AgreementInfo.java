// Copyright 2008 Google Inc.
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
import com.google.gerrit.client.reviewdb.ContributorAgreement;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgreementInfo {
  protected AccountInfoCache accounts;
  protected List<AccountAgreement> accepted;
  protected Map<ContributorAgreement.Id, ContributorAgreement> agreements;

  public AgreementInfo() {
  }

  public void load(final Account.Id me, final ReviewDb db) throws OrmException {
    final AccountInfoCacheFactory acc = new AccountInfoCacheFactory(db);

    accepted = db.accountAgreements().byAccount(me).toList();
    agreements = new HashMap<ContributorAgreement.Id, ContributorAgreement>();
    for (final AccountAgreement a : accepted) {
      acc.want(a.getReviewedBy());
      if (!agreements.containsKey(a.getAgreementId())) {
        agreements.put(a.getAgreementId(), db.contributorAgreements().get(
            a.getAgreementId()));
      }
    }

    accounts = acc.create();
  }
}
