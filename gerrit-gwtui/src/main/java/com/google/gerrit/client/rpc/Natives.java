// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.client.rpc;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.json.client.JSONObject;

import java.util.Collections;
import java.util.Set;

public class Natives {
  /**
   * Get the names of defined properties on the object. The returned set
   * iterates in the native iteration order, which may match the source order.
   */
  public static Set<String> keys(JavaScriptObject obj) {
    if (obj != null) {
      return new JSONObject(obj).keySet();
    }
    return Collections.emptySet();
  }

  public static <T extends JavaScriptObject> T parseJSON(String json) {
    if (parser == null) {
      parser = bestJsonParser();
    }
    return parse0(parser, json);
  }

  private static native <T extends JavaScriptObject>
  T parse0(JavaScriptObject p, String s)
  /*-{ return p(s); }-*/;

  private static JavaScriptObject parser;
  private static native JavaScriptObject bestJsonParser()
  /*-{
    if ($wnd.JSON && typeof $wnd.JSON.parse === 'function')
      return $wnd.JSON.parse;
    return function(s) { return eval('(' + s + ')'); };
  }-*/;

  private Natives() {
  }
}
