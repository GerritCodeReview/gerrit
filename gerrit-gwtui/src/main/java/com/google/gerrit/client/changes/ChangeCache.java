// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.client.ui.ListenableValue;
import com.google.gerrit.common.data.ChangeInfo;
import com.google.gerrit.reviewdb.client.Change;

import java.util.HashMap;
import java.util.Map;

/** A Cache to store common client side data by change */
public class ChangeCache {
  private static Map<Change.Id, ChangeCache> caches =
    new HashMap<Change.Id, ChangeCache>();

  public static ChangeCache get(Change.Id chg) {
    ChangeCache cache = caches.get(chg);
    if (cache == null) {
      cache = new ChangeCache(chg);
      caches.put(chg, cache);
    }
    return cache;
  }

  private Change.Id changeId;
  private ChangeDetailCache detail;
  private ListenableValue<ChangeInfo> info;

  protected ChangeCache(Change.Id chg) {
    changeId = chg;
  }

  public Change.Id getChangeId() {
    return changeId;
  }

  public ChangeDetailCache getChangeDetailCache() {
    if (detail == null) {
      detail = new ChangeDetailCache(changeId);
    }
    return detail;
  }

  public ListenableValue<ChangeInfo> getChangeInfoCache() {
    if (info == null) {
      info = new ListenableValue<ChangeInfo>();
    }
    return info;
  }
}
