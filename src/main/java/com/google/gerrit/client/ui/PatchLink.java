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

import com.google.gerrit.client.Link;
import com.google.gerrit.client.changes.PatchTable;
import com.google.gerrit.client.patches.PatchScreen;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

public abstract class PatchLink extends DirectScreenLink {
  protected PatchSetKeys patch;

  public PatchLink(final String text, final PatchSetKeys patch, final String token) {
    super(text, token);
    this.patch = patch;
  }

  protected PatchTable parentPatchTable() {
    Widget w = getParent();
    while (w != null) {
      if (w instanceof PatchTable) {
        return ((PatchTable) w);
      }
      w = w.getParent();
    }
    return null;
  }

  public static class SideBySide extends PatchLink {
    public SideBySide(final String text, PatchSetKeys psk) {
      super(text, psk, Link.toPatchSideBySide(psk.getKey()));
    }

    @Override
    protected Screen createScreen() {
      return new PatchScreen.SideBySide(patch.getKey(), patch, parentPatchTable());
    }
  }

  public static class Unified extends PatchLink {
    public Unified(final String text, PatchSetKeys psk) {
      super(text, psk, Link.toPatchUnified(psk.getKey()));
    }

    @Override
    protected Screen createScreen() {
      return new PatchScreen.Unified(patch.getKey(), patch, parentPatchTable());
    }
  }
}
