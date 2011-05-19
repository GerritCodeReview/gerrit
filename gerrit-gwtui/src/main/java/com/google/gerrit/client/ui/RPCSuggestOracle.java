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

/** This class will proxy SuggestOracle requests to another SuggestOracle
 *  while keeping track of the latest request.  Any repsonse that belongs
 *  to a request which is not the latest request will be dropped to prevent
 *  invalid deliveries.
 */

public class RPCSuggestOracle extends SuggestOracle {

  private SuggestOracle oracle;
  private SuggestOracle.Request request;
  private SuggestOracle.Callback callback;
  private SuggestOracle.Callback myCallback = new SuggestOracle.Callback() {
      public void onSuggestionsReady(SuggestOracle.Request req,
            SuggestOracle.Response response) {
          if (request == req) {
            callback.onSuggestionsReady(req, response);
            request = null;
            callback = null;
          }
        }
      };


  public RPCSuggestOracle(SuggestOracle ora) {
    oracle = ora;
  }

  public void requestSuggestions(SuggestOracle.Request req,
      SuggestOracle.Callback cb) {
    request = req;
    callback = cb;
    oracle.requestSuggestions(req, myCallback);
  }

  public boolean isDisplayStringHTML() {
    return oracle.isDisplayStringHTML();
  }
}
