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

package com.google.gerrit.client.change;

import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.info.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtexpui.safehtml.client.HighlightSuggestOracle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class PathSuggestOracle extends HighlightSuggestOracle {

  private final Change.Id changeId;
  private final RevisionInfo revision;

  PathSuggestOracle(Change.Id changeId, RevisionInfo revision) {
    this.changeId = changeId;
    this.revision = revision;
  }

  @Override
  protected void onRequestSuggestions(final Request req, final Callback cb) {
    ChangeApi.revision(changeId.get(), revision.name())
        .view("files")
        .addParameter("q", req.getQuery())
        .background()
        .get(
            new AsyncCallback<JsArrayString>() {
              @Override
              public void onSuccess(JsArrayString result) {
                List<Suggestion> r = new ArrayList<>();
                for (String path : Natives.asList(result)) {
                  r.add(new PathSuggestion(path));
                }
                cb.onSuggestionsReady(req, new Response(r));
              }

              @Override
              public void onFailure(Throwable caught) {
                List<Suggestion> none = Collections.emptyList();
                cb.onSuggestionsReady(req, new Response(none));
              }
            });
  }

  private static class PathSuggestion implements Suggestion {
    private final String path;

    PathSuggestion(String path) {
      this.path = path;
    }

    @Override
    public String getDisplayString() {
      return path;
    }

    @Override
    public String getReplacementString() {
      return path;
    }
  }
}
