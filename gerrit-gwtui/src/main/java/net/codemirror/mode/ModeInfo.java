// Copyright (C) 2014 The Android Open Source Project
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

package net.codemirror.mode;

import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.Natives;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.resources.client.DataResource;
import com.google.gwt.safehtml.shared.SafeUri;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** Description of a CodeMirror language mode. */
public class ModeInfo extends JavaScriptObject {
  private static NativeMap<ModeInfo> byMime;
  private static NativeMap<ModeInfo> byExt;

  /** Map of names such as "clike" to URI for code download. */
  private static final Map<String, SafeUri> modeUris = new HashMap<>();

  static {
    indexModes(new DataResource[] {
      Modes.I.clike(),
      Modes.I.clojure(),
      Modes.I.coffeescript(),
      Modes.I.commonlisp(),
      Modes.I.css(),
      Modes.I.d(),
      Modes.I.dart(),
      Modes.I.diff(),
      Modes.I.dockerfile(),
      Modes.I.dtd(),
      Modes.I.erlang(),
      Modes.I.gas(),
      Modes.I.gerrit_commit(),
      Modes.I.gfm(),
      Modes.I.go(),
      Modes.I.groovy(),
      Modes.I.haskell(),
      Modes.I.htmlmixed(),
      Modes.I.javascript(),
      Modes.I.lua(),
      Modes.I.markdown(),
      Modes.I.perl(),
      Modes.I.php(),
      Modes.I.pig(),
      Modes.I.properties(),
      Modes.I.python(),
      Modes.I.r(),
      Modes.I.rst(),
      Modes.I.ruby(),
      Modes.I.scheme(),
      Modes.I.shell(),
      Modes.I.smalltalk(),
      Modes.I.soy(),
      Modes.I.sql(),
      Modes.I.stex(),
      Modes.I.velocity(),
      Modes.I.verilog(),
      Modes.I.xml(),
      Modes.I.yaml(),
    });

    alias("application/x-httpd-php-open", "application/x-httpd-php");
    alias("application/x-javascript", "application/javascript");
    alias("application/x-shellscript", "text/x-sh");
    alias("application/x-tcl", "text/x-tcl");
    alias("text/typescript", "application/typescript");
    alias("text/x-c", "text/x-csrc");
    alias("text/x-c++hdr", "text/x-c++src");
    alias("text/x-chdr", "text/x-csrc");
    alias("text/x-h", "text/x-csrc");
    alias("text/x-ini", "text/x-properties");
    alias("text/x-java-source", "text/x-java");
    alias("text/x-php", "application/x-httpd-php");
    alias("text/x-scripttcl", "text/x-tcl");
  }

  /** All supported modes. */
  public static native JsArray<ModeInfo> all() /*-{
    return $wnd.CodeMirror.modeInfo
  }-*/;

  private static native void setAll(JsArray<ModeInfo> m) /*-{
    $wnd.CodeMirror.modeInfo = m
  }-*/;

  /** Look up mode by primary or alternate MIME types. */
  public static ModeInfo findModeByMIME(String mime) {
    return byMime.get(mime);
  }

  public static SafeUri getModeScriptUri(String mode) {
    return modeUris.get(mode);
  }

  /** Look up mode by MIME type or file extension from a path. */
  public static ModeInfo findMode(String mime, String path) {
    ModeInfo m = byMime.get(mime);
    if (m != null) {
      return m;
    }

    int s = path.lastIndexOf('/');
    int d = path.lastIndexOf('.');
    if (d == -1 || s > d) {
      return null; // punt on "foo.src/bar" type paths.
    }

    if (byExt == null) {
      byExt = NativeMap.create();
      for (ModeInfo mode : Natives.asList(all())) {
        for (String ext : Natives.asList(mode.ext())) {
          byExt.put(ext, mode);
        }
      }
    }
    return byExt.get(path.substring(d + 1));
  }

  private static void alias(String serverMime, String toMime) {
    ModeInfo mode = byMime.get(toMime);
    if (mode != null) {
      byMime.put(serverMime, mode);
    }
  }

  private static void indexModes(DataResource[] all) {
    for (DataResource r : all) {
      modeUris.put(r.getName(), r.getSafeUri());
    }

    JsArray<ModeInfo> modeList = all();
    modeList.push(gerrit_commit());

    byMime = NativeMap.create();
    JsArray<ModeInfo> filtered = JsArray.createArray().cast();
    for (ModeInfo m : Natives.asList(modeList)) {
      if (modeUris.containsKey(m.mode())) {
        filtered.push(m);

        for (String mimeType : Natives.asList(m.mimes())) {
          byMime.put(mimeType, m);
        }
        byMime.put(m.mode(), m);
      }
    }
    Collections.sort(Natives.asList(filtered), new Comparator<ModeInfo>() {
      @Override
      public int compare(ModeInfo a, ModeInfo b) {
        return a.name().toLowerCase().compareTo(b.name().toLowerCase());
      }
    });
    setAll(filtered);
  }

  /** Human readable name of the mode, such as "C++". */
  public final native String name() /*-{ return this.name }-*/;

  /** Internal CodeMirror name for {@code mode.js} file to load. */
  public final native String mode() /*-{ return this.mode }-*/;

  /** Primary MIME type to activate this mode. */
  public final native String mime() /*-{ return this.mime }-*/;

  /** Primary and additional MIME types that activate this mode. */
  public final native JsArrayString mimes()
  /*-{ return this.mimes || [this.mime] }-*/;

  private final native JsArrayString ext()
  /*-{ return this.ext || [] }-*/;

  protected ModeInfo() {
  }

  private static native ModeInfo gerrit_commit() /*-{
    return {name: "Git Commit Message",
            mime: "text/x-gerrit-commit-message",
            mode: "gerrit_commit"}
  }-*/;
}
