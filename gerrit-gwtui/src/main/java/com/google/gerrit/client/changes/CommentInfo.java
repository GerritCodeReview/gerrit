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
import com.google.gerrit.common.changes.Side;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwtjsonrpc.client.impl.ser.JavaSqlTimestamp_JsonSerializer;

import java.sql.Timestamp;

public class CommentInfo extends JavaScriptObject {

  public final native String id() /*-{ return this.id; }-*/;
  public final native String path() /*-{ return this.path; }-*/;

  public final Side side() {
    String s = sideRaw();
    return s != null
        ? Side.valueOf(s)
        : Side.REVISION;
  }
  private final native String sideRaw() /*-{ return this.side }-*/;

  public final native int line() /*-{ return this.line; }-*/;
  public final native String in_reply_to() /*-{ return this.in_reply_to; }-*/;
  public final native String message() /*-{ return this.message; }-*/;

  public final Timestamp updated() {
    return JavaSqlTimestamp_JsonSerializer.parseTimestamp(updatedRaw());
  }
  private final native String updatedRaw() /*-{ return this.updated; }-*/;

  public final native AccountInfo author() /*-{ return this.author; }-*/;

  public final native boolean has_line() /*-{ return this.hasOwnProperty('line'); }-*/;

  protected CommentInfo() {
  }
}
