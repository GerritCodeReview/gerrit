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

import net.codemirror.lib.ModeInjector;

import java.util.Collections;
import java.util.Comparator;

/** Description of a CodeMirror language mode. */
public class ModeInfo extends JavaScriptObject {
  private static NativeMap<ModeInfo> byMime;
  private static NativeMap<ModeInfo> byExt;

  /** All supported modes. */
  public static native JsArray<ModeInfo> all() /*-{
    return $wnd.CodeMirror.modeInfo
  }-*/;

  private static native void setAll(JsArray<ModeInfo> m) /*-{
    $wnd.CodeMirror.modeInfo = m
  }-*/;

  /** Lookup mode by primary or alternate MIME types. */
  public static ModeInfo findModeByMIME(String mime) {
    return byMime.get(mime);
  }

  /** Lookup mode by MIME type or file extension from a path. */
  public static ModeInfo findMode(String mime, String path) {
    ModeInfo m = byMime.get(mime);
    if (m != null) {
      return m;
    }

    int s = path.lastIndexOf('/');
    int d = path.lastIndexOf('.');
    if (s > d) {
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

  public static void buildMimeMap() {
    JsArray<ModeInfo> modeList = all();
    modeList.push(gerrit_commit());

    byMime = NativeMap.create();
    JsArray<ModeInfo> filtered = JsArray.createArray().cast();
    for (ModeInfo m : Natives.asList(modeList)) {
      if (ModeInjector.canLoad(m.mode())) {
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

  /** Alias an additional MIME type to this mode. */
  public final void addMime(String mimeType) {
    byMime.put(mimeType, this);
  }

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
