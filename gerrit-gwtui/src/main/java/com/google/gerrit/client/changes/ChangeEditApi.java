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
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.HttpResponse;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * A collection of static methods which work on the Gerrit REST API for specific
 * files in a change.
 */
public class ChangeEditApi {
  private static AsyncCallback<HttpResponse<NativeString>> decode(
      final AsyncCallback<FileContent> cb) {
    return new AsyncCallback<HttpResponse<NativeString>>() {
      @Override
      public void onSuccess(HttpResponse<NativeString> in) {
        String type = in.getHeader("X-FYI-Content-Type");
        int semi = type.indexOf(';');
        if (semi >= 0) {
          type = type.substring(0, semi).trim();
        }
        String raw = b64decode(in.result().asString());
        cb.onSuccess(FileContent.create(type, raw));
      }

      @Override
      public void onFailure(Throwable e) {
        cb.onFailure(e);
      }
    };
  }

  public static class FileContent extends JavaScriptObject {
    static final native FileContent create(String c, String v) /*-{
      return {type: c, text: v}
    }-*/;

    public final native String contentType() /*-{ return this.type }-*/;
    public final native String text() /*-{ return this.text }-*/;

    protected FileContent() {
    }
  }

  /** Get file (or commit message) contents. */
  public static void get(PatchSet.Id id, String path,
      AsyncCallback<FileContent> cb) {
    RestApi api;
    if (id.get() != 0) {
      // Read from a published revision, as the edit may not exist.
      api = ChangeApi.revision(id).view("files").id(path).view("content");
    } else if (Patch.COMMIT_MSG.equals(path)) {
      api = editMessage(id.getParentKey().get());
    } else {
      api = editFile(id.getParentKey().get(), path);
    }
    api.getWithResponse(decode(cb));
  }

  /** Put message into a change edit. */
  public static void putMessage(int id, String m, GerritCallback<VoidResult> cb) {
    editMessage(id).put(m, cb);
  }

  /** Put contents into a file or commit message in a change edit. */
  public static void put(int id, String path, String content,
      GerritCallback<VoidResult> cb) {
    if (Patch.COMMIT_MSG.equals(path)) {
      putMessage(id, content, cb);
    } else {
      editFile(id, path).put(content, cb);
    }
  }

  /** Delete a file in the pending edit. */
  public static void delete(int id, String path, AsyncCallback<VoidResult> cb) {
    editFile(id, path).delete(cb);
  }

  /** Restore (undo delete/modify) a file in the pending edit. */
  public static void restore(int id, String path, AsyncCallback<VoidResult> cb) {
    Input in = Input.create();
    in.restore_path(path);
    ChangeApi.edit(id).post(in, cb);
  }

  private static RestApi editMessage(int id) {
    return ChangeApi.change(id).view("edit:message");
  }

  private static RestApi editFile(int id, String path) {
    return ChangeApi.edit(id).id(path);
  }

  private static native String b64decode(String a)
  /*-{ return window.atob(a) }-*/;

  private static class Input extends JavaScriptObject {
    static Input create() {
      return createObject().cast();
    }

    final native void restore_path(String p) /*-{ this.restore_path=p }-*/;

    protected Input() {
    }
  }
}
