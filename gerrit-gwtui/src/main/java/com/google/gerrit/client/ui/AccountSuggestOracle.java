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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.account.AccountApi;
import com.google.gerrit.client.account.AccountInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.ui.SuggestOracle;

import java.util.ArrayList;
import java.util.List;

/** Suggestion Oracle for Account entities. */
public class AccountSuggestOracle extends SuggestAfterTypingNCharsOracle {
  @Override
  public void _onRequestSuggestions(final Request req, final Callback cb) {
    AccountApi.suggest(req.getQuery(), req.getLimit(),
        new GerritCallback<JsArray<AccountInfo>>() {
          @Override
          public void onSuccess(JsArray<AccountInfo> in) {
            List<AccountSuggestion> r = new ArrayList<>(in.length());
            for (AccountInfo p : Natives.asList(in)) {
              r.add(new AccountSuggestion(p));
            }
            cb.onSuggestionsReady(req, new Response(r));
          }
        });
  }

  private static class AccountSuggestion implements SuggestOracle.Suggestion {
    private final AccountInfo info;

    AccountSuggestion(final AccountInfo k) {
      info = k;
    }

    @Override
    public String getDisplayString() {
      return FormatUtil.nameEmail(info);
    }

    @Override
    public String getReplacementString() {
      return FormatUtil.nameEmail(info);
    }
  }
}
