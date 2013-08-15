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

package com.google.gerrit.client.diff;

import com.google.gwt.core.client.JavaScriptObject;

import net.codemirror.lib.LineCharacter;
import net.codemirror.lib.TextMarker.FromTo;

public class CommentRange extends JavaScriptObject {
  public static CommentRange create(int sl, int sc, int el, int ec) {
    CommentRange r = createObject().cast();
    r.set(sl, sc, el, ec);
    return r;
  }

  public static CommentRange create(FromTo fromTo) {
    if (fromTo == null) {
      return null;
    }

    LineCharacter from = fromTo.getFrom();
    LineCharacter to = fromTo.getTo();
    return create(
        from.getLine() + 1, from.getCh(),
        to.getLine() + 1, to.getCh());
  }

  public final native int start_line() /*-{ return this.start_line; }-*/;
  public final native int start_character() /*-{ return this.start_character; }-*/;
  public final native int end_line() /*-{ return this.end_line; }-*/;
  public final native int end_character() /*-{ return this.end_character; }-*/;

  private final native void set(int sl, int sc, int el, int ec) /*-{
    this.start_line = sl;
    this.start_character = sc;
    this.end_line = el;
    this.end_character = ec;
  }-*/;

  protected CommentRange() {
  }
}