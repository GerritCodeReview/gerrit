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

import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.AsyncCallback;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cache of {@link ConfigInfo} objects by project name. */
public class ConfigInfoCache {
  private static final int PROJECT_LIMIT = 25;
  private static final int CHANGE_LIMIT = 100;
  private static final ConfigInfoCache instance = GWT.create(ConfigInfoCache.class);

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

    public List<String> getExtensionPanelNames(String extensionPoint) {
      return Natives.asList(info.extensionPanelNames().get(extensionPoint));
    }
  }

  public static void get(Project.NameKey name, AsyncCallback<Entry> cb) {
    instance.getImpl(name.get(), cb);
  }

  public static void get(Change.Id changeId, AsyncCallback<Entry> cb) {
    instance.getImpl(changeId.get(), cb);
  }

  public static void add(ChangeInfo info) {
    instance.changeToProject.put(info.legacyId().get(), info.project());
  }

  private final LinkedHashMap<String, Entry> cache;
  private final LinkedHashMap<Integer, String> changeToProject;

  protected ConfigInfoCache() {
    cache =
        new LinkedHashMap<String, Entry>(PROJECT_LIMIT) {
          private static final long serialVersionUID = 1L;

          @Override
          protected boolean removeEldestEntry(Map.Entry<String, ConfigInfoCache.Entry> e) {
            return size() > PROJECT_LIMIT;
          }
        };

    changeToProject =
        new LinkedHashMap<Integer, String>(CHANGE_LIMIT) {
          private static final long serialVersionUID = 1L;

          @Override
          protected boolean removeEldestEntry(Map.Entry<Integer, String> e) {
            return size() > CHANGE_LIMIT;
          }
        };
  }

  private void getImpl(String name, AsyncCallback<Entry> cb) {
    Entry e = cache.get(name);
    if (e != null) {
      cb.onSuccess(e);
      return;
    }
    ProjectApi.getConfig(
        new Project.NameKey(name),
        new AsyncCallback<ConfigInfo>() {
          @Override
          public void onSuccess(ConfigInfo result) {
            Entry e = new Entry(result);
            cache.put(name, e);
            cb.onSuccess(e);
          }

          @Override
          public void onFailure(Throwable caught) {
            cb.onFailure(caught);
          }
        });
  }

  private void getImpl(Integer id, AsyncCallback<Entry> cb) {
    String name = changeToProject.get(id);
    if (name != null) {
      getImpl(name, cb);
      return;
    }
    ChangeApi.change(id)
        .get(
            new AsyncCallback<ChangeInfo>() {
              @Override
              public void onSuccess(ChangeInfo result) {
                changeToProject.put(id, result.project());
                getImpl(result.project(), cb);
              }

              @Override
              public void onFailure(Throwable caught) {
                cb.onFailure(caught);
              }
            });
  }
}
