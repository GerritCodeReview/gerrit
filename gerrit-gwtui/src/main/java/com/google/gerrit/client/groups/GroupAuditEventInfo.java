// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.client.groups;

import com.google.gerrit.client.info.AccountInfo;
import com.google.gerrit.client.info.GroupInfo;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwtjsonrpc.client.impl.ser.JavaSqlTimestamp_JsonSerializer;
import java.sql.Timestamp;

public class GroupAuditEventInfo extends JavaScriptObject {
  public enum Type {
    ADD_USER,
    REMOVE_USER,
    ADD_GROUP,
    REMOVE_GROUP
  }

  public final Timestamp date() {
    return JavaSqlTimestamp_JsonSerializer.parseTimestamp(dateRaw());
  }

  public final Type type() {
    return Type.valueOf(typeRaw());
  }

  public final native AccountInfo user() /*-{ return this.user; }-*/;

  public final native AccountInfo memberAsUser() /*-{ return this.member; }-*/;

  public final native GroupInfo memberAsGroup() /*-{ return this.member; }-*/;

  private native String dateRaw() /*-{ return this.date; }-*/;

  private native String typeRaw() /*-{ return this.type; }-*/;

  protected GroupAuditEventInfo() {}
}
