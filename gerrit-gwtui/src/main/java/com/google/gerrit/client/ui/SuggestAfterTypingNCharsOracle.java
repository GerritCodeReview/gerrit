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

/**
 * Suggest oracle that only provides suggestions if the user has typed at least
 * as many characters as configured by 'suggest.from'. If 'suggest.from' is set
 * to 0, suggestions will always be provided.
 */
public abstract class SuggestAfterTypingNCharsOracle extends HighlightSuggestOracle {

  @Override
  protected void onRequestSuggestions(final Request request, final Callback done) {
    final int suggestFrom = Gerrit.getConfig().getSuggestFrom();
    if (suggestFrom == 0 || request.getQuery().length() >= suggestFrom) {
      _onRequestSuggestions(request, done);
    }
  }

  protected abstract void _onRequestSuggestions(Request request, Callback done);
}
