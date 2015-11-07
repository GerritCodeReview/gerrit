// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.common.data.DiffType;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;

public class PatchLink extends InlineHyperlink {
  private PatchLink(String text, String historyToken) {
    super(text, historyToken);
  }

  public static class SideBySide extends PatchLink {
    public SideBySide(String text, PatchSet.Id base, Patch.Key id,
        DiffType diffType) {
      super(text, Dispatcher.toSideBySide(base, id, diffType));
    }
  }

  public static class Unified extends PatchLink {
    public Unified(String text, PatchSet.Id base, Patch.Key id,
        DiffType diffType) {
      super(text, Dispatcher.toUnified(base, id, diffType));
    }
  }
}
