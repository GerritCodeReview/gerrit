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

/** Pos (or {line, ch}) objects used within CodeMirror. */
public class Pos extends JavaScriptObject {
  public static final native Pos create(int line) /*-{
    return $wnd.CodeMirror.Pos(line)
  }-*/;

  public static final native Pos create(int line, int ch) /*-{
    return $wnd.CodeMirror.Pos(line, ch)
  }-*/;

  public final native void line(int l) /*-{ this.line = l }-*/;

  public final native void ch(int c) /*-{ this.ch = c }-*/;

  public final native int line() /*-{ return this.line }-*/;

  public final native int ch() /*-{ return this.ch || 0 }-*/;

  public final boolean equals(Pos o) {
    return this == o || (line() == o.line() && ch() == o.ch());
  }

  protected Pos() {}
}
