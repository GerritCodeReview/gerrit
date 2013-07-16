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

package com.google.gerrit.client.api;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptException;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.impl.StackTraceCreator;

/**
 * Determines the name a plugin has been installed under.
 *
 * This implementation guesses the name a plugin runs under by looking at the
 * JavaScript call stack and identifying the URL of the script file calling
 * {@code Gerrit.install()}. The simple approach applied here is looking at
 * the source URLs and extracting the name out of the string, e.g.:
 * {@code "http://localhost:8080/plugins/{name}/static/foo.js"}.
 */
class PluginName {
  private static final String UNKNOWN = "<unknown>";

  static String get() {
    return GWT.<PluginName> create(PluginName.class).guessName();
  }

  String guessName() {
    JavaScriptException err = makeException();
    if (hasStack(err)) {
      return PluginNameMoz.guessName(err);
    }

    String baseUrl = baseUrl();
    StackTraceElement[] trace = getTrace(err);
    for (int i = trace.length - 1; i >= 0; i--) {
      String u = trace[i].getFileName();
      if (u != null && u.startsWith(baseUrl)) {
        int s = u.indexOf('/', baseUrl.length());
        if (s > 0) {
          return u.substring(baseUrl.length(), s);
        }
      }
    }
    return UNKNOWN;
  }

  private static String baseUrl() {
    return GWT.getHostPageBaseURL() + "plugins/";
  }

  private static StackTraceElement[] getTrace(JavaScriptException err) {
    StackTraceCreator.fillInStackTrace(err);
    return err.getStackTrace();
  }

  protected static final native JavaScriptException makeException()
  /*-{ try { null.a() } catch (e) { return e } }-*/;

  private static final native boolean hasStack(JavaScriptException e)
  /*-{ return !!e.stack }-*/;

  /** Extracts URL from the stack frame. */
  static class PluginNameMoz extends PluginName {
    String guessName() {
      return guessName(makeException());
    }

    static String guessName(JavaScriptException e) {
      String baseUrl = baseUrl();
      JsArrayString stack = getStack(e);
      for (int i = stack.length() - 1; i >= 0; i--) {
        String frame = stack.get(i);
        int at = frame.indexOf(baseUrl);
        if (at >= 0) {
          int s = frame.indexOf('/', at + baseUrl.length());
          if (s > 0) {
            return frame.substring(at + baseUrl.length(), s);
          }
        }
      }
      return UNKNOWN;
    }

    private static final native JsArrayString getStack(JavaScriptException e)
    /*-{ return e.stack ? e.stack.split('\n') : [] }-*/;
  }
}
