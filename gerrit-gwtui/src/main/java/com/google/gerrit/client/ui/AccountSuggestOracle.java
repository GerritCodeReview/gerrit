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
import com.google.gerrit.client.RpcStatus;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.common.data.AccountInfo;
import com.google.gwt.user.client.ui.SuggestOracle;

import java.util.ArrayList;
import java.util.List;

/** Suggestion Oracle for Account entities. */
public class AccountSuggestOracle extends SuggestAfterTypingNCharsOracle {
  @Override
  public void _onRequestSuggestions(final Request req, final Callback callback) {
    RpcStatus.hide(new Runnable() {
      public void run() {
        SuggestUtil.SVC.suggestAccount(req.getQuery(), Boolean.TRUE,
            req.getLimit(),
            new GerritCallback<List<AccountInfo>>() {
              public void onSuccess(final List<AccountInfo> result) {
                final ArrayList<AccountSuggestion> r =
                    new ArrayList<AccountSuggestion>(result.size());
                for (final AccountInfo p : result) {
                  r.add(new AccountSuggestion(p));
                }
                callback.onSuggestionsReady(req, new Response(r));
              }
            });
      }
    });
  }

  private static class AccountSuggestion implements SuggestOracle.Suggestion {
    private final AccountInfo info;

    AccountSuggestion(final AccountInfo k) {
      info = k;
    }

    public String getDisplayString() {
      return FormatUtil.nameEmail(FormatUtil.asInfo(info));
    }

    public String getReplacementString() {
      return FormatUtil.nameEmail(FormatUtil.asInfo(info));
    }
  }
}
