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
    r.setStartLine(sl);
    r.setStartCh(sc);
    r.setEndLine(el);
    r.setEndCh(ec);
    return r;
  }

  public static CommentRange fromFromTo(FromTo fromTo) {
    LineCharacter from = fromTo.getFrom();
    LineCharacter to = fromTo.getTo();
    return create(from.getLine(), from.getCh(), to.getLine(), to.getCh());
  }

  public final native int start_line() /*-{
    return this.start_line;
  }-*/;

  public final native int start_ch() /*-{
    return this.start_ch;
  }-*/;

  public final native int end_line() /*-{
    return this.end_line;
  }-*/;

  public final native int end_ch() /*-{
    return this.end_ch;
  }-*/;

  private final native void setStartLine(int sl) /*-{
    this.start_line = sl;
  }-*/;

  private final native void setStartCh(int sc) /*-{
    this.start_ch = sc;
  }-*/;

  private final native void setEndLine(int el) /*-{
    this.end_line = el;
  }-*/;

  private final native void setEndCh(int ec) /*-{
    this.end_ch = ec;
  }-*/;

  protected CommentRange() {
  }
}