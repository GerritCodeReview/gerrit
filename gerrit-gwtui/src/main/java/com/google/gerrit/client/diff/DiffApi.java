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

import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.user.client.rpc.AsyncCallback;

public class DiffApi {
  public enum IgnoreWhitespace {
    NONE, TRAILING, CHANGED, ALL;
  };

  public static void list(int id, String base, String revision,
      AsyncCallback<NativeMap<FileInfo>> cb) {
    RestApi api = ChangeApi.revision(id, revision).view("files");
    if (base != null) {
      api.addParameter("base", base);
    }
    api.get(NativeMap.copyKeysIntoChildren("path", cb));
  }

  public static DiffApi diff(PatchSet.Id id, String path) {
    return new DiffApi(ChangeApi.revision(id)
        .view("files").id(path)
        .view("diff"));
  }

  private final RestApi call;

  private DiffApi(RestApi call) {
    this.call = call;
  }

  public DiffApi base(PatchSet.Id id) {
    if (id != null) {
      call.addParameter("base", id.get());
    }
    return this;
  }

  public DiffApi ignoreWhitespace(AccountDiffPreference.Whitespace w) {
    switch (w) {
      default:
      case IGNORE_NONE:
        return ignoreWhitespace(IgnoreWhitespace.NONE);
      case IGNORE_SPACE_AT_EOL:
        return ignoreWhitespace(IgnoreWhitespace.TRAILING);
      case IGNORE_SPACE_CHANGE:
        return ignoreWhitespace(IgnoreWhitespace.CHANGED);
      case IGNORE_ALL_SPACE:
        return ignoreWhitespace(IgnoreWhitespace.ALL);
    }
  }

  public DiffApi ignoreWhitespace(IgnoreWhitespace w) {
    if (w != null && w != IgnoreWhitespace.NONE) {
      call.addParameter("ignore-whitespace", w);
    }
    return this;
  }

  public DiffApi intraline(boolean intraline) {
    if (intraline) {
      call.addParameterTrue("intraline");
    }
    return this;
  }

  public DiffApi wholeFile() {
    call.addParameter("context", "ALL");
    return this;
  }

  public DiffApi context(int lines) {
    call.addParameter("context", lines);
    return this;
  }

  public void get(AsyncCallback<DiffInfo> cb) {
    call.get(cb);
  }
}
