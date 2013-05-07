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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * A collection of static methods which work on the Gerrit REST API for specific
 * files in a change.
 */
public class ChangeFileApi {
  public static class CallbackWrapper<I, O> implements AsyncCallback<I> {
    protected AsyncCallback<O> wrapped;

    public CallbackWrapper(AsyncCallback<O> callback) {
      wrapped = callback;
    }

    @Override
    public void onSuccess(I result) {
    }

    @Override
    public void onFailure(Throwable caught) {
      wrapped.onFailure(caught);
    }
  }

  /** Get the contents of a File in a PatchSet. */
  public static void getContent(PatchSet.Id id, String filename, 
      AsyncCallback<String> content) {  // Needs NewPatchSet to get draft
    ChangeApi.change(id.getParentKey().get()).view("revisions").id(id.get())
      .view("files").id(filename).view("content")
      .get(new CallbackWrapper<NativeString, String>(content) {
            @Override
            public void onSuccess(NativeString b64) {
              wrapped.onSuccess(b64decode(b64.asString()));
            }
          });
  }

  /** Put contents into a File in a Draft PatchSet. */
  public static void putContent(PatchSet.Id id, String filename, String content,
      AsyncCallback<VoidResult> result) {
  }

  public static native String b64decode(String a) /*-{
    return window.atob(a);
  }-*/;
}
