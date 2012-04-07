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

package com.google.gerrit.client.rpc;

import com.google.gwt.user.client.rpc.AsyncCallback;

/** Transforms a value and passes it on to another callback. */
public abstract class TransformCallback<I, O> implements AsyncCallback<I>{
  private final AsyncCallback<O> callback;

  protected TransformCallback(AsyncCallback<O> callback) {
    this.callback = callback;
  }

  @Override
  public void onSuccess(I result) {
    callback.onSuccess(transform(result));
  }

  @Override
  public void onFailure(Throwable caught) {
    callback.onFailure(caught);
  }

  protected abstract O transform(I result);
}
