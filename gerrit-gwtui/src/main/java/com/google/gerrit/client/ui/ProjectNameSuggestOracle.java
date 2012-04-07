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
import com.google.gerrit.client.projects.ProjectMap;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gwtexpui.safehtml.client.HighlightSuggestOracle;

/** Suggestion Oracle for Project.NameKey entities. */
public class ProjectNameSuggestOracle extends HighlightSuggestOracle {
  @Override
  public void onRequestSuggestions(final Request req, final Callback callback) {
    RpcStatus.hide(new Runnable() {
      @Override
      public void run() {
        ProjectMap.suggest(req.getQuery(), req.getLimit(),
            new GerritCallback<ProjectMap>() {
              @Override
              public void onSuccess(ProjectMap map) {
                callback.onSuggestionsReady(req, new Response(map.values().asList()));
              }
            });
      }
    });
  }
}
