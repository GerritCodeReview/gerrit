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
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.rpc.AsyncCallback;

public class CommentApi {

  public static void comments(PatchSet.Id id,
      AsyncCallback<NativeMap<JsArray<CommentInfo>>> cb) {
    ChangeApi.revision(id).view("comments").get(cb);
  }

  public static void drafts(PatchSet.Id id,
      AsyncCallback<NativeMap<JsArray<CommentInfo>>> cb) {
    ChangeApi.revision(id).view("drafts").get(cb);
  }

  private CommentApi() {
  }
}
