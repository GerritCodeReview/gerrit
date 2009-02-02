// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.ui;

import com.google.gwt.core.client.GWT;

/** Utilities for dealing with the DOM. */
public abstract class DomUtil {
  private static final Impl INSTANCE;

  static {
    if (GWT.isClient())
      INSTANCE = new ClientImpl();
    else
      INSTANCE = new JavaImpl();
  }

  /** Escape XML/HTML special characters in the input string. */
  public static String escape(final String in) {
    return INSTANCE.escape(in);
  }

  /** Convert bare URLs into &lt;a href&gt; tags. */
  public static String linkify(final String in) {
    return INSTANCE.linkify(in);
  }

  /** Do wiki style formatting to make it pretty */
  public static String wikify(String in) {
    in = INSTANCE.escape(in);
    in = INSTANCE.linkify(in);
    in = INSTANCE.wikify(in);
    return in;
  }

  private DomUtil() {
  }

  private static abstract class Impl {
    abstract String escape(String in);

    String wikify(String s) {
      s = s.replaceAll("(^|\n)([ \t][^\n]*)", "$1<span class=\"gerrit-preformat\">$2</span><br />");
      s = s.replaceAll("\n\n", "<p>\n");
      return s;
    }

    String linkify(String in) {
      return in.replaceAll("(https?://[^ \n\r\t]*)", "<a href=\"$1\">$1</a>");
    }
  }

  private static class ClientImpl extends Impl {
    @Override
    native String escape(String src)/*-{ return src.replace(/&/g,'&amp;').replace(/>/g,'&gt;').replace(/</g,'&lt;').replace(/"/g,'&quot;'); }-*/;
  }

  private static class JavaImpl extends Impl {
    @Override
    String escape(final String in) {
      final StringBuilder r = new StringBuilder(in.length());
      for (int i = 0; i < in.length(); i++) {
        final char c = in.charAt(i);
        switch (c) {
          case '&':
            r.append("&amp;");
            break;
          case '>':
            r.append("&gt;");
            break;
          case '<':
            r.append("&lt;");
            break;
          case '"':
            r.append("&quot;");
            break;
          default:
            r.append(c);
        }
      }
      return r.toString();
    }
  }
}
