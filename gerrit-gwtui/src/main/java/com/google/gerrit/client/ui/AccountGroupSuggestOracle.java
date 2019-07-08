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

import com.google.gerrit.client.groups.GroupMap;
import com.google.gerrit.client.info.GroupInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.user.client.ui.SuggestOracle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Suggestion Oracle for AccountGroup entities. */
public class AccountGroupSuggestOracle extends SuggestAfterTypingNCharsOracle {
  private Map<String, AccountGroup.UUID> priorResults = new HashMap<>();

  private Project.NameKey projectName;

  @Override
  public void _onRequestSuggestions(final Request req, final Callback callback) {
    GroupMap.suggestAccountGroupForProject(
        projectName == null ? null : projectName.get(),
        req.getQuery(),
        req.getLimit(),
        new GerritCallback<GroupMap>() {
          @Override
          public void onSuccess(GroupMap result) {
            priorResults.clear();
            ArrayList<AccountGroupSuggestion> r = new ArrayList<>(result.size());
            for (GroupInfo group : Natives.asList(result.values())) {
              r.add(new AccountGroupSuggestion(group));
              priorResults.put(group.name(), group.getGroupUUID());
            }
            callback.onSuggestionsReady(req, new Response(r));
          }
        });
  }

  public void setProject(Project.NameKey projectName) {
    this.projectName = projectName;
  }

  private static class AccountGroupSuggestion implements SuggestOracle.Suggestion {
    private final GroupInfo info;

    AccountGroupSuggestion(final GroupInfo k) {
      info = k;
    }

    @Override
    public String getDisplayString() {
      return info.name();
    }

    @Override
    public String getReplacementString() {
      return info.name();
    }
  }

  /** @return the group UUID, or null if it cannot be found. */
  public AccountGroup.UUID getUUID(String name) {
    return priorResults.get(name);
  }
}
