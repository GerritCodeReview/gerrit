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

package com.google.gerrit.client.diff;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.CommentApi;
import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.common.data.DiffType;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.rpc.AsyncCallback;

import java.util.Collections;
import java.util.Comparator;

/** Collection of published and draft comments loaded from the server. */
class CommentsCollections {
  private String path;

  JsArray<CommentInfo> publishedBase;
  JsArray<CommentInfo> publishedRevision;
  JsArray<CommentInfo> draftsBase;
  JsArray<CommentInfo> draftsRevision;

  void load(PatchSet.Id base, PatchSet.Id revision, DiffType diffType,
      String path, CallbackGroup group) {
    this.path = path;

    if (base != null) {
      CommentApi.comments(base, diffType, group.add(publishedBase()));
    }
    CommentApi.comments(revision, diffType, group.add(publishedRevision()));

    if (Gerrit.isSignedIn()) {
      if (base != null) {
        CommentApi.drafts(base, diffType, group.add(draftsBase()));
      }
      CommentApi.drafts(revision, diffType, group.add(draftsRevision()));
    }
  }

  private AsyncCallback<NativeMap<JsArray<CommentInfo>>> publishedBase() {
    return new AsyncCallback<NativeMap<JsArray<CommentInfo>>>() {
      @Override
      public void onSuccess(NativeMap<JsArray<CommentInfo>> result) {
        publishedBase = sort(result.get(path));
      }

      @Override
      public void onFailure(Throwable caught) {
      }
    };
  }

  private AsyncCallback<NativeMap<JsArray<CommentInfo>>> publishedRevision() {
    return new AsyncCallback<NativeMap<JsArray<CommentInfo>>>() {
      @Override
      public void onSuccess(NativeMap<JsArray<CommentInfo>> result) {
        publishedRevision = sort(result.get(path));
      }

      @Override
      public void onFailure(Throwable caught) {
      }
    };
  }

  private AsyncCallback<NativeMap<JsArray<CommentInfo>>> draftsBase() {
    return new AsyncCallback<NativeMap<JsArray<CommentInfo>>>() {
      @Override
      public void onSuccess(NativeMap<JsArray<CommentInfo>> result) {
        draftsBase = sort(result.get(path));
      }

      @Override
      public void onFailure(Throwable caught) {
      }
    };
  }

  private AsyncCallback<NativeMap<JsArray<CommentInfo>>> draftsRevision() {
    return new AsyncCallback<NativeMap<JsArray<CommentInfo>>>() {
      @Override
      public void onSuccess(NativeMap<JsArray<CommentInfo>> result) {
        draftsRevision = sort(result.get(path));
      }

      @Override
      public void onFailure(Throwable caught) {
      }
    };
  }

  private JsArray<CommentInfo> sort(JsArray<CommentInfo> in) {
    if (in != null) {
      for (CommentInfo c : Natives.asList(in)) {
        c.path(path);
      }
      Collections.sort(Natives.asList(in), new Comparator<CommentInfo>() {
        @Override
        public int compare(CommentInfo a, CommentInfo b) {
          return a.updated().compareTo(b.updated());
        }
      });
    }
    return in;
  }
}
