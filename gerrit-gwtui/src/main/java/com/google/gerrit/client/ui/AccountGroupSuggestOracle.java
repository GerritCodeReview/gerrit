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
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.user.client.ui.SuggestOracle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Suggestion Oracle for AccountGroup entities. */
public class AccountGroupSuggestOracle extends SuggestAfterTypingNCharsOracle {
  private Map<String, AccountGroup.UUID> priorResults = new HashMap<>();

  private Project.NameKey projectName;

  @Override
  public void _onRequestSuggestions(final Request req, final Callback callback) {
    RpcStatus.hide(new Runnable() {
      @Override
      public void run() {
        SuggestUtil.SVC.suggestAccountGroupForProject(
            projectName, req.getQuery(), req.getLimit(),
            new GerritCallback<List<GroupReference>>() {
              @Override
              public void onSuccess(final List<GroupReference> result) {
                priorResults.clear();
                final ArrayList<AccountGroupSuggestion> r =
                    new ArrayList<>(result.size());
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

  public void setProject(Project.NameKey projectName) {
    this.projectName = projectName;
  }

  private static class AccountGroupSuggestion implements
      SuggestOracle.Suggestion {
    private final GroupReference info;

    AccountGroupSuggestion(final GroupReference k) {
      info = k;
    }

    @Override
    public String getDisplayString() {
      return info.getName();
    }

    @Override
    public String getReplacementString() {
      return info.getName();
    }
  }

  /** @return the group UUID, or null if it cannot be found. */
  public AccountGroup.UUID getUUID(String name) {
    return priorResults.get(name);
  }
}
