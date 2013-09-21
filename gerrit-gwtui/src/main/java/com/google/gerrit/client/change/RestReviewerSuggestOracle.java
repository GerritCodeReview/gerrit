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

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.account.AccountInfo;
import com.google.gerrit.client.admin.Util;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.groups.GroupBaseInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.SuggestAfterTypingNCharsOracle;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.ui.SuggestOracle;

import java.util.ArrayList;
import java.util.List;

/** REST API based suggestion Oracle for reviewers. */
public class RestReviewerSuggestOracle extends SuggestAfterTypingNCharsOracle {

  private Change.Id changeId;

  @Override
  protected void _onRequestSuggestions(final Request req, final Callback callback) {
    ChangeApi.suggestReviewers(changeId.get(), req.getQuery(),
        req.getLimit()).get(new GerritCallback<JsArray<SuggestReviewerInfo>>() {
          @Override
          public void onSuccess(JsArray<SuggestReviewerInfo> result) {
            final List<RestReviewerSuggestion> r =
                new ArrayList<RestReviewerSuggestion>(result.length());
            for (final SuggestReviewerInfo reviewer : Natives.asList(result)) {
              r.add(new RestReviewerSuggestion(reviewer));
            }
            callback.onSuggestionsReady(req, new Response(r));
          }
        });
  }

  public void setChange(Change.Id changeId) {
    this.changeId = changeId;
  }

  private static class RestReviewerSuggestion implements SuggestOracle.Suggestion {
    private final SuggestReviewerInfo reviewer;

    RestReviewerSuggestion(final SuggestReviewerInfo reviewer) {
      this.reviewer = reviewer;
    }

    public String getDisplayString() {
      if (reviewer.account() != null) {
        return FormatUtil.nameEmail(reviewer.account());
      }
      return reviewer.group().name()
          + " ("
          + Util.C.suggestedGroupLabel()
          + ")";
    }

    public String getReplacementString() {
      if (reviewer.account() != null) {
        return FormatUtil.nameEmail(reviewer.account());
      }
      return reviewer.group().name();
    }
  }

  public static class SuggestReviewerInfo extends JavaScriptObject {
    public final native AccountInfo account() /*-{ return this.account; }-*/;
    public final native GroupBaseInfo group() /*-{ return this.group; }-*/;
    protected SuggestReviewerInfo() {
    }
  }
}
