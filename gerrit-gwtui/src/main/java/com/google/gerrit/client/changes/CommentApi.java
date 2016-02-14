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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.common.data.DiffType;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.rpc.AsyncCallback;

public class CommentApi {

  public static void comments(PatchSet.Id id,
      AsyncCallback<NativeMap<JsArray<CommentInfo>>> cb) {
    comments(id, null, cb);
  }

  public static void comments(PatchSet.Id id, DiffType diffType,
      AsyncCallback<NativeMap<JsArray<CommentInfo>>> cb) {
    RestApi call = revision(id, "comments");
    addDiffType(call, diffType);
    call.get(cb);
  }

  public static void comment(PatchSet.Id id, String commentId,
      AsyncCallback<CommentInfo> cb) {
    revision(id, "comments").id(commentId).get(cb);
  }

  public static void drafts(PatchSet.Id id,
      AsyncCallback<NativeMap<JsArray<CommentInfo>>> cb) {
    drafts(id, null, cb);
  }

  public static void drafts(PatchSet.Id id, DiffType diffType,
      AsyncCallback<NativeMap<JsArray<CommentInfo>>> cb) {
    RestApi call = revision(id, "drafts");
    addDiffType(call, diffType);
    call.get(cb);
  }

  public static void draft(PatchSet.Id id, String draftId,
      AsyncCallback<CommentInfo> cb) {
    revision(id, "drafts").id(draftId).get(cb);
  }

  public static void createDraft(PatchSet.Id id, CommentInfo content,
      AsyncCallback<CommentInfo> cb) {
    createDraft(id, null, content, cb);
  }

  public static void createDraft(PatchSet.Id id, DiffType diffType,
      CommentInfo content, AsyncCallback<CommentInfo> cb) {
    RestApi call = revision(id, "drafts");
    addDiffType(call, diffType);
    call.put(content, cb);
  }

  public static void updateDraft(PatchSet.Id id, String draftId,
      CommentInfo content, AsyncCallback<CommentInfo> cb) {
    updateDraft(id, draftId, null, content, cb);
  }

  public static void updateDraft(PatchSet.Id id, String draftId,
      DiffType diffType, CommentInfo content, AsyncCallback<CommentInfo> cb) {
    RestApi call = revision(id, "drafts").id(draftId);
    addDiffType(call, diffType);
    call.put(content, cb);
  }

  public static void deleteDraft(PatchSet.Id id, String draftId,
      AsyncCallback<JavaScriptObject> cb) {
    revision(id, "drafts").id(draftId).delete(cb);
  }

  private static RestApi revision(PatchSet.Id id, String type) {
    return revision(id, type, null);
  }

  private static RestApi revision(PatchSet.Id id, String type,
      DiffType diffType) {
    RestApi call = ChangeApi.revision(id).view(type);
    addDiffType(call, diffType);
    return call;
  }

  private static void addDiffType(RestApi call, DiffType diffType) {
    if (diffType != null) {
      call.addParameter("diff-type", diffType);
    }
  }

  private CommentApi() {
  }
}
