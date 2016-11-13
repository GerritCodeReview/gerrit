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

import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.SuggestOracle;

/**
 * Delegates to a slow SuggestOracle, such as a remote server API.
 *
 * <p>A response is only supplied to the UI if no requests were made after the oracle begin that
 * request.
 *
 * <p>When a request is made while the delegate is still processing a prior request all intermediate
 * requests are discarded and the most recent request is queued. The pending request's response is
 * discarded and the most recent request is started.
 */
public class RemoteSuggestOracle extends SuggestOracle {
  private final SuggestOracle oracle;
  private Query query;
  private String last;
  private Timer requestRetentionTimer;
  private boolean cancelOutstandingRequest;

  private boolean serveSuggestions;

  public RemoteSuggestOracle(SuggestOracle src) {
    oracle = src;
  }

  public String getLast() {
    return last;
  }

  @Override
  public void requestSuggestions(Request req, Callback cb) {
    if (!serveSuggestions) {
      return;
    }

    // Use a timer for key stroke retention, such that we don't query the
    // backend for each and every keystroke we receive.
    if (requestRetentionTimer != null) {
      requestRetentionTimer.cancel();
    }
    requestRetentionTimer =
        new Timer() {
          @Override
          public void run() {
            Query q = new Query(req, cb);
            if (query == null) {
              query = q;
              q.start();
            } else {
              query = q;
            }
          }
        };
    requestRetentionTimer.schedule(200);
  }

  @Override
  public void requestDefaultSuggestions(Request req, Callback cb) {
    requestSuggestions(req, cb);
  }

  @Override
  public boolean isDisplayStringHTML() {
    return oracle.isDisplayStringHTML();
  }

  public void cancelOutstandingRequest() {
    if (requestRetentionTimer != null) {
      requestRetentionTimer.cancel();
    }
    if (query != null) {
      cancelOutstandingRequest = true;
    }
  }

  public void setServeSuggestions(boolean serveSuggestions) {
    this.serveSuggestions = serveSuggestions;
  }

  private class Query implements Callback {
    final Request request;
    final Callback callback;

    Query(Request req, Callback cb) {
      request = req;
      callback = cb;
    }

    void start() {
      oracle.requestSuggestions(request, this);
    }

    @Override
    public void onSuggestionsReady(Request req, Response res) {
      if (cancelOutstandingRequest || !serveSuggestions) {
        // If cancelOutstandingRequest() was called, we ignore this response
        cancelOutstandingRequest = false;
        query = null;
      } else if (query == this) {
        // No new request was started while this query was running.
        // Propose this request's response as the suggestions.
        query = null;
        last = request.getQuery();
        callback.onSuggestionsReady(req, res);
      } else {
        // Another query came in while this one was running. Skip
        // this response and start the most recent query.
        query.start();
      }
    }
  }
}
