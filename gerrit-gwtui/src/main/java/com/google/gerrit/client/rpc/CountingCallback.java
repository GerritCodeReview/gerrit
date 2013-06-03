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

package com.google.gerrit.client.rpc;

import com.google.gerrit.client.VoidResult;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.rpc.AsyncCallback;

public class CountingCallback extends GerritCallback<JavaScriptObject> {
  private final int expectedCount;
  private final AsyncCallback<VoidResult> callback;

  private int count;
  private boolean failed;

  public CountingCallback(int expectedCount, AsyncCallback<VoidResult> callback) {
    this.expectedCount = expectedCount;
    this.callback = callback;
  }

  @Override
  public void onSuccess(JavaScriptObject result) {
    count++;
    if (count == expectedCount) {
        callback.onSuccess(VoidResult.create());
    }
  }

  @Override
  public void onFailure(Throwable caught) {
    if (failed) {
      return;
    }
    failed = true;
    callback.onFailure(caught);
  }
}
