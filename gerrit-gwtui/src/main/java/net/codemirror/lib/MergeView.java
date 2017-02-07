// Copyright (C) 2016 The Android Open Source Project
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
import com.google.gwt.dom.client.Element;

/** Object that represents a text marker within CodeMirror */
public class MergeView extends JavaScriptObject {
  public static MergeView create(Element p, Configuration cfg) {
    MergeView mv = newMergeView(p, cfg);
    Extras.attach(mv.leftOriginal());
    Extras.attach(mv.editor());
    return mv;
  }

  private static native MergeView newMergeView(Element p, Configuration cfg) /*-{
    return $wnd.CodeMirror.MergeView(p, cfg);
  }-*/;

  public final native CodeMirror leftOriginal() /*-{
    return this.leftOriginal();
  }-*/;

  public final native CodeMirror editor() /*-{
    return this.editor();
  }-*/;

  public final native void setShowDifferences(boolean b) /*-{
    this.setShowDifferences(b);
  }-*/;

  public final native Element getGapElement() /*-{
    return $doc.getElementsByClassName("CodeMirror-merge-gap")[0];
  }-*/;

  protected MergeView() {}
}
