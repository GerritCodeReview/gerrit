// Copyright (C) 2014 The Android Open Source Project
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
import com.google.gerrit.client.changes.HashTagApi;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.SuggestAfterTypingNCharsOracle;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.ui.SuggestOracle;

import java.util.ArrayList;
import java.util.List;

public class RestHashtagsSuggestOracle extends SuggestAfterTypingNCharsOracle{

  private int accountId;

  @Override
  protected  void _onRequestSuggestions(final Request request, final Callback done) {
    //TODO sven.selberg Implemet rest endpoint and enable autocompletion for hashtag adding.
//    HashTagApi.suggestHashtags(accountId, request.getQuery(),
//        request.getLimit()).get(new GerritCallback<JsArray<SuggestHashtagInfo>>() {
//          @Override
//          public void onSuccess(JsArray<SuggestHashtagInfo> result) {
//            final List<RestHashtagSuggestion> r =
//                new ArrayList<>(result.length());
//            for (final SuggestHashtagInfo hashtag : Natives.asList(result)) {
//              r.add(new RestHashtagSuggestion(hashtag));
//            }
//            done.onSuggestionsReady(request, new Response(r));
//          }
//        });
  }
  public void setAccountId(int accontId) {
    this.accountId = accontId;
  }
  private static class RestHashtagSuggestion implements SuggestOracle.Suggestion {
    private final SuggestHashtagInfo hashtag;

    RestHashtagSuggestion(final SuggestHashtagInfo hashtag) {
      this.hashtag = hashtag;
    }

    public String getDisplayString() {
      if (hashtag.hashtag() != null) {
        return hashtag.hashtag();
      } else {
        return "";
      }
    }

    @Override
    public String getReplacementString() {
      if (hashtag.hashtag() != null) {
        return hashtag.hashtag();
      } else {
        return "";
      }
    }
  }

  public static class SuggestHashtagInfo extends JavaScriptObject {
    public final native String hashtag() /*-{ return this.hashtag; }-*/;
    protected SuggestHashtagInfo() {
    }
  }
}
