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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.rpc.GerritCallback;

/**
 * GerritCallback to be used on user interfaces that allow filtering to handle
 * RPC's that request filtering. The user may change the filter quickly so that
 * a response may be outdated when the client receives it. In this case the
 * response must be ignored because the responses to RCP's may come out-of-order
 * and an outdated response would overwrite the correct result which was
 * received before.
 */
public class IgnoreOutdatedFilterResultsCallbackWrapper<T> extends GerritCallback<T> {
  private final FilteredUserInterface filteredUI;
  private final String myFilter;
  private final GerritCallback<T> cb;

  public IgnoreOutdatedFilterResultsCallbackWrapper(
      final FilteredUserInterface filteredUI, final GerritCallback<T> cb) {
    this.filteredUI = filteredUI;
    this.myFilter = filteredUI.getCurrentFilter();
    this.cb = cb;
  }

  @Override
  public void onSuccess(final T result) {
    if ((myFilter == null && filteredUI.getCurrentFilter() == null)
        || (myFilter != null && myFilter.equals(filteredUI.getCurrentFilter()))) {
      cb.onSuccess(result);
    }
    // Else ignore the result, the user has already changed the filter
    // and the result is not relevant anymore. If multiple RPC's are
    // fired the results may come back out-of-order and a non-relevant
    // result could overwrite the correct result if not ignored.
  }
}
