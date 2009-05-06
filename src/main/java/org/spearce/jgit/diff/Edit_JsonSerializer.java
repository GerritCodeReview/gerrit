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

package org.spearce.jgit.diff;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwtjsonrpc.client.JsonSerializer;

public class Edit_JsonSerializer extends JsonSerializer<Edit> {
  public static final Edit_JsonSerializer INSTANCE = new Edit_JsonSerializer();

  @Override
  public Edit fromJson(Object jso) {
    if (jso == null) {
      return null;
    }
    final JavaScriptObject o = (JavaScriptObject) jso;
    return new Edit(get(o, 0), get(o, 1), get(o, 2), get(o, 3));
  }

  @Override
  public void printJson(final StringBuilder sb, final Edit o) {
    sb.append('[');
    sb.append(o.getBeginA());
    sb.append(',');
    sb.append(o.getEndA());
    sb.append(',');
    sb.append(o.getBeginB());
    sb.append(',');
    sb.append(o.getEndB());
    sb.append(']');
  }

  private static native int get(JavaScriptObject jso, int idx)
  /*-{ return jso[idx]; }-*/;
}
