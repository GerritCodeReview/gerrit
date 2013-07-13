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
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * Glue to connect CodeMirror to be callable from GWT.
 *
 * @link http://codemirror.net/doc/manual.html#api
 */
public class CodeMirror extends JavaScriptObject {
  public static void initLibrary(AsyncCallback<Void> cb) {
    Loader.initLibrary(cb);
  }

  public static native CodeMirror create(Element parent,
      Configuration cfg) /*-{
    return $wnd.CodeMirror(parent, cfg);
  }-*/;

  public final native void setOption(String option, boolean value) /*-{
    this.setOption(option, value);
  }-*/;

  public final native void setValue(String v) /*-{ this.setValue(v); }-*/;

  public final native void setWidth(int w) /*-{ this.setSize(w, null); }-*/;
  public final native void setWidth(String w) /*-{ this.setSize(w, null); }-*/;
  public final native void setHeight(int h) /*-{ this.setSize(null, h); }-*/;
  public final native void setHeight(String h) /*-{ this.setSize(null, h); }-*/;

  public final native void refresh() /*-{ this.refresh(); }-*/;
  public final native Element getWrapperElement() /*-{ return this.getWrapperElement(); }-*/;

  public final native TextMarker markText(LineCharacter from, LineCharacter to,
      Configuration options) /*-{
    return this.markText(from, to, options);
  }-*/;

  public enum LineClassWhere {
    TEXT, BACKGROUND, WRAP;
  }

  public final void addLineClass(int line, LineClassWhere where,
      String className) {
    addLineClassNative(line, where.name().toLowerCase(), className);
  }

  private final native void addLineClassNative(int line, String where,
      String lineClass) /*-{
    this.addLineClass(line, where, lineClass);
  }-*/;

  public final void addLineClass(LineHandle line, LineClassWhere where,
      String className) {
    addLineClassNative(line, where.name().toLowerCase(), className);
  }

  private final native void addLineClassNative(LineHandle line, String where,
      String lineClass) /*-{
    this.addLineClass(line, where, lineClass);
  }-*/;

  public final void removeLineClass(int line, LineClassWhere where,
      String className) {
    removeLineClassNative(line, where.name().toLowerCase(), className);
  }

  private final native void removeLineClassNative(int line, String where,
      String lineClass) /*-{
    this.removeLineClass(line, where, lineClass);
  }-*/;

  public final void removeLineClass(LineHandle line, LineClassWhere where,
      String className) {
    removeLineClassNative(line, where.name().toLowerCase(), className);
  }

  private final native void removeLineClassNative(LineHandle line, String where,
      String lineClass) /*-{
    this.removeLineClass(line, where, lineClass);
  }-*/;


  public final native void addWidget(LineCharacter pos, Element node,
      boolean scrollIntoView) /*-{
    this.addWidget(pos, node, scrollIntoView);
  }-*/;

  public final native LineWidget addLineWidget(int line, Element node,
      Configuration options) /*-{
    return this.addLineWidget(line, node, options);
  }-*/;

  public final native int lineAtHeight(int height) /*-{
    return this.lineAtHeight(height);
  }-*/;

  public final native CodeMirrorDoc getDoc() /*-{
    return this.getDoc();
  }-*/;

  public final native void scrollTo(int x, int y) /*-{
    this.scrollTo(x, y);
  }-*/;

  public final native void scrollToY(int y) /*-{
    this.scrollTo(null, y);
  }-*/;

  public final native ScrollInfo getScrollInfo() /*-{
    return this.getScrollInfo();
  }-*/;

  public final native void on(String event, Runnable thunk) /*-{
    this.on(event, $entry(function() {
      thunk.@java.lang.Runnable::run()();
    }));
  }-*/;

  public final native void on(String event, EventHandler handler) /*-{
    this.on(event, $entry(function(cm, e) {
      handler.@net.codemirror.lib.CodeMirror.EventHandler::handle(Lnet/codemirror/lib/CodeMirror;Lcom/google/gwt/dom/client/NativeEvent;)(cm, e);
    }));
  }-*/;

  public final native LineCharacter getCursor() /*-{
    return this.getCursor();
  }-*/;

  public final native LineCharacter getCursor(String start) /*-{
    return this.getCursor(start);
  }-*/;

  public final native void setCursor(LineCharacter lineCh) /*-{
    this.setCursor(lineCh);
  }-*/;

  public final native boolean somethingSelected() /*-{
    return this.somethingSelected();
  }-*/;

  public final native boolean hasActiveLine() /*-{
    return this.state.hasOwnProperty('activeLine');
  }-*/;

  public final native LineHandle getActiveLine() /*-{
    return this.state.activeLine;
  }-*/;

  public final native void setActiveLine(LineHandle line) /*-{
    this.state.activeLine = line;
  }-*/;

  public final native void addKeyMap(KeyMap map) /*-{ this.addKeyMap(map); }-*/;

  public final native void removeKeyMap(KeyMap map) /*-{ this.removeKeyMap(map); }-*/;

  public final native void removeKeyMap(String name) /*-{ this.removeKeyMap(name); }-*/;

  public static final native LineCharacter pos(int line, int ch) /*-{
    return $wnd.CodeMirror.Pos(line, ch);
  }-*/;

  public static final native LineCharacter pos(int line) /*-{
    return $wnd.CodeMirror.Pos(line);
  }-*/;

  public final native LineHandle getLineHandle(int line) /*-{
    return this.getLineHandle(line);
  }-*/;

  public final native LineHandle getLineHandleVisualStart(int line) /*-{
    return this.getLineHandleVisualStart(line);
  }-*/;

  public final native int getLineNumber(LineHandle handle) /*-{
    return this.getLineNumber(handle);
  }-*/;

  public final native void focus() /*-{
    this.focus();
  }-*/;

  /** Hack into CodeMirror to disable unwanted keys */
  public static final native void disableUnwantedKey(String category,
      String name) /*-{
    $wnd.CodeMirror.keyMap[category][name] = undefined;
  }-*/;

  protected CodeMirror() {
  }

  public static class LineHandle extends JavaScriptObject {
    protected LineHandle(){
    }
  }

  public interface EventHandler {
    public void handle(CodeMirror instance, NativeEvent event);
  }
}
