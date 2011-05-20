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
import com.google.gerrit.common.data.GroupReference;
import com.google.gwt.user.client.ui.SuggestOracle;
import com.google.gwtexpui.safehtml.client.HighlightSuggestOracle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Suggestion Oracle for Account and AccountGroup entities. */
public class AccountAndAccountGroupSuggestOracle extends HighlightSuggestOracle {

  @Override
  protected void onRequestSuggestions(final Request req, final Callback callback) {
    RpcStatus.hide(new Runnable() {
      public void run() {
        SuggestUtil.SVC.suggestAccount(req.getQuery(), Boolean.TRUE,
            req.getLimit(), new GerritCallback<List<AccountInfo>>() {
              public void onSuccess(final List<AccountInfo> result) {
                final List<AccountOrAccountGroupSuggestion> r =
                    new ArrayList<AccountOrAccountGroupSuggestion>(result
                        .size());
                for (final AccountInfo p : result) {
                  r.add(new AccountOrAccountGroupSuggestion(p));
                }

                SuggestUtil.SVC.suggestAccountGroup(req.getQuery(),
                    req.getLimit(),
                    new GerritCallback<List<GroupReference>>() {
                      public void onSuccess(final List<GroupReference> result) {
                        for (final GroupReference groupRef : result) {
                          r.add(new AccountOrAccountGroupSuggestion(groupRef));
                        }

                        Collections.sort(r);
                        if (r.size() <= req.getLimit()) {
                          callback.onSuggestionsReady(req, new Response(r));
                        } else {
                          callback.onSuggestionsReady(req,
                              new Response(r.subList(0, req.getLimit())));
                        }
                      }
                    });
              }
            });
      }
    });
  }

  private static class AccountOrAccountGroupSuggestion implements
      SuggestOracle.Suggestion, Comparable<AccountOrAccountGroupSuggestion> {
    private final AccountInfo accountInfo;
    private final GroupReference groupReference;

    AccountOrAccountGroupSuggestion(final AccountInfo accountInfo) {
      this.accountInfo = accountInfo;
      this.groupReference = null;
    }

    AccountOrAccountGroupSuggestion(final GroupReference groupReference) {
      this.groupReference = groupReference;
      this.accountInfo = null;
    }

    public String getDisplayString() {
      if (accountInfo != null) {
        return FormatUtil.nameEmail(accountInfo);
      } else {
        return groupReference.getName() + " (" + Util.C.suggestedGroupLabel() + ")";
      }
    }

    public String getReplacementString() {
      if (accountInfo != null) {
        return FormatUtil.nameEmail(accountInfo);
      } else {
        return groupReference.getName();
      }
    }

    @Override
    public int compareTo(AccountOrAccountGroupSuggestion o) {
      return getDisplayString().compareTo(o.getDisplayString());
    }
  }
}
