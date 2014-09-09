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


import com.google.gerrit.client.ui.SuggestAfterTypingNCharsOracle;
import com.google.gerrit.reviewdb.client.Change;

public class RestHashtagsSuggestOracle extends SuggestAfterTypingNCharsOracle{

  private Change.Id changeId;

  @Override
  protected void _onRequestSuggestions(Request request, Callback done) {
//TODO
  }

  public void setChange(Change.Id changeId) {
    this.changeId = changeId;
  }
}
