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
import com.google.gerrit.client.info.AccountInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.ui.SuggestOracle;
import java.util.ArrayList;
import java.util.List;

/** Suggestion Oracle for Account entities. */
public class AccountSuggestOracle extends SuggestAfterTypingNCharsOracle {
  @Override
  public void _onRequestSuggestions(Request req, Callback cb) {
    AccountApi.suggest(
        req.getQuery(),
        req.getLimit(),
        new GerritCallback<JsArray<AccountInfo>>() {
          @Override
          public void onSuccess(JsArray<AccountInfo> in) {
            List<AccountSuggestion> r = new ArrayList<>(in.length());
            for (AccountInfo p : Natives.asList(in)) {
              r.add(new AccountSuggestion(p, req.getQuery()));
            }
            cb.onSuggestionsReady(req, new Response(r));
          }
        });
  }

  public static class AccountSuggestion implements SuggestOracle.Suggestion {
    private final String suggestion;

    public AccountSuggestion(AccountInfo info, String query) {
      this.suggestion = format(info, query);
    }

    @Override
    public String getDisplayString() {
      return suggestion;
    }

    @Override
    public String getReplacementString() {
      return suggestion;
    }

    public static String format(AccountInfo info, String query) {
      String s = FormatUtil.nameEmail(info);
      if (query != null && !containsQuery(s, query) && info.secondaryEmails() != null) {
        for (String email : Natives.asList(info.secondaryEmails())) {
          AccountInfo info2 =
              AccountInfo.create(info._accountId(), info.name(), email, info.username());
          String s2 = FormatUtil.nameEmail(info2);
          if (containsQuery(s2, query)) {
            s = s2;
            break;
          }
        }
      }
      return s;
    }

    private static boolean containsQuery(String s, String query) {
      for (String qterm : query.split("\\s+")) {
        if (!s.toLowerCase().contains(qterm.toLowerCase())) {
          return false;
        }
      }
      return true;
    }
  }
}
