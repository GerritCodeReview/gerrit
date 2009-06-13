// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.client.patches;

import com.google.gerrit.client.reviewdb.AccountGeneralPreferences;

public class PatchScriptSettings {
  public static enum Whitespace {
    IGNORE_NONE, IGNORE_SPACE_AT_EOL, IGNORE_SPACE_CHANGE, IGNORE_ALL_SPACE;
  }

  protected int context;
  protected Whitespace whitespace;

  public PatchScriptSettings() {
    context = AccountGeneralPreferences.DEFAULT_CONTEXT;
    whitespace = Whitespace.IGNORE_NONE;
  }

  public int getContext() {
    return context;
  }

  public void setContext(final int ctx) {
    assert 0 <= ctx || ctx == AccountGeneralPreferences.WHOLE_FILE_CONTEXT;
    context = ctx;
  }

  public Whitespace getWhitespace() {
    return whitespace;
  }

  public void setWhitespace(final Whitespace ws) {
    assert ws != null;
    whitespace = ws;
  }
}
