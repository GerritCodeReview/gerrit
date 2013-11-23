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

import net.codemirror.lib.TextMarker.FromTo;

/**
 * Glue to connect CodeMirror to be callable from GWT.
 *
 * @see <a href="http://codemirror.net/doc/manual.html#api">CodeMirror API</a>
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

  public final native void setOption(String option, double value) /*-{
    this.setOption(option, value);
  }-*/;

  public final native void setOption(String option, JavaScriptObject val) /*-{
    this.setOption(option, val);
  }-*/;

  public final native void setValue(String v) /*-{ this.setValue(v); }-*/;

  public final native void setWidth(double w) /*-{ this.setSize(w, null); }-*/;
  public final native void setWidth(String w) /*-{ this.setSize(w, null); }-*/;
  public final native void setHeight(double h) /*-{ this.setSize(null, h); }-*/;
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

  public final native int lineAtHeight(double height) /*-{
    return this.lineAtHeight(height);
  }-*/;

  public final native int lineAtHeight(double height, String mode) /*-{
    return this.lineAtHeight(height, mode);
  }-*/;

  public final native double heightAtLine(int line) /*-{
    return this.heightAtLine(line);
  }-*/;

  public final native double heightAtLine(int line, String mode) /*-{
    return this.heightAtLine(line, mode);
  }-*/;

  public final native CodeMirrorDoc getDoc() /*-{
    return this.getDoc();
  }-*/;

  public final native void scrollTo(double x, double y) /*-{
    this.scrollTo(x, y);
  }-*/;

  public final native void scrollToY(double y) /*-{
    this.scrollTo(null, y);
  }-*/;

  public final native ScrollInfo getScrollInfo() /*-{
    return this.getScrollInfo();
  }-*/;

  public final native Viewport getViewport() /*-{
    return this.getViewport();
  }-*/;

  public final native int getOldViewportSize() /*-{
    return this.state.oldViewportSize || 0;
  }-*/;

  public final native void setOldViewportSize(int lines) /*-{
    this.state.oldViewportSize = lines;
  }-*/;

  public final native void operation(Runnable thunk) /*-{
    this.operation(function() {
      thunk.@java.lang.Runnable::run()();
    });
  }-*/;

  public final native void on(String event, Runnable thunk) /*-{
    this.on(event, $entry(function() {
      thunk.@java.lang.Runnable::run()();
    }));
  }-*/;

  /** TODO: Break this line after updating GWT */
  public final native void on(String event, EventHandler handler) /*-{
    this.on(event, $entry(function(cm, e) {
      handler.@net.codemirror.lib.CodeMirror.EventHandler::handle(Lnet/codemirror/lib/CodeMirror;Lcom/google/gwt/dom/client/NativeEvent;)(cm, e);
    }));
  }-*/;

  public final native void on(String event, RenderLineHandler handler) /*-{
    this.on(event, $entry(function(cm, h, ele) {
      handler.@net.codemirror.lib.CodeMirror.RenderLineHandler::handle(Lnet/codemirror/lib/CodeMirror;Lnet/codemirror/lib/CodeMirror$LineHandle;Lcom/google/gwt/dom/client/Element;)(cm, h, ele);
    }));
  }-*/;

  public final native void on(String event, GutterClickHandler handler) /*-{
    this.on(event, $entry(function(cm, l, g, e) {
      handler.@net.codemirror.lib.CodeMirror.GutterClickHandler::handle(Lnet/codemirror/lib/CodeMirror;ILjava/lang/String;Lcom/google/gwt/dom/client/NativeEvent;)(cm, l, g, e);
    }));
  }-*/;

  public final native LineCharacter getCursor() /*-{
    return this.getCursor();
  }-*/;

  public final native LineCharacter getCursor(String start) /*-{
    return this.getCursor(start);
  }-*/;

  public final FromTo getSelectedRange() {
    return FromTo.create(getCursor("start"), getCursor("end"));
  };

  public final native void setCursor(LineCharacter lineCh) /*-{
    this.setCursor(lineCh);
  }-*/;

  public final native boolean somethingSelected() /*-{
    return this.somethingSelected();
  }-*/;

  public final native boolean hasActiveLine() /*-{
    return !!this.state.activeLine;
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

  public final native int lineCount() /*-{
    return this.lineCount();
  }-*/;

  public final native Element getGutterElement() /*-{
    return this.getGutterElement();
  }-*/;

  public final native Element getScrollerElement() /*-{
    return this.getScrollerElement();
  }-*/;

  public final native Element getSizer() /*-{
    return this.display.sizer;
  }-*/;

  public final native Element getInputField() /*-{
    return this.getInputField();
  }-*/;

  public final native Element getScrollbarV() /*-{
    return this.display.scrollbarV;
  }-*/;

  public static final native KeyMap cloneKeyMap(String name) /*-{
    var i = $wnd.CodeMirror.keyMap[name];
    var o = {};
    for (n in i)
      if (i.hasOwnProperty(n))
        o[n] = i[n];
    return o;
  }-*/;

  public final native void execCommand(String cmd) /*-{
    this.execCommand(cmd);
  }-*/;

  public static final native void addKeyMap(String name, KeyMap km) /*-{
    $wnd.CodeMirror.keyMap[name] = km;
  }-*/;

  public static final native void handleVimKey(CodeMirror cm, String key) /*-{
    $wnd.CodeMirror.Vim.handleKey(cm, key);
  }-*/;

  public static final native void mapVimKey(String alias, String actual) /*-{
    $wnd.CodeMirror.Vim.map(alias, actual);
  }-*/;

  public final native boolean hasVimSearchHighlight() /*-{
    return this.state.vim && this.state.vim.searchState_ &&
        !!this.state.vim.searchState_.getOverlay();
  }-*/;

  protected CodeMirror() {
  }

  public static class Viewport extends JavaScriptObject {
    public final native int getFrom() /*-{ return this.from; }-*/;
    public final native int getTo() /*-{ return this.to; }-*/;

    protected Viewport() {
    }
  }

  public static class LineHandle extends JavaScriptObject {
    protected LineHandle(){
    }
  }

  public interface EventHandler {
    public void handle(CodeMirror instance, NativeEvent event);
  }

  public interface RenderLineHandler {
    public void handle(CodeMirror instance, LineHandle handle, Element element);
  }

  public interface GutterClickHandler {
    public void handle(CodeMirror instance, int line, String gutter,
        NativeEvent clickEvent);
  }
}
