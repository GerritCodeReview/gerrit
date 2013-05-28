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

package net.codemirror.lib;

import com.google.gwt.core.client.JavaScriptObject;

/** {line, ch} objects used within CodeMirror. */
public class LineCharacter extends JavaScriptObject {
  public static LineCharacter create(int line, int ch) {
    LineCharacter lineCh = createObject().cast();
    return lineCh.setLine(line).setCh(ch);
  }

  private final native LineCharacter setLine(int line) /*-{
    this.line = line; return this;
  }-*/;

  private final native LineCharacter setCh(int ch) /*-{
    this.ch = ch; return this;
  }-*/;

  public final native int getLine() /*-{ return this.line; }-*/;
  public final native int getCh() /*-{ return this.ch; }-*/;

  protected LineCharacter() {
  }
}
