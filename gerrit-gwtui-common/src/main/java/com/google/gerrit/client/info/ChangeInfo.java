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

package com.google.gerrit.client.info;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwtjsonrpc.client.impl.ser.JavaSqlTimestamp_JsonSerializer;

import java.sql.Timestamp;

public class ChangeInfo extends JavaScriptObject {

  public final Timestamp created() {
    Timestamp ts = _getCts();
    if (ts == null) {
      ts = JavaSqlTimestamp_JsonSerializer.parseTimestamp(createdRaw());
      _setCts(ts);
    }
    return ts;
  }

  private final native Timestamp _getCts() /*-{ return this._cts; }-*/;
  private final native void _setCts(Timestamp ts) /*-{ this._cts = ts; }-*/;

  private final native String createdRaw() /*-{ return this.created; }-*/;
}
