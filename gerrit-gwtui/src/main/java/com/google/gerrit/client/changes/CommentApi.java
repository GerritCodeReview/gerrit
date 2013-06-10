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
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.rpc.AsyncCallback;

public class CommentApi {

  public static void comments(PatchSet.Id id,
      AsyncCallback<NativeMap<JsArray<CommentInfo>>> cb) {
    getViewApi(id, "comments").get(cb);
  }

  public static void getComment(PatchSet.Id id, String commentId,
      AsyncCallback<NativeMap<JsArray<CommentInfo>>> cb) {
    getViewApi(id, "comments").id(commentId).get(cb);
  }

  public static void drafts(PatchSet.Id id,
      AsyncCallback<NativeMap<JsArray<CommentInfo>>> cb) {
    getViewApi(id, "drafts").get(cb);
  }

  public static void getDraft(PatchSet.Id id, String draftId,
      AsyncCallback<NativeMap<JsArray<CommentInfo>>> cb) {
    getViewApi(id, "drafts").id(draftId).get(cb);
  }

  public static void createDraft(PatchSet.Id id, CommentInfo content,
      AsyncCallback<NativeMap<JsArray<CommentInfo>>> cb) {
    getViewApi(id, "drafts").put(content, cb);
  }

  public static void updateDraft(PatchSet.Id id, CommentInfo content,
      String draftId, AsyncCallback<NativeMap<JsArray<CommentInfo>>> cb) {
    getViewApi(id, "drafts").id(draftId).put(content, cb);
  }

  public static void deleteDraft(PatchSet.Id id, String draftId,
      AsyncCallback<NativeMap<JsArray<CommentInfo>>> cb) {
    getViewApi(id, "drafts").id(draftId).delete(cb);
  }

  private static RestApi getViewApi(PatchSet.Id id, String type) {
    return ChangeApi.revision(id).view(type);
  }

  private CommentApi() {
  }
}
