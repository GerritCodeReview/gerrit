// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.client.admin.AdminConstants;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.info.AccountInfo;
import com.google.gerrit.client.info.GroupBaseInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.AccountSuggestOracle;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwtexpui.safehtml.client.HighlightSuggestOracle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** REST API based suggestion Oracle for reviewers. */
public class ReviewerSuggestOracle extends HighlightSuggestOracle {
  private Change.Id changeId;

  @Override
  protected void onRequestSuggestions(Request req, Callback cb) {
    ChangeApi.suggestReviewers(changeId.get(), req.getQuery(), req.getLimit(), false)
        .get(
            new GerritCallback<JsArray<SuggestReviewerInfo>>() {
              @Override
              public void onSuccess(JsArray<SuggestReviewerInfo> result) {
                List<RestReviewerSuggestion> r = new ArrayList<>(result.length());
                for (SuggestReviewerInfo reviewer : Natives.asList(result)) {
                  r.add(new RestReviewerSuggestion(reviewer, req.getQuery()));
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

  @Override
  public void requestDefaultSuggestions(Request req, Callback cb) {
    requestSuggestions(req, cb);
  }

  public void setChange(Change.Id changeId) {
    this.changeId = changeId;
  }

  public static class RestReviewerSuggestion implements Suggestion {
    private final String displayString;
    private final String replacementString;

    RestReviewerSuggestion(SuggestReviewerInfo reviewer, String query) {
      if (reviewer.account() != null) {
        this.replacementString =
            AccountSuggestOracle.AccountSuggestion.format(reviewer.account(), query);
        this.displayString = replacementString;
      } else {
        this.replacementString = reviewer.group().name();
        this.displayString =
            replacementString + " (" + AdminConstants.I.suggestedGroupLabel() + ")";
      }
    }

    @Override
    public String getDisplayString() {
      return displayString;
    }

    @Override
    public String getReplacementString() {
      return replacementString;
    }
  }

  public static class SuggestReviewerInfo extends JavaScriptObject {
    public final native AccountInfo account() /*-{ return this.account; }-*/;

    public final native GroupBaseInfo group() /*-{ return this.group; }-*/;

    protected SuggestReviewerInfo() {}
  }
}
