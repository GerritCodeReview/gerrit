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
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gwt.user.client.ui.SuggestOracle;
import com.google.gwtexpui.safehtml.client.HighlightSuggestOracle;

import java.util.ArrayList;
import java.util.List;

/** Suggestion Oracle for Project.NameKey entities. */
public class ProjectNameSuggestOracle extends HighlightSuggestOracle {
  @Override
  public void onRequestSuggestions(final Request req, final Callback callback) {
    RpcStatus.hide(new Runnable() {
      public void run() {
        SuggestUtil.SVC.suggestProjectNameKey(req.getQuery(), req.getLimit(),
            new GerritCallback<List<Project.NameKey>>() {
              public void onSuccess(final List<Project.NameKey> result) {
                final ArrayList<ProjectNameSuggestion> r =
                    new ArrayList<ProjectNameSuggestion>(result.size());
                for (final Project.NameKey p : result) {
                  r.add(new ProjectNameSuggestion(p));
                }
                callback.onSuggestionsReady(req, new Response(r));
              }
            });
      }
    });
  }

  private static class ProjectNameSuggestion implements
      SuggestOracle.Suggestion {
    private final Project.NameKey key;

    ProjectNameSuggestion(final Project.NameKey k) {
      key = k;
    }

    public String getDisplayString() {
      return key.get();
    }

    public String getReplacementString() {
      return key.get();
    }
  }
}
