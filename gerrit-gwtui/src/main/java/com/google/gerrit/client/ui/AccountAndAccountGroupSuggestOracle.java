// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.RpcStatus;
import com.google.gerrit.client.admin.Util;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.common.data.ReviewerInfo;
import com.google.gwt.user.client.ui.SuggestOracle;
import com.google.gwtexpui.safehtml.client.HighlightSuggestOracle;

import java.util.ArrayList;
import java.util.List;

/** Suggestion Oracle for Account and AccountGroup entities. */
public class AccountAndAccountGroupSuggestOracle extends HighlightSuggestOracle {

  @Override
  protected void onRequestSuggestions(final Request req, final Callback callback) {
    RpcStatus.hide(new Runnable() {
      public void run() {
        SuggestUtil.SVC.suggestReviewer(req.getQuery(), req.getLimit(),
            new GerritCallback<List<ReviewerInfo>>() {
              public void onSuccess(final List<ReviewerInfo> result) {
                final List<AccountOrAccountGroupSuggestion> r =
                    new ArrayList<AccountOrAccountGroupSuggestion>(result
                        .size());
                for (final ReviewerInfo reviewer : result) {
                  r.add(new AccountOrAccountGroupSuggestion(reviewer));
                }
                callback.onSuggestionsReady(req, new Response(r));
              }
            });
      }
    });
  }

  private static class AccountOrAccountGroupSuggestion implements
      SuggestOracle.Suggestion {
    private final ReviewerInfo reviewerInfo;

    AccountOrAccountGroupSuggestion(final ReviewerInfo reviewerInfo) {
      this.reviewerInfo = reviewerInfo;
    }

    public String getDisplayString() {
      final AccountInfo accountInfo = reviewerInfo.getAccountInfo();
      if (accountInfo != null) {
        return FormatUtil.nameEmail(accountInfo);
      } else {
        return reviewerInfo.getGroup().getName() + " ("
            + Util.C.suggestedGroupLabel() + ")";
      }
    }

    public String getReplacementString() {
      final AccountInfo accountInfo = reviewerInfo.getAccountInfo();
      if (accountInfo != null) {
        return FormatUtil.nameEmail(accountInfo);
      } else {
        return reviewerInfo.getGroup().getName();
      }
    }
  }
}
