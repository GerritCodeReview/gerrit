// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.plugin.client.ui;

import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.ui.HighlightSuggestion;
import com.google.gerrit.plugin.client.rpc.RestApi;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.SuggestOracle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A {@code SuggestOracle} for groups. */
public class GroupSuggestOracle extends SuggestOracle {

  private final int chars;

  /** @param chars minimum chars to start suggesting. */
  public GroupSuggestOracle(int chars) {
    this.chars = chars;
  }

  @Override
  public boolean isDisplayStringHTML() {
    return true;
  }

  @Override
  public void requestSuggestions(final Request req, final Callback done) {
    if (req.getQuery().length() < chars) {
      responseEmptySuggestion(req, done);
      return;
    }
    RestApi rest = new RestApi("/groups/").addParameter("suggest", req.getQuery());
    if (req.getLimit() > 0) {
      rest.addParameter("n", req.getLimit());
    }
    rest.get(
        new AsyncCallback<NativeMap<JavaScriptObject>>() {
          @Override
          public void onSuccess(NativeMap<JavaScriptObject> result) {
            List<String> keys = result.sortedKeys();
            List<Suggestion> suggestions = new ArrayList<>(keys.size());
            for (String g : keys) {
              suggestions.add(new HighlightSuggestion(req.getQuery(), g));
            }
            done.onSuggestionsReady(req, new Response(suggestions));
          }

          @Override
          public void onFailure(Throwable caught) {
            responseEmptySuggestion(req, done);
          }
        });
  }

  private static void responseEmptySuggestion(Request req, Callback done) {
    List<Suggestion> empty = Collections.emptyList();
    done.onSuggestionsReady(req, new Response(empty));
  }
}
