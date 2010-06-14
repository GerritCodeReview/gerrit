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

package com.google.gerrit.common.data;

import com.google.gerrit.prettify.common.PrettySettings;
import com.google.gerrit.reviewdb.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.CodedEnum;
import com.google.gerrit.reviewdb.Patch.PatchType;

public class PatchScriptSettings {
  public static enum Whitespace implements CodedEnum {
    IGNORE_NONE('N'), //
    IGNORE_SPACE_AT_EOL('E'), //
    IGNORE_SPACE_CHANGE('S'), //
    IGNORE_ALL_SPACE('A');

    private final char code;

    private Whitespace(final char c) {
      code = c;
    }

    public char getCode() {
      return code;
    }

    public static Whitespace forCode(final char c) {
      for (final Whitespace s : Whitespace.values()) {
        if (s.code == c) {
          return s;
        }
      }
      return null;
    }
  }

  protected int context;
  protected Whitespace whitespace;
  protected PrettySettings pretty;

  public PatchScriptSettings() {
    context = AccountGeneralPreferences.DEFAULT_CONTEXT;
    whitespace = Whitespace.IGNORE_NONE;
    pretty = new PrettySettings();
  }

  public PatchScriptSettings(final PatchScriptSettings s) {
    context = s.context;
    whitespace = s.whitespace;
    pretty = new PrettySettings(s.pretty);
  }

  public PrettySettings getPrettySettings() {
    return pretty;
  }

  public void setPrettySettings(PrettySettings s) {
    pretty = s;
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
