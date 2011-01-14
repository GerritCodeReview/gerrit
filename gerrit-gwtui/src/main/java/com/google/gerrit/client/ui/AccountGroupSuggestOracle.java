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
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gwt.user.client.ui.SuggestOracle;
import com.google.gwtexpui.safehtml.client.HighlightSuggestOracle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Suggestion Oracle for AccountGroup entities. */
public class AccountGroupSuggestOracle extends HighlightSuggestOracle {
  private Map<String, AccountGroup.UUID> priorResults =
      new HashMap<String, AccountGroup.UUID>();

  @Override
  public void onRequestSuggestions(final Request req, final Callback callback) {
    RpcStatus.hide(new Runnable() {
      public void run() {
        SuggestUtil.SVC.suggestAccountGroup(req.getQuery(), req.getLimit(),
            new GerritCallback<List<GroupReference>>() {
              public void onSuccess(final List<GroupReference> result) {
                priorResults.clear();
                final ArrayList<AccountGroupSuggestion> r =
                    new ArrayList<AccountGroupSuggestion>(result.size());
                for (final GroupReference p : result) {
                  r.add(new AccountGroupSuggestion(p));
                  priorResults.put(p.getName(), p.getUUID());
                }
                callback.onSuggestionsReady(req, new Response(r));
              }
            });
      }
    });
  }

  private static class AccountGroupSuggestion implements
      SuggestOracle.Suggestion {
    private final GroupReference info;

    AccountGroupSuggestion(final GroupReference k) {
      info = k;
    }

    public String getDisplayString() {
      return info.getName();
    }

    public String getReplacementString() {
      return info.getName();
    }
  }

  /** @return the group UUID, or null if it cannot be found. */
  public AccountGroup.UUID getUUID(String name) {
    return priorResults.get(name);
  }
}
