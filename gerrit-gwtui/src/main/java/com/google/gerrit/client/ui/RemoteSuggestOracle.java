// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gwt.user.client.ui.SuggestOracle;

/**
 * Delegates to a slow SuggestOracle, such as a remote server API.
 * <p>
 * A response is only supplied to the UI if no requests were made after the
 * oracle begin that request.
 * <p>
 * When a request is made while the delegate is still processing a prior request
 * all intermediate requests are discarded and the most recent request is
 * queued. The pending request's response is discarded and the most recent
 * request is started.
 */
public class RemoteSuggestOracle extends SuggestOracle {
  private final SuggestOracle oracle;
  private boolean active;
  private Query query;

  public RemoteSuggestOracle(SuggestOracle src) {
    oracle = src;
  }

  @Override
  public void requestSuggestions(Request req, Callback cb) {
    query = new Query(req, cb);

    if (!active) {
      query.start();
    }
  }

  @Override
  public boolean isDisplayStringHTML() {
    return oracle.isDisplayStringHTML();
  }

  private class Query implements Callback {
    final Request request;
    final Callback callback;

    Query(Request req, Callback cb) {
      request = req;
      callback = cb;
    }

    void start() {
      active = true;
      oracle.requestSuggestions(request, this);
    }

    @Override
    public void onSuggestionsReady(Request req, Response res) {
      if (query == this) {
        // No new request was started while this query was running.
        // Propose this request's response as the suggestions.
        active = false;
        query = null;
        callback.onSuggestionsReady(req, res);
      } else {
        // Another query came in while this one was running. Skip
        // this response and start the most recent query.
        query.start();
      }
    }
  }
}
