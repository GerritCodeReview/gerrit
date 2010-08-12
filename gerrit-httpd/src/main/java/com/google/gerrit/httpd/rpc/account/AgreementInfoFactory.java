// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.account;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.common.data.AgreementInfo;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.AbstractAgreement;
import com.google.gerrit.reviewdb.AccountAgreement;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupAgreement;
import com.google.gerrit.reviewdb.ContributorAgreement;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountAgreementsCache;
import com.google.gerrit.server.account.AccountGroupAgreementsCache;
import com.google.gerrit.server.util.FutureUtil;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

class AgreementInfoFactory extends Handler<AgreementInfo> {
  interface Factory {
    AgreementInfoFactory create();
  }

  private final ReviewDb db;
  private final IdentifiedUser user;
  private final AccountAgreementsCache accountAgreementsCache;
  private final AccountGroupAgreementsCache accountGroupAgreementsCache;

  private AgreementInfo info;

  @Inject
  AgreementInfoFactory(final ReviewDb db, final IdentifiedUser user,
      final AccountAgreementsCache aac, final AccountGroupAgreementsCache agac) {
    this.db = db;
    this.user = user;
    this.accountAgreementsCache = aac;
    this.accountGroupAgreementsCache = agac;
  }

  @Override
  public AgreementInfo call() throws Exception {
    Future<List<AccountAgreement>> wantUser =
        accountAgreementsCache.byAccount(user.getAccountId());

    List<ListenableFuture<List<AccountGroupAgreement>>> wantGroup =
        Lists.newArrayList();
    for (AccountGroup.Id groupId : user.getEffectiveGroups()) {
      wantGroup.add(accountGroupAgreementsCache.byGroup(groupId));
    }

    List<AccountAgreement> userAccepted = FutureUtil.getOrEmptyList(wantUser);
    List<AccountGroupAgreement> groupAccepted =
        FutureUtil.getOrEmptyList(FutureUtil.concat(wantGroup));

    Collections.sort(userAccepted, AbstractAgreement.SORT);
    Collections.sort(groupAccepted, AbstractAgreement.SORT);

    info = new AgreementInfo();
    info.setUserAccepted(userAccepted);
    info.setGroupAccepted(groupAccepted);
    info.setAgreements(agreements(userAccepted, groupAccepted));
    return info;
  }

  private Map<ContributorAgreement.Id, ContributorAgreement> agreements(
      List<AccountAgreement> userAccepted,
      List<AccountGroupAgreement> groupAccepted) throws OrmException {
    Map<ContributorAgreement.Id, ContributorAgreement> all = Maps.newHashMap();
    for (AccountAgreement a : userAccepted) {
      ContributorAgreement.Id id = a.getAgreementId();
      if (!all.containsKey(id)) {
        all.put(id, db.contributorAgreements().get(id));
      }
    }
    for (AccountGroupAgreement a : groupAccepted) {
      ContributorAgreement.Id id = a.getAgreementId();
      if (!all.containsKey(id)) {
        all.put(id, db.contributorAgreements().get(id));
      }
    }
    return all;
  }
}
