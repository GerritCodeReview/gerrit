// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gwtexpui.safehtml.client;

public class Prettify {
  private static boolean loaded = isLoaded();

  private static native boolean isLoaded()
  /*-{ return $wnd['prettyPrintOne'] != null }-*/;

  public static SafeHtml prettify(SafeHtml src, String srcType) {
    if (loaded) {
      src = src.replaceAll("&#39;", "'");
      src = SafeHtml.asis(prettifyNative(src.asString(), srcType));
    }
    return src;
  }

  private static native String prettifyNative(String srcText, String srcType)
  /*-{ return $wnd.prettyPrintOne(srcText, srcType); }-*/;

  private Prettify() {
  }
}
