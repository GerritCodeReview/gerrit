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

package com.google.gerrit.server.notedb;

import com.google.common.base.Splitter;
import com.google.gerrit.reviewdb.client.Change;

public class ChangeNoteUtil {
  public static final String REFS_GERRIT_CHANGES = "refs/gerrit/changes/";

  public static String changeRefName(Change.Id id) {
    StringBuilder r = new StringBuilder();
    r.append(REFS_GERRIT_CHANGES);
    int n = id.get();
    int m = n % 100;
    if (m < 10) {
      r.append('0');
    }
    r.append(m);
    r.append('/');
    r.append(n);
    return r.toString();
  }

  static final String FOOTER_ACCOUNT = "Gerrit-Account";
  static final String FOOTER_PATCH_SET = "Gerrit-Patch-Set";
  static final String FOOTER_VOTE = "Gerrit-Vote";

  static final Splitter EQUALS = Splitter.on('=').limit(2);

  private ChangeNoteUtil() {
  }
}
