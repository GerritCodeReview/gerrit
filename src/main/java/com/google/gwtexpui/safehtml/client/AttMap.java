// Copyright (C) 2009 The Android Open Source Project
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

import java.util.ArrayList;
import java.util.HashMap;

/** Lightweight map of names/values for element attribute construction. */
class AttMap {
  private static final Tag ANY = new AnyTag();
  private static final HashMap<String, Tag> TAGS;
  static {
    final Tag src = new SrcTag();
    TAGS = new HashMap<String, Tag>();
    TAGS.put("a", new AnchorTag());
    TAGS.put("form", new FormTag());
    TAGS.put("img", src);
    TAGS.put("script", src);
    TAGS.put("frame", src);
  }

  private final ArrayList<String> names = new ArrayList<String>();
  private final ArrayList<String> values = new ArrayList<String>();

  private Tag tag = ANY;
  private int live;

  void reset(final String tagName) {
    tag = TAGS.get(tagName.toLowerCase());
    if (tag == null) {
      tag = ANY;
    }
    live = 0;
  }

  void onto(final Buffer raw, final SafeHtmlBuilder esc) {
    for (int i = 0; i < live; i++) {
      final String v = values.get(i);
      if (v.length() > 0) {
        raw.append(" ");
        raw.append(names.get(i));
        raw.append("=\"");
        esc.append(v);
        raw.append("\"");
      }
    }
  }

  String get(String name) {
    name = name.toLowerCase();

    for (int i = 0; i < live; i++) {
      if (name.equals(names.get(i))) {
        return values.get(i);
      }
    }
    return "";
  }

  void set(String name, final String value) {
    name = name.toLowerCase();
    tag.assertSafe(name, value);

    for (int i = 0; i < live; i++) {
      if (name.equals(names.get(i))) {
        values.set(i, value);
        return;
      }
    }

    final int i = live++;
    if (names.size() < live) {
      names.add(name);
      values.add(value);
    } else {
      names.set(i, name);
      values.set(i, value);
    }
  }

  private static void assertNotJavascriptUrl(final String value) {
    if (value.startsWith("#")) {
      // common in GWT, and safe, so bypass further checks

    } else if (value.trim().toLowerCase().startsWith("javascript:")) {
      // possibly unsafe, we could have random user code here
      // we can't tell if its safe or not so we refuse to accept
      //
      throw new RuntimeException("javascript unsafe in href: " + value);
    }
  }

  private static interface Tag {
    void assertSafe(String name, String value);
  }

  private static class AnyTag implements Tag {
    public void assertSafe(String name, String value) {
    }
  }

  private static class AnchorTag implements Tag {
    public void assertSafe(String name, String value) {
      if ("href".equals(name)) {
        assertNotJavascriptUrl(value);
      }
    }
  }

  private static class FormTag implements Tag {
    public void assertSafe(String name, String value) {
      if ("action".equals(name)) {
        assertNotJavascriptUrl(value);
      }
    }
  }

  private static class SrcTag implements Tag {
    public void assertSafe(String name, String value) {
      if ("src".equals(name)) {
        assertNotJavascriptUrl(value);
      }
    }
  }
}
