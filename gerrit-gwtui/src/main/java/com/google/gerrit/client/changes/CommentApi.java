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
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.rpc.AsyncCallback;

public class CommentApi {

  public static void comments(
      @Nullable String project, PatchSet.Id id, AsyncCallback<NativeMap<JsArray<CommentInfo>>> cb) {
    revision(project, id, "comments").get(cb);
  }

  public static void comment(
      @Nullable String project, PatchSet.Id id, String commentId, AsyncCallback<CommentInfo> cb) {
    revision(project, id, "comments").id(commentId).get(cb);
  }

  public static void drafts(
      @Nullable String project, PatchSet.Id id, AsyncCallback<NativeMap<JsArray<CommentInfo>>> cb) {
    revision(project, id, "drafts").get(cb);
  }

  public static void draft(
      @Nullable String project, PatchSet.Id id, String draftId, AsyncCallback<CommentInfo> cb) {
    revision(project, id, "drafts").id(draftId).get(cb);
  }

  public static void createDraft(
      @Nullable String project,
      PatchSet.Id id,
      CommentInfo content,
      AsyncCallback<CommentInfo> cb) {
    revision(project, id, "drafts").put(content, cb);
  }

  public static void updateDraft(
      @Nullable String project,
      PatchSet.Id id,
      String draftId,
      CommentInfo content,
      AsyncCallback<CommentInfo> cb) {
    revision(project, id, "drafts").id(draftId).put(content, cb);
  }

  public static void deleteDraft(
      @Nullable String project,
      PatchSet.Id id,
      String draftId,
      AsyncCallback<JavaScriptObject> cb) {
    revision(project, id, "drafts").id(draftId).delete(cb);
  }

  private static RestApi revision(@Nullable String project, PatchSet.Id id, String type) {
    return ChangeApi.revision(project, id).view(type);
  }

  private CommentApi() {}
}
