// Copyright (C) 2011 The Android Open Source Project
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

/** This simple SuggestOracle takes an array of SuggestOracles
 *  and proxies all requests to the currently selected oracle,
 *  which can be changed with the setIndex() method.
 */
public class SwitchingSuggestOracle extends SuggestOracle {
  private int index = 0;
  private SuggestOracle[] oracles;

  public SwitchingSuggestOracle(SuggestOracle[] oracles) {
    setOracles(oracles);
  }

  public boolean isDisplayStringHTML() {
    return getOracle().isDisplayStringHTML();
  }

  public void requestSuggestions(SuggestOracle.Request request,
    SuggestOracle.Callback callback) {
    getOracle().requestSuggestions(request, callback);
  }

  public int getIndex() {
    return index;
  }

  public void setIndex(int index) {
    this.index = index;
  }

  public SuggestOracle getOracle() {
    return getOracle(getIndex());
  }

  public SuggestOracle getOracle(int i) {
    return getOracles()[i];
  }

  public SuggestOracle[] getOracles() {
    return oracles;
  }

  public void setOracles(SuggestOracle[] oracles) {
    this.oracles = oracles;
  }
}
