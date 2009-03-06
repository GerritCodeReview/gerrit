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

import com.google.gerrit.client.RpcStatus;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gwt.user.client.ui.SuggestOracle;
import com.google.gwtexpui.safehtml.client.HighlightSuggestOracle;

import java.util.ArrayList;
import java.util.List;

/** Suggestion Oracle for AccountGroup entities. */
public class AccountGroupSuggestOracle extends HighlightSuggestOracle {
  @Override
  public void onRequestSuggestions(final Request req, final Callback callback) {
    RpcStatus.hide(new Runnable() {
      public void run() {
        SuggestUtil.SVC.suggestAccountGroup(req.getQuery(), req.getLimit(),
            new GerritCallback<List<AccountGroup>>() {
              public void onSuccess(final List<AccountGroup> result) {
                final ArrayList<AccountGroupSuggestion> r =
                    new ArrayList<AccountGroupSuggestion>(result.size());
                for (final AccountGroup p : result) {
                  r.add(new AccountGroupSuggestion(p));
                }
                callback.onSuggestionsReady(req, new Response(r));
              }
            });
      }
    });
  }

  private static class AccountGroupSuggestion implements
      SuggestOracle.Suggestion {
    private final AccountGroup info;

    AccountGroupSuggestion(final AccountGroup k) {
      info = k;
    }

    public String getDisplayString() {
      return info.getName();
    }

    public String getReplacementString() {
      return info.getName();
    }
  }
}
