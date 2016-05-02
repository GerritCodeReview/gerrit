// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.account;

import com.google.common.base.Strings;
import com.google.gerrit.audit.AuditService;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collections;

public class PostAgreement implements RestModifyView<AccountResource, String> {
  private final ProjectCache projectCache;
  private final GroupCache groupCache;
  private final Provider<IdentifiedUser> self;
  private final ChangeHooks hooks;
  private final Provider<ReviewDb> dbProvider;
  private final AuditService auditService;
  private final AccountCache accountCache;

  @Inject
  PostAgreement(ProjectCache projectCache,
      GroupCache groupCache,
      Provider<IdentifiedUser> self,
      ChangeHooks hooks,
      Provider<ReviewDb> dbProvider,
      AuditService auditService,
      AccountCache accountCache) {
    this.projectCache = projectCache;
    this.groupCache = groupCache;
    this.self = self;
    this.hooks = hooks;
    this.dbProvider = dbProvider;
    this.auditService = auditService;
    this.accountCache = accountCache;
  }

  @Override
  public Object apply(AccountResource resource, String input)
      throws AuthException, BadRequestException, ResourceNotFoundException,
      ResourceConflictException, OrmException {
    if (self.get() != resource.getUser()) {
      throw new AuthException("not allowed to enter agreement");
    }

    String agreementName = Strings.nullToEmpty(input);
    ContributorAgreement ca = projectCache.getAllProjects().getConfig()
        .getContributorAgreement(agreementName);
    if (ca == null) {
      throw new ResourceNotFoundException();
    }

    if (ca.getAutoVerify() == null) {
      throw new BadRequestException("cannot enter a non-autoVerify agreement");
    } else if (ca.getAutoVerify().getUUID() == null) {
      throw new ResourceNotFoundException();
    }

    AccountGroup group = groupCache.get(ca.getAutoVerify().getUUID());
    if (group == null) {
      throw new ResourceNotFoundException();
    }

    Account account = self.get().getAccount();
    hooks.doClaSignupHook(account, ca.getName());

    AccountGroupMember.Key key =
        new AccountGroupMember.Key(account.getId(), group.getId());
    ReviewDb db = dbProvider.get();
    AccountGroupMember m = db.accountGroupMembers().get(key);
    if (m == null) {
      m = new AccountGroupMember(key);
      auditService.dispatchAddAccountsToGroup(account.getId(), Collections
          .singleton(m));
      db.accountGroupMembers().insert(Collections.singleton(m));
      accountCache.evict(m.getAccountId());
    }

    return agreementName;
  }

}
