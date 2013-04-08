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

package com.google.gerrit.client.projects;

import com.google.gerrit.client.projects.ConfigInfo.CommentLinkInfo;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtexpui.safehtml.client.FindReplace;
import com.google.gwtexpui.safehtml.client.LinkFindReplace;
import com.google.gwtexpui.safehtml.client.RawFindReplace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cache of {@link ConfigInfo} objects by project name. */
public class ConfigInfoCache {
  private static final int LIMIT = 25;

  public static class Value {
    private final ConfigInfo info;
    private CommentLinkProcessor commentLinkProcessor;

    private Value(ConfigInfo info) {
      this.info = info;
    }

    public CommentLinkProcessor getCommentLinkProcessor() {
      if (commentLinkProcessor == null) {
        JsArray<CommentLinkInfo> cls = info.commentlinks().values();
        List<FindReplace> commentLinks =
            new ArrayList<FindReplace>(cls.length());
        for (int i = 0; i < cls.length(); i++) {
          CommentLinkInfo cl = cls.get(i);
          if (!cl.enabled()) {
            continue;
          }
          if (cl.link() != null) {
            commentLinks.add(new LinkFindReplace(cl.match(), cl.link()));
          } else {
            commentLinks.add(new RawFindReplace(cl.match(), cl.html()));
          }
        }
        commentLinkProcessor = new CommentLinkProcessor(commentLinks);
      }
      return commentLinkProcessor;
    }
  }

  private final LinkedHashMap<String, Value> cache;

  protected ConfigInfoCache() {
    cache = new LinkedHashMap<String, Value>(LIMIT) {
      private static final long serialVersionUID = 1L;

      @Override
      protected boolean removeEldestEntry(Map.Entry<String, Value> e) {
        return size() > LIMIT;
      }
    };
  }

  public void get(final Project.NameKey name, final AsyncCallback<Value> cb) {
    Value v = cache.get(name.get());
    if (v != null) {
      cb.onSuccess(v);
      return;
    }
    ProjectApi.config(name).get(new AsyncCallback<ConfigInfo>() {
      @Override
      public void onSuccess(ConfigInfo result) {
        Value v = new Value(result);
        cache.put(name.get(), v);
        cb.onSuccess(v);
      }

      @Override
      public void onFailure(Throwable caught) {
        cb.onFailure(caught);
      }
    });
  }
}
