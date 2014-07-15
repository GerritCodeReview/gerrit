// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gwtorm.client.IntKey;

public class ChangeDraftNotesUtil {
  private static StringBuilder appendShardAndFullId(StringBuilder b,
      IntKey<com.google.gwtorm.client.Key<?>> id) {
    int n = id.get();
    int m = n % 100;
    if (m < 10) {
      b.append('0');
    }
    b.append(m);
    b.append('/');
    b.append(n);
    return b;
  }

  public static String draftRefName(Account.Id accountId, Change.Id changeId) {
    StringBuilder r = new StringBuilder();
    r.append(RefNames.REFS_USER);
    appendShardAndFullId(r, accountId);
    r.append("/drafts/");
    appendShardAndFullId(r, changeId);
    return r.toString();
  }
}
