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

package com.google.gerrit.client.change;

import com.google.gerrit.client.account.AccountApi;
import com.google.gerrit.client.info.AccountInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.AccountSuggestOracle.AccountSuggestion;
import com.google.gerrit.client.ui.SuggestAfterTypingNCharsOracle;
import com.google.gwt.core.client.JsArray;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** REST API based suggestion Oracle for assignee */
public class AssigneeSuggestOracle extends SuggestAfterTypingNCharsOracle {
  @Override
  protected void _onRequestSuggestions(Request req, Callback cb) {
    AccountApi.suggest(
        req.getQuery(),
        req.getLimit(),
        new GerritCallback<JsArray<AccountInfo>>() {
          @Override
          public void onSuccess(JsArray<AccountInfo> result) {
            List<AccountSuggestion> r = new ArrayList<>(result.length());
            for (AccountInfo reviewer : Natives.asList(result)) {
              r.add(new AccountSuggestion(reviewer, req.getQuery()));
            }
            cb.onSuggestionsReady(req, new Response(r));
          }

          @Override
          public void onFailure(Throwable err) {
            List<Suggestion> r = Collections.emptyList();
            cb.onSuggestionsReady(req, new Response(r));
          }
        });
  }
}
