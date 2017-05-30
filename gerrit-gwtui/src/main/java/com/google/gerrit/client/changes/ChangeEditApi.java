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
import com.google.gerrit.client.editor.EditFileInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.HttpCallback;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.rpc.AsyncCallback;

/** REST API helpers to remotely edit a change. */
public class ChangeEditApi {
  /** Get file (or commit message) contents. */
  public static void get(
      @Nullable Project.NameKey project,
      PatchSet.Id id,
      String path,
      boolean base,
      HttpCallback<NativeString> cb) {
    RestApi api;
    if (id.get() != 0) {
      // Read from a published revision, when change edit doesn't
      // exist for the caller, or is not currently active.
      api =
          ChangeApi.revision(Project.NameKey.asStringOrNull(project), id)
              .view("files")
              .id(path)
              .view("content");
    } else if (Patch.COMMIT_MSG.equals(path)) {
      api =
          editMessage(Project.NameKey.asStringOrNull(project), id.getParentKey().get())
              .addParameter("base", base);
    } else {
      api =
          editFile(Project.NameKey.asStringOrNull(project), id.getParentKey().get(), path)
              .addParameter("base", base);
    }
    api.get(cb);
  }

  /** Get file (or commit message) contents of the edit. */
  public static void get(
      @Nullable Project.NameKey project,
      PatchSet.Id id,
      String path,
      HttpCallback<NativeString> cb) {
    get(project, id, path, false, cb);
  }

  /** Get meta info for change edit. */
  public static void getMeta(
      @Nullable String project, PatchSet.Id id, String path, AsyncCallback<EditFileInfo> cb) {
    if (id.get() != 0) {
      throw new IllegalStateException("only supported for edits");
    }
    editFile(project, id.getParentKey().get(), path).view("meta").get(cb);
  }

  /** Put message into a change edit. */
  public static void putMessage(
      @Nullable String project, int id, String m, GerritCallback<VoidResult> cb) {
    editMessage(project, id).put(m, cb);
  }

  /** Put contents into a file or commit message in a change edit. */
  public static void put(
      @Nullable String project,
      int id,
      String path,
      String content,
      GerritCallback<VoidResult> cb) {
    if (Patch.COMMIT_MSG.equals(path)) {
      putMessage(project, id, content, cb);
    } else {
      editFile(project, id, path).put(content, cb);
    }
  }

  /** Delete a file in the pending edit. */
  public static void delete(
      @Nullable String project, int id, String path, AsyncCallback<VoidResult> cb) {
    editFile(project, id, path).delete(cb);
  }

  /** Rename a file in the pending edit. */
  public static void rename(
      @Nullable String project, int id, String path, String newPath, AsyncCallback<VoidResult> cb) {
    Input in = Input.create();
    in.oldPath(path);
    in.newPath(newPath);
    ChangeApi.edit(project, id).post(in, cb);
  }

  /** Restore (undo delete/modify) a file in the pending edit. */
  public static void restore(
      @Nullable String project, int id, String path, AsyncCallback<VoidResult> cb) {
    Input in = Input.create();
    in.restorePath(path);
    ChangeApi.edit(project, id).post(in, cb);
  }

  private static RestApi editMessage(@Nullable String project, int id) {
    return ChangeApi.change(project, id).view("edit:message");
  }

  private static RestApi editFile(@Nullable String project, int id, String path) {
    return ChangeApi.edit(project, id).id(path);
  }

  private static class Input extends JavaScriptObject {
    static Input create() {
      return createObject().cast();
    }

    final native void restorePath(String p) /*-{ this.restore_path=p }-*/;

    final native void oldPath(String p) /*-{ this.old_path=p }-*/;

    final native void newPath(String p) /*-{ this.new_path=p }-*/;

    protected Input() {}
  }
}
