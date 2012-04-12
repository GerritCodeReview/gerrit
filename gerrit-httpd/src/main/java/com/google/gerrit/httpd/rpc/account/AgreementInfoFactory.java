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
import com.google.gerrit.common.data.AgreementInfo;
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.data.PermissionRule.Action;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class AgreementInfoFactory extends Handler<AgreementInfo> {
  interface Factory {
    AgreementInfoFactory create();
  }

  private final ReviewDb db;
  private final GroupCache groupCache;
  private final IdentifiedUser user;
  private final ProjectCache projectCache;

  private AgreementInfo info;

  @Inject
  AgreementInfoFactory(final ReviewDb db, final GroupCache groupCache,
      final IdentifiedUser user, final ProjectCache projectCache) {
    this.db = db;
    this.groupCache = groupCache;
    this.user = user;
    this.projectCache = projectCache;
  }

  @Override
  public AgreementInfo call() throws Exception {
    List<String> accepted = Lists.newArrayList();
    Map<String, ContributorAgreement> agreements = Maps.newHashMap();
    Collection<ContributorAgreement> cas =
        projectCache.getAllProjects().getConfig().getContributorAgreements();
    for (ContributorAgreement ca : cas) {
      agreements.put(ca.getName(), ca.forUi());

      List<AccountGroup.UUID> groupIds = Lists.newArrayList();
      for (PermissionRule rule : ca.getAccepted()) {
        if (rule.getAction() == Action.ALLOW) {
          groupIds.add(new AccountGroup.UUID(rule.getGroup().getUUID().get()));
        }
      }
      if (user.getEffectiveGroups().containsAnyOf(groupIds)) {
        accepted.add(ca.getName());
      }
    }

    info = new AgreementInfo();
    info.setAccepted(accepted);
    info.setAgreements(agreements);
    return info;
  }
}
