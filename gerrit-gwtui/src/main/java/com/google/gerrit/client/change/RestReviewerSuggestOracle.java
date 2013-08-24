// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.change;

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.account.AccountInfo;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.SuggestAfterTypingNCharsOracle;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.ui.SuggestOracle;

import java.util.ArrayList;
import java.util.List;

/** REST API based suggestion Oracle for reviewers. */
public class RestReviewerSuggestOracle extends SuggestAfterTypingNCharsOracle {

  private Change.Id changeId;

  @Override
  protected void _onRequestSuggestions(final Request req, final Callback callback) {
    ChangeApi.suggestReviewers(changeId.get(), req.getQuery(),
        req.getLimit()).get(new GerritCallback<JsArray<AccountInfo>>() {
          @Override
          public void onSuccess(JsArray<AccountInfo> result) {
            final List<RestReviewerSuggestion> r =
                new ArrayList<RestReviewerSuggestion>(result.length());
            for (final AccountInfo account : Natives.asList(result)) {
              r.add(new RestReviewerSuggestion(account));
            }
            callback.onSuggestionsReady(req, new Response(r));
          }
        });
  }

  public void setChange(Change.Id changeId) {
    this.changeId = changeId;
  }

  private static class RestReviewerSuggestion implements SuggestOracle.Suggestion {
    private final AccountInfo accountInfo;

    RestReviewerSuggestion(final AccountInfo accountInfo) {
      this.accountInfo = accountInfo;
    }

    public String getDisplayString() {
      return FormatUtil.nameEmail(accountInfo);
    }

    public String getReplacementString() {
      return FormatUtil.nameEmail(accountInfo);
    }
  }
}
