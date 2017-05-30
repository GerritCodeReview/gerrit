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

import com.google.gerrit.client.DiffObject;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.CommentApi;
import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.rpc.AsyncCallback;
import java.util.Collections;
import java.util.Comparator;

/** Collection of published and draft comments loaded from the server. */
class CommentsCollections {
  private final Project.NameKey project;
  private final String path;
  private final DiffObject base;
  private final PatchSet.Id revision;
  private NativeMap<JsArray<CommentInfo>> publishedBaseAll;
  private NativeMap<JsArray<CommentInfo>> publishedRevisionAll;
  JsArray<CommentInfo> publishedBase;
  JsArray<CommentInfo> publishedRevision;
  JsArray<CommentInfo> draftsBase;
  JsArray<CommentInfo> draftsRevision;

  CommentsCollections(
      @Nullable Project.NameKey project, DiffObject base, PatchSet.Id revision, String path) {
    this.project = project;
    this.path = path;
    this.base = base;
    this.revision = revision;
  }

  void load(CallbackGroup group) {
    if (base.isPatchSet()) {
      CommentApi.comments(
          Project.NameKey.asStringOrNull(project), base.asPatchSetId(), group.add(publishedBase()));
    }
    CommentApi.comments(
        Project.NameKey.asStringOrNull(project), revision, group.add(publishedRevision()));

    if (Gerrit.isSignedIn()) {
      if (base.isPatchSet()) {
        CommentApi.drafts(
            Project.NameKey.asStringOrNull(project), base.asPatchSetId(), group.add(draftsBase()));
      }
      CommentApi.drafts(
          Project.NameKey.asStringOrNull(project), revision, group.add(draftsRevision()));
    }
  }

  boolean hasCommentForPath(String filePath) {
    if (base.isPatchSet()) {
      JsArray<CommentInfo> forBase = publishedBaseAll.get(filePath);
      if (forBase != null && forBase.length() > 0) {
        return true;
      }
    }
    JsArray<CommentInfo> forRevision = publishedRevisionAll.get(filePath);
    if (forRevision != null && forRevision.length() > 0) {
      return true;
    }
    return false;
  }

  private AsyncCallback<NativeMap<JsArray<CommentInfo>>> publishedBase() {
    return new AsyncCallback<NativeMap<JsArray<CommentInfo>>>() {
      @Override
      public void onSuccess(NativeMap<JsArray<CommentInfo>> result) {
        publishedBaseAll = result;
        publishedBase = sort(result.get(path));
      }

      @Override
      public void onFailure(Throwable caught) {}
    };
  }

  private AsyncCallback<NativeMap<JsArray<CommentInfo>>> publishedRevision() {
    return new AsyncCallback<NativeMap<JsArray<CommentInfo>>>() {
      @Override
      public void onSuccess(NativeMap<JsArray<CommentInfo>> result) {
        for (String k : result.keySet()) {
          result.put(k, filterForParent(result.get(k)));
        }
        publishedRevisionAll = result;
        publishedRevision = sort(result.get(path));
      }

      @Override
      public void onFailure(Throwable caught) {}
    };
  }

  private JsArray<CommentInfo> filterForParent(JsArray<CommentInfo> list) {
    JsArray<CommentInfo> result = JsArray.createArray().cast();
    for (CommentInfo c : Natives.asList(list)) {
      if (c.side() == Side.REVISION) {
        result.push(c);
      } else if (base.isBaseOrAutoMerge() && !c.hasParent()) {
        result.push(c);
      } else if (base.isParent() && c.parent() == base.getParentNum()) {
        result.push(c);
      }
    }
    return result;
  }

  private AsyncCallback<NativeMap<JsArray<CommentInfo>>> draftsBase() {
    return new AsyncCallback<NativeMap<JsArray<CommentInfo>>>() {
      @Override
      public void onSuccess(NativeMap<JsArray<CommentInfo>> result) {
        draftsBase = sort(result.get(path));
      }

      @Override
      public void onFailure(Throwable caught) {}
    };
  }

  private AsyncCallback<NativeMap<JsArray<CommentInfo>>> draftsRevision() {
    return new AsyncCallback<NativeMap<JsArray<CommentInfo>>>() {
      @Override
      public void onSuccess(NativeMap<JsArray<CommentInfo>> result) {
        for (String k : result.keySet()) {
          result.put(k, filterForParent(result.get(k)));
        }
        draftsRevision = sort(result.get(path));
      }

      @Override
      public void onFailure(Throwable caught) {}
    };
  }

  private JsArray<CommentInfo> sort(JsArray<CommentInfo> in) {
    if (in != null) {
      for (CommentInfo c : Natives.asList(in)) {
        c.path(path);
      }
      Collections.sort(
          Natives.asList(in),
          new Comparator<CommentInfo>() {
            @Override
            public int compare(CommentInfo a, CommentInfo b) {
              return a.updated().compareTo(b.updated());
            }
          });
    }
    return in;
  }
}
