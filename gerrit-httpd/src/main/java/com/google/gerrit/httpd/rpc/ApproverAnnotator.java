// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc;

import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.SuggestedReviewerInfo;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.change.SuggestReviewers.ChangeReviewerAnnotator;
import com.google.gerrit.server.change.SuggestReviewers.VisibilityControl;
import com.google.gerrit.server.project.ChangeControl;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;


public class ApproverAnnotator implements ChangeReviewerAnnotator {
  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      DynamicItem.bind(binder(), ChangeReviewerAnnotator.Factory.class)
          .to(ApproverAnnotator.Factory.class);
    }
  }

  public static class Factory implements ChangeReviewerAnnotator.Factory {
    private final AccountCache accountCache;
    private final IdentifiedUser.RequestFactory userFactory;
    private final Provider<ReviewDb> reviewDbProvider;

    @Inject
    public Factory(AccountCache accountCache,
        IdentifiedUser.RequestFactory userFactory,
        Provider<ReviewDb> reviewDbProvider) {
      this.accountCache = accountCache;
      this.userFactory = userFactory;
      this.reviewDbProvider = reviewDbProvider;
    }

    @Override
    public ChangeReviewerAnnotator create(String query, int limit,
        ChangeControl cc, VisibilityControl vc) {
      return new ApproverAnnotator(accountCache, userFactory, reviewDbProvider,
          cc);
    }
  }

  private final AccountCache accountCache;
  private final IdentifiedUser.RequestFactory userFactory;
  private final Provider<ReviewDb> reviewDbProvider;
  private final ChangeControl changeControl;

  ApproverAnnotator(AccountCache accountCache,
      IdentifiedUser.RequestFactory userFactory,
      Provider<ReviewDb> reviewDbProvider,
      ChangeControl changeControl) {
    this.accountCache = accountCache;
    this.userFactory = userFactory;
    this.reviewDbProvider = reviewDbProvider;
    this.changeControl = changeControl;
  }

  @Override
  public String getAnnotation(SuggestedReviewerInfo sri) {
    if (isApprover(sri)) {
      return " [Can Approve]";
    }
    return "";
  }

  private boolean isApprover(SuggestedReviewerInfo sri) {
    AccountInfo ai = sri.account;
    if (ai == null) {
      return false;
    }

    final IdentifiedUser reviewer = userFactory.create(
        accountCache.get(new Account.Id(ai._accountId)).getAccount().getId());
    ChangeControl reviewercc = changeControl.forUser(reviewer);

    return reviewercc.getRange(Permission.forLabel("Code-Review")).getMax() == 2;
  }
}
