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

import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.AsyncCallback;

import java.util.LinkedHashMap;
import java.util.Map;

/** Cache of {@link ConfigInfo} objects by project name. */
public class ConfigInfoCache {
  private static final int LIMIT = 25;
  private static final ConfigInfoCache instance =
      GWT.create(ConfigInfoCache.class);

  public static class Entry {
    private final ConfigInfo info;
    private CommentLinkProcessor commentLinkProcessor;

    private Entry(ConfigInfo info) {
      this.info = info;
    }

    public CommentLinkProcessor getCommentLinkProcessor() {
      if (commentLinkProcessor == null) {
        commentLinkProcessor = new CommentLinkProcessor(info.commentlinks());
      }
      return commentLinkProcessor;
    }

    public ThemeInfo getTheme() {
      return info.theme();
    }
  }

  public static void get(Project.NameKey name, AsyncCallback<Entry> cb) {
    instance.getImpl(name, cb);
  }

  private final LinkedHashMap<String, Entry> cache;

  protected ConfigInfoCache() {
    cache = new LinkedHashMap<String, Entry>(LIMIT) {
      private static final long serialVersionUID = 1L;

      @Override
      protected boolean removeEldestEntry(
          Map.Entry<String, ConfigInfoCache.Entry> e) {
        return size() > LIMIT;
      }
    };
  }

  private void getImpl(final Project.NameKey name,
      final AsyncCallback<Entry> cb) {
    Entry e = cache.get(name.get());
    if (e != null) {
      cb.onSuccess(e);
      return;
    }
    ProjectApi.config(name).get(new AsyncCallback<ConfigInfo>() {
      @Override
      public void onSuccess(ConfigInfo result) {
        Entry e = new Entry(result);
        cache.put(name.get(), e);
        cb.onSuccess(e);
      }

      @Override
      public void onFailure(Throwable caught) {
        cb.onFailure(caught);
      }
    });
  }
}
