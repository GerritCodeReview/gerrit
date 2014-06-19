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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.account.AccountInfo;
import com.google.gerrit.client.diff.CommentRange;
import com.google.gerrit.extensions.common.Side;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwtjsonrpc.client.impl.ser.JavaSqlTimestamp_JsonSerializer;

import java.sql.Timestamp;

public class CommentInfo extends JavaScriptObject {
  public static CommentInfo create(String path, Side side,
      int line, CommentRange range) {
    CommentInfo n = createObject().cast();
    n.path(path);
    n.side(side);
    if (range != null) {
      n.line(range.end_line());
      n.range(range);
    } else if (line > 0) {
      n.line(line);
    }
    return n;
  }

  public static CommentInfo createReply(CommentInfo r) {
    CommentInfo n = createObject().cast();
    n.path(r.path());
    n.side(r.side());
    n.in_reply_to(r.id());
    if (r.has_range()) {
      n.line(r.range().end_line());
      n.range(r.range());
    } else if (r.has_line()) {
      n.line(r.line());
    }
    return n;
  }

  public static CommentInfo copy(CommentInfo s) {
    CommentInfo n = createObject().cast();
    n.path(s.path());
    n.side(s.side());
    n.id(s.id());
    n.in_reply_to(s.in_reply_to());
    n.message(s.message());
    if (s.has_range()) {
      n.line(s.range().end_line());
      n.range(s.range());
    } else if (s.has_line()) {
      n.line(s.line());
    }
    return n;
  }

  public final native void path(String p) /*-{ this.path = p }-*/;
  public final native void id(String i) /*-{ this.id = i }-*/;
  public final native void line(int n) /*-{ this.line = n }-*/;
  public final native void range(CommentRange r) /*-{ this.range = r }-*/;
  public final native void in_reply_to(String i) /*-{ this.in_reply_to = i }-*/;
  public final native void message(String m) /*-{ this.message = m }-*/;

  public final void side(Side side) {
    sideRaw(side.toString());
  }
  private final native void sideRaw(String s) /*-{ this.side = s }-*/;

  public final native String path() /*-{ return this.path }-*/;
  public final native String id() /*-{ return this.id }-*/;
  public final native String in_reply_to() /*-{ return this.in_reply_to }-*/;

  public final Side side() {
    String s = sideRaw();
    return s != null
        ? Side.valueOf(s)
        : Side.REVISION;
  }
  private final native String sideRaw() /*-{ return this.side }-*/;

  public final Timestamp updated() {
    Timestamp r = updatedTimestamp();
    if (r == null) {
      String s = updatedRaw();
      if (s != null) {
        r = JavaSqlTimestamp_JsonSerializer.parseTimestamp(s);
        updatedTimestamp(r);
      }
    }
    return r;
  }
  private final native String updatedRaw() /*-{ return this.updated }-*/;
  private final native Timestamp updatedTimestamp() /*-{ return this._ts }-*/;
  private final native void updatedTimestamp(Timestamp t) /*-{ this._ts = t }-*/;

  public final native AccountInfo author() /*-{ return this.author }-*/;
  public final native int line() /*-{ return this.line || 0 }-*/;
  public final native boolean has_line() /*-{ return this.hasOwnProperty('line') }-*/;
  public final native boolean has_range() /*-{ return this.hasOwnProperty('range') }-*/;
  public final native CommentRange range() /*-{ return this.range }-*/;
  public final native String message() /*-{ return this.message }-*/;

  protected CommentInfo() {
  }
}
