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

package com.google.gerrit.prettify.common;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwtjsonrpc.client.impl.JsonSerializer;

import java.util.ArrayList;
import java.util.List;

public class LineEdit_JsonSerializer extends JsonSerializer<LineEdit> {
  public static final LineEdit_JsonSerializer INSTANCE = new LineEdit_JsonSerializer();

  @Override
  public LineEdit fromJson(Object jso) {
    if (jso == null) {
      return null;
    }

    final JavaScriptObject o = (JavaScriptObject) jso;
    final int cnt = length(o);
    if (4 == cnt) {
      return new LineEdit(get(o, 0), get(o, 1), get(o, 2), get(o, 3));
    }

    List<BaseEdit> l = new ArrayList<BaseEdit>((cnt / 4) - 1);
    for (int i = 4; i < cnt;) {
      int as = get(o, i++);
      int ae = get(o, i++);
      int bs = get(o, i++);
      int be = get(o, i++);
      l.add(new BaseEdit(as, ae, bs, be));
    }
    return new LineEdit(get(o, 0), get(o, 1), get(o, 2), get(o, 3), l);
  }

  @Override
  public void printJson(final StringBuilder sb, final LineEdit o) {
    sb.append('[');
    append(sb, o);
    if (o.getEdits() != null) {
      for (BaseEdit e : o.getEdits()) {
        sb.append(',');
        append(sb, e);
      }
    }
    sb.append(']');
  }

  private void append(final StringBuilder sb, final BaseEdit o) {
    sb.append(o.getBeginA());
    sb.append(',');
    sb.append(o.getEndA());
    sb.append(',');
    sb.append(o.getBeginB());
    sb.append(',');
    sb.append(o.getEndB());
  }

  private static native int length(JavaScriptObject jso)
  /*-{ return jso.length; }-*/;

  private static native int get(JavaScriptObject jso, int idx)
  /*-{ return jso[idx]; }-*/;
}
