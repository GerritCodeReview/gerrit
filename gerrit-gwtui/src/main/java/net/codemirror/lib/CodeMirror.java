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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.diff.DisplaySide;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import net.codemirror.lib.TextMarker.FromTo;

/**
 * Glue to connect CodeMirror to be callable from GWT.
 *
 * @see <a href="http://codemirror.net/doc/manual.html#api">CodeMirror API</a>
 */
public class CodeMirror extends JavaScriptObject {
  public static void preload() {
    initLibrary(CallbackGroup.<Void>emptyCallback());
  }

  public static void initLibrary(AsyncCallback<Void> cb) {
    Loader.initLibrary(cb);
  }

  interface Style extends CssResource {
    String activeLine();

    String showTabs();

    String margin();
  }

  static Style style() {
    return Lib.I.style();
  }

  public static CodeMirror create(Element p, Configuration cfg) {
    CodeMirror cm = newCM(p, cfg);
    Extras.attach(cm);
    return cm;
  }

  private static native CodeMirror newCM(Element p, Configuration cfg) /*-{
    return $wnd.CodeMirror(p, cfg);
  }-*/;

  public final native void setOption(String option, boolean value) /*-{
    this.setOption(option, value)
  }-*/;

  public final native void setOption(String option, double value) /*-{
    this.setOption(option, value)
  }-*/;

  public final native void setOption(String option, String value) /*-{
    this.setOption(option, value)
  }-*/;

  public final native void setOption(String option, JavaScriptObject val) /*-{
    this.setOption(option, val)
  }-*/;

  public final native String getStringOption(String o) /*-{ return this.getOption(o) }-*/;

  public final native String getValue() /*-{ return this.getValue() }-*/;

  public final native void setValue(String v) /*-{ this.setValue(v) }-*/;

  public final native int changeGeneration(boolean closeEvent)
      /*-{ return this.changeGeneration(closeEvent) }-*/ ;

  public final native boolean isClean(int generation)/*-{ return this.isClean(generation) }-*/ ;

  public final native void setWidth(double w) /*-{ this.setSize(w, null) }-*/;

  public final native void setHeight(double h) /*-{ this.setSize(null, h) }-*/;

  public final int getHeight() {
    return getWrapperElement().getClientHeight();
  }

  public final void adjustHeight(int localHeader) {
    int rest = Gerrit.getHeaderFooterHeight() + localHeader + 5; // Estimate
    setHeight(Window.getClientHeight() - rest);
  }

  public final native String getLine(int n) /*-{ return this.getLine(n) }-*/;

  public final native double barHeight() /*-{ return this.display.barHeight }-*/;

  public final native double barWidth() /*-{ return this.display.barWidth }-*/;

  public final native int lastLine() /*-{ return this.lastLine() }-*/;

  public final native void refresh() /*-{ this.refresh() }-*/;

  public final native TextMarker markText(Pos from, Pos to, Configuration options) /*-{
    return this.markText(from, to, options)
  }-*/;

  public enum LineClassWhere {
    TEXT {
      @Override
      String value() {
        return "text";
      }
    },
    BACKGROUND {
      @Override
      String value() {
        return "background";
      }
    },
    WRAP {
      @Override
      String value() {
        return "wrap";
      }
    };

    abstract String value();
  }

  public final void addLineClass(int line, LineClassWhere where, String className) {
    addLineClassNative(line, where.value(), className);
  }

  private native void addLineClassNative(int line, String where, String lineClass) /*-{
    this.addLineClass(line, where, lineClass)
  }-*/;

  public final void addLineClass(LineHandle line, LineClassWhere where, String className) {
    addLineClassNative(line, where.value(), className);
  }

  private native void addLineClassNative(LineHandle line, String where, String lineClass) /*-{
    this.addLineClass(line, where, lineClass)
  }-*/;

  public final void removeLineClass(int line, LineClassWhere where, String className) {
    removeLineClassNative(line, where.value(), className);
  }

  private native void removeLineClassNative(int line, String where, String lineClass) /*-{
    this.removeLineClass(line, where, lineClass)
  }-*/;

  public final void removeLineClass(LineHandle line, LineClassWhere where, String className) {
    removeLineClassNative(line, where.value(), className);
  }

  private native void removeLineClassNative(LineHandle line, String where, String lineClass) /*-{
    this.removeLineClass(line, where, lineClass)
  }-*/;

  public final native void addWidget(Pos pos, Element node) /*-{
    this.addWidget(pos, node, false)
  }-*/;

  public final native LineWidget addLineWidget(int line, Element node, Configuration options) /*-{
    return this.addLineWidget(line, node, options)
  }-*/;

  public final native int lineAtHeight(double height) /*-{
    return this.lineAtHeight(height)
  }-*/;

  public final native int lineAtHeight(double height, String mode) /*-{
    return this.lineAtHeight(height, mode)
  }-*/;

  public final native double heightAtLine(int line) /*-{
    return this.heightAtLine(line)
  }-*/;

  public final native double heightAtLine(int line, String mode) /*-{
    return this.heightAtLine(line, mode)
  }-*/;

  public final native Rect charCoords(Pos pos, String mode) /*-{
    return this.charCoords(pos, mode)
  }-*/;

  public final native CodeMirrorDoc getDoc() /*-{
    return this.getDoc()
  }-*/;

  public final native void scrollTo(double x, double y) /*-{
    this.scrollTo(x, y)
  }-*/;

  public final native void scrollToY(double y) /*-{
    this.scrollTo(null, y)
  }-*/;

  public final void scrollToLine(int line) {
    int height = getHeight();
    if (lineAtHeight(height - 20) < line) {
      scrollToY(heightAtLine(line, "local") - 0.5 * height);
    }
    setCursor(Pos.create(line, 0));
  }

  public final native ScrollInfo getScrollInfo() /*-{
    return this.getScrollInfo()
  }-*/;

  public final native Viewport getViewport() /*-{
    return this.getViewport()
  }-*/;

  public final native void operation(Runnable thunk) /*-{
    this.operation(function() {
      thunk.@java.lang.Runnable::run()();
    })
  }-*/;

  public final native void off(String event, RegisteredHandler h) /*-{
    this.off(event, h)
  }-*/;

  public final native RegisteredHandler on(String event, Runnable thunk) /*-{
    var h = $entry(function() { thunk.@java.lang.Runnable::run()() });
    this.on(event, h);
    return h;
  }-*/;

  public final native void on(String event, EventHandler handler) /*-{
    this.on(event, $entry(function(cm, e) {
      handler.@net.codemirror.lib.CodeMirror.EventHandler::handle(
        Lnet/codemirror/lib/CodeMirror;
        Lcom/google/gwt/dom/client/NativeEvent;)(cm, e);
    }))
  }-*/;

  public final native void on(String event, RenderLineHandler handler) /*-{
    this.on(event, $entry(function(cm, h, e) {
      handler.@net.codemirror.lib.CodeMirror.RenderLineHandler::handle(
        Lnet/codemirror/lib/CodeMirror;
        Lnet/codemirror/lib/CodeMirror$LineHandle;
        Lcom/google/gwt/dom/client/Element;)(cm, h, e);
    }))
  }-*/;

  public final native void on(String event, GutterClickHandler handler) /*-{
    this.on(event, $entry(function(cm, l, g, e) {
      handler.@net.codemirror.lib.CodeMirror.GutterClickHandler::handle(
        Lnet/codemirror/lib/CodeMirror;
        I
        Ljava/lang/String;
        Lcom/google/gwt/dom/client/NativeEvent;)(cm, l, g, e);
    }))
  }-*/;

  public final native void on(String event, BeforeSelectionChangeHandler handler) /*-{
    this.on(event, $entry(function(cm, o) {
      var e = o.ranges[o.ranges.length-1];
      handler.@net.codemirror.lib.CodeMirror.BeforeSelectionChangeHandler::handle(
        Lnet/codemirror/lib/CodeMirror;
        Lnet/codemirror/lib/Pos;
        Lnet/codemirror/lib/Pos;)(cm, e.anchor, e.head);
    }))
  }-*/;

  public final native void on(ChangesHandler handler) /*-{
    this.on('changes', $entry(function(cm, o) {
      handler.@net.codemirror.lib.CodeMirror.ChangesHandler::handle(
        Lnet/codemirror/lib/CodeMirror;)(cm);
    }))
  }-*/;

  public final native void setCursor(Pos p) /*-{ this.setCursor(p) }-*/;

  public final native Pos getCursor() /*-{ return this.getCursor() }-*/;

  public final native Pos getCursor(String start) /*-{
    return this.getCursor(start)
  }-*/;

  public final FromTo getSelectedRange() {
    return FromTo.create(getCursor("start"), getCursor("end"));
  }

  public final native void setSelection(Pos p) /*-{ this.setSelection(p) }-*/;

  public final native void setSelection(Pos anchor, Pos head) /*-{
    this.setSelection(anchor, head)
  }-*/;

  public final native boolean somethingSelected() /*-{
    return this.somethingSelected()
  }-*/;

  public final native void addKeyMap(KeyMap map) /*-{ this.addKeyMap(map) }-*/;

  public final native void removeKeyMap(KeyMap map) /*-{ this.removeKeyMap(map) }-*/;

  public final native LineHandle getLineHandle(int line) /*-{
    return this.getLineHandle(line)
  }-*/;

  public final native LineHandle getLineHandleVisualStart(int line) /*-{
    return this.getLineHandleVisualStart(line)
  }-*/;

  public final native int getLineNumber(LineHandle handle) /*-{
    return this.getLineNumber(handle)
  }-*/;

  public final native void focus() /*-{
    this.focus()
  }-*/;

  public final native Element getWrapperElement() /*-{
    return this.getWrapperElement()
  }-*/;

  public final native Element getGutterElement() /*-{
    return this.getGutterElement()
  }-*/;

  public final native Element sizer() /*-{
    return this.display.sizer
  }-*/;

  public final native Element mover() /*-{
    return this.display.mover
  }-*/;

  public final native Element measure() /*-{
    return this.display.measure
  }-*/;

  public final native Element scrollbarV() /*-{
    return this.display.scrollbars.vert.node;
  }-*/;

  public final native void execCommand(String cmd) /*-{
    this.execCommand(cmd)
  }-*/;

  public static final native KeyMap getKeyMap(String name) /*-{
    return $wnd.CodeMirror.keyMap[name];
  }-*/;

  public static final native void addKeyMap(String name, KeyMap km) /*-{
    $wnd.CodeMirror.keyMap[name] = km
  }-*/;

  public static final native void normalizeKeyMap(KeyMap km) /*-{
    $wnd.CodeMirror.normalizeKeyMap(km);
  }-*/;

  public static final native void addCommand(String name, CommandRunner runner) /*-{
    $wnd.CodeMirror.commands[name] = function(cm) {
      runner.@net.codemirror.lib.CodeMirror.CommandRunner::run(
        Lnet/codemirror/lib/CodeMirror;)(cm);
    };
  }-*/;

  public final native Vim vim() /*-{
    return this;
  }-*/;

  public final DisplaySide side() {
    return extras().side();
  }

  public final Extras extras() {
    return Extras.get(this);
  }

  public final native LineHandle setGutterMarker(int line, String gutterId, Element value) /*-{
    return this.setGutterMarker(line, gutterId, value);
  }-*/;

  public final native LineHandle setGutterMarker(
      LineHandle line, String gutterId, Element value) /*-{
    return this.setGutterMarker(line, gutterId, value);
  }-*/;

  public final native boolean hasSearchHighlight() /*-{
    return this.state.search && !!this.state.search.query;
  }-*/;

  protected CodeMirror() {}

  public static class Viewport extends JavaScriptObject {
    public final native int from() /*-{ return this.from }-*/;

    public final native int to() /*-{ return this.to }-*/;

    public final boolean contains(int line) {
      return from() <= line && line < to();
    }

    protected Viewport() {}
  }

  public static class LineHandle extends JavaScriptObject {
    protected LineHandle() {}
  }

  public static class RegisteredHandler extends JavaScriptObject {
    protected RegisteredHandler() {}
  }

  public interface EventHandler {
    void handle(CodeMirror instance, NativeEvent event);
  }

  public interface RenderLineHandler {
    void handle(CodeMirror instance, LineHandle handle, Element element);
  }

  public interface GutterClickHandler {
    void handle(CodeMirror instance, int line, String gutter, NativeEvent clickEvent);
  }

  public interface BeforeSelectionChangeHandler {
    void handle(CodeMirror instance, Pos anchor, Pos head);
  }

  public interface ChangesHandler {
    void handle(CodeMirror instance);
  }

  public interface CommandRunner {
    void run(CodeMirror instance);
  }
}
