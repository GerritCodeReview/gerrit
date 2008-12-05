// Copyright 2008 Google Inc.
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
import com.google.gerrit.client.patches.PatchSideBySideScreen;
import com.google.gerrit.client.patches.PatchUnifiedScreen;
import com.google.gerrit.client.reviewdb.Patch;

public abstract class PatchLink extends DirectScreenLink {
  protected Patch.Id id;

  public PatchLink(final String text, final Patch.Id p, final String token) {
    super(text, token);
    id = p;
  }

  public static class SideBySide extends PatchLink {
    public SideBySide(final String text, final Patch.Id p) {
      super(text, p, Link.toPatchSideBySide(p));
    }

    @Override
    protected Screen createScreen() {
      return new PatchSideBySideScreen(id);
    }
  }

  public static class Unified extends PatchLink {
    public Unified(final String text, final Patch.Id p) {
      super(text, p, Link.toPatchUnified(p));
    }

    @Override
    protected Screen createScreen() {
      return new PatchUnifiedScreen(id);
    }
  }
}
