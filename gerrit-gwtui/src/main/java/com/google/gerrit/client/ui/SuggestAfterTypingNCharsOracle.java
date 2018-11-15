// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.client.Gerrit;
import com.google.gwtexpui.safehtml.client.HighlightSuggestOracle;
import java.util.Collections;
import java.util.List;

/**
 * Suggest oracle that only provides suggestions if the user has typed at least as many characters
 * as configured by 'suggest.from'. If 'suggest.from' is set to 0, suggestions will always be
 * provided.
 */
public abstract class SuggestAfterTypingNCharsOracle extends HighlightSuggestOracle {

  @Override
  protected void onRequestSuggestions(Request req, Callback cb) {
    if (req.getQuery() != null && req.getQuery().length() >= Gerrit.info().suggest().from()) {
      _onRequestSuggestions(req, cb);
    } else {
      List<Suggestion> none = Collections.emptyList();
      cb.onSuggestionsReady(req, new Response(none));
    }
  }

  protected abstract void _onRequestSuggestions(Request request, Callback done);
}
