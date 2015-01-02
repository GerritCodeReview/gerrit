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
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * A collection of static methods which work on the Gerrit REST API for specific
 * files in a change.
 */
public class ChangeFileApi {
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

  /** Get the contents of a File in a PatchSet or change edit. */
  public static void getContent(PatchSet.Id id, String filename,
      AsyncCallback<FileContent> cb) {
    contentEditOrPs(id, filename).get(decode(cb));
  }

  /**
   * Get the contents of a file or commit message in a PatchSet or change
   * edit.
   **/
  public static void getContentOrMessage(PatchSet.Id id, String path,
      AsyncCallback<FileContent> cb) {
    RestApi api = (Patch.COMMIT_MSG.equals(path) && id.get() == 0)
        ? messageEdit(id)
        : contentEditOrPs(id, path);
    api.getWithResponse(decode(cb));
  }

  /** Put contents into a File in a change edit. */
  public static void putContent(PatchSet.Id id, String filename,
      String content, GerritCallback<VoidResult> result) {
    contentEdit(id.getParentKey(), filename).put(content, result);
  }

  /** Put contents into a File or commit message in a change edit. */
  public static void putContentOrMessage(PatchSet.Id id, String path,
      String content, GerritCallback<VoidResult> result) {
    if (Patch.COMMIT_MSG.equals(path)) {
      putMessage(id, content, result);
    } else {
      contentEdit(id.getParentKey(), path).put(content, result);
    }
  }

  /** Put message into a change edit. */
  private static void putMessage(PatchSet.Id id, String m,
      GerritCallback<VoidResult> r) {
    putMessage(id.getParentKey(), m, r);
  }

  /** Put message into a change edit. */
  public static void putMessage(Change.Id id, String m,
      GerritCallback<VoidResult> r) {
    ChangeApi.change(id.get()).view("edit:message").put(m, r);
  }

  /** Restore contents of a File in a change edit. */
  public static void restoreContent(PatchSet.Id id, String filename,
      AsyncCallback<VoidResult> result) {
    Input in = Input.create();
    in.restore_path(filename);
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

  private static RestApi messageEdit(PatchSet.Id id) {
    return ChangeApi.change(id.getParentKey().get()).view("edit:message");
  }

  private static RestApi contentEdit(Change.Id id, String filename) {
    return ChangeApi.edit(id.get()).id(filename);
  }

  private static native String b64decode(String a) /*-{ return window.atob(a); }-*/;

  private static class Input extends JavaScriptObject {
    final native void restore_path(String p) /*-{ if(p)this.restore_path=p; }-*/;

    static Input create() {
      return (Input) createObject();
    }

    protected Input() {
    }
  }
}
