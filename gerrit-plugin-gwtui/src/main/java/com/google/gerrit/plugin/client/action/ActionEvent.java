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

package com.google.gerrit.plugin.client.action;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.rpc.AsyncCallback;

public final class ActionEvent extends JavaScriptObject {
  /**
   * Call the remote server action.
   *
   * @param input optional JSON input. This must be an object and will be
   *        automatically serialized as JSON. Use {@code null if no input is
   *        expected, such as for {@code DELETE}.
   * @param b handler invoked with the result of the call. Due to a bug in the
   *        underlying JavaScript glue the handler is only called on success. If
   *        failure information is needed plugin implementations should use
   *        {@link com.google.gerrit.plugin.client.rpc.RestApi} directly.
   */
  public final native <I extends JavaScriptObject, O extends JavaScriptObject>
  void call(I input, AsyncCallback<O> b) /*-{
    this.call(input, $entry(function(r){
      b.@com.google.gwt.user.client.rpc.AsyncCallback::onSuccess(Ljava/lang/Object;)(r)
    }))
  }-*/;

  protected ActionEvent() {
  }
}
