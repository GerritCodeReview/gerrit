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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * A collection of static methods which work on the Gerrit REST API for specific
 * files in a change.
 */
public class ChangeFileApi {
  static abstract class CallbackWrapper<I, O> implements AsyncCallback<I> {
    protected AsyncCallback<O> wrapped;

    public CallbackWrapper(AsyncCallback<O> callback) {
      wrapped = callback;
    }

    @Override
    public abstract void onSuccess(I result);

    @Override
    public void onFailure(Throwable caught) {
      wrapped.onFailure(caught);
    }
  }

  private static CallbackWrapper<NativeString, String> wrapper(
      AsyncCallback<String> cb) {
    return new CallbackWrapper<NativeString, String>(cb) {
      @Override
      public void onSuccess(NativeString b64) {
        if (b64 != null) {
          wrapped.onSuccess(b64decode(b64.asString()));
        }
      }
    };
  }

  /** Get the contents of a File in a PatchSet or cange edit. */
  public static void getContent(PatchSet.Id id, String filename,
      AsyncCallback<String> cb) {
    contentEditOrPs(id, filename).get(wrapper(cb));
  }

  /** Get commit message in a PatchSet or cange edit. */
  public static void getMessage(PatchSet.Id id, AsyncCallback<String> cb) {
    ChangeApi.change(id.getParentKey().get()).view("edit:message").get(
        wrapper(cb));
  }

  /** Put contents into a File in a change edit. */
  public static void putContent(PatchSet.Id id, String filename,
      String content, AsyncCallback<VoidResult> result) {
    contentEdit(id.getParentKey(), filename).put(content, result);
  }

  /** Put message into a change edit. */
  public static void putMessage(PatchSet.Id id, String m,
      AsyncCallback<VoidResult> r) {
    ChangeApi.change(id.getParentKey().get()).view("edit:message").put(m, r);
  }

  /** Restore contents of a File in a change edit. */
  public static void restoreContent(PatchSet.Id id, String filename,
      AsyncCallback<VoidResult> result) {
    Input in = Input.create();
    in.path(filename);
    in.restore(true);
    ChangeApi.edit(id.getParentKey().get()).post(in, result);
  }

  /** Delete a file from a change edit. */
  public static void deleteContent(PatchSet.Id id, String filename,
      AsyncCallback<VoidResult> result) {
    contentEdit(id.getParentKey(), filename).delete(result);
  }

  private static RestApi contentEditOrPs(PatchSet.Id id, String filename) {
    return id.get() == 0
        ? contentEdit(id.getParentKey(), filename)
        : ChangeApi.revision(id).view("files").id(filename).view("content");
  }

  private static RestApi contentEdit(Change.Id id, String filename) {
    return ChangeApi.edit(id.get()).id(filename);
  }

  private static native String b64decode(String a) /*-{ return window.atob(a); }-*/;

  private static class Input extends JavaScriptObject {
    final native void path(String p) /*-{ if(p)this.path=p; }-*/;
    final native void restore(boolean r) /*-{ if(r)this.restore=r; }-*/;

    static Input create() {
      return (Input) createObject();
    }

    protected Input() {
    }
  }
}
