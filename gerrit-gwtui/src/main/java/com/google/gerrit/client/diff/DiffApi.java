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

import static com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace.IGNORE_ALL;

import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.info.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.info.FileInfo;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.user.client.rpc.AsyncCallback;

public class DiffApi {
  public static void list(
      int id, String revision, RevisionInfo base, AsyncCallback<NativeMap<FileInfo>> cb) {
    RestApi api = ChangeApi.revision(id, revision).view("files");
    if (base != null) {
      if (base._number() < 0) {
        api.addParameter("parent", -base._number());
      } else {
        api.addParameter("base", base.name());
      }
    }
    api.get(NativeMap.copyKeysIntoChildren("path", cb));
  }

  public static void list(PatchSet.Id id, PatchSet.Id base, AsyncCallback<NativeMap<FileInfo>> cb) {
    RestApi api = ChangeApi.revision(id).view("files");
    if (base != null) {
      if (base.get() < 0) {
        api.addParameter("parent", -base.get());
      } else {
        api.addParameter("base", base.get());
      }
    }
    api.get(NativeMap.copyKeysIntoChildren("path", cb));
  }

  public static DiffApi diff(PatchSet.Id id, String path) {
    return new DiffApi(ChangeApi.revision(id).view("files").id(path).view("diff"));
  }

  private final RestApi call;

  private DiffApi(RestApi call) {
    this.call = call;
  }

  public DiffApi base(PatchSet.Id id) {
    if (id != null) {
      if (id.get() < 0) {
        call.addParameter("parent", -id.get());
      } else {
        call.addParameter("base", id.get());
      }
    }
    return this;
  }

  public DiffApi webLinksOnly() {
    call.addParameterTrue("weblinks-only");
    return this;
  }

  public DiffApi ignoreWhitespace(DiffPreferencesInfo.Whitespace w) {
    if (w != null && w != IGNORE_ALL) {
      call.addParameter("whitespace", w);
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
