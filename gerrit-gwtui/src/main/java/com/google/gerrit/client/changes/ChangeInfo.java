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

package com.google.gerrit.client.changes;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwtjsonrpc.client.impl.ser.JavaSqlTimestamp_JsonSerializer;

import java.sql.Timestamp;

public class ChangeInfo extends JavaScriptObject {
  protected ChangeInfo() {
  }

  public final Project.NameKey project_name_key() {
    return new Project.NameKey(project());
  }

  public final Change.Id legacy_id() {
    return new Change.Id(_number());
  }

  public final Timestamp updated() {
    return JavaSqlTimestamp_JsonSerializer.parseTimestamp(updatedRaw());
  }

  public final String id_abbreviated() {
    return new Change.Key(id()).abbreviate();
  }

  public final Change.Status status() {
    return Change.Status.valueOf(statusRaw());
  }

  public final native String project() /*-{ return this.project; }-*/;
  public final native String branch() /*-{ return this.branch; }-*/;
  public final native String topic() /*-{ return this.topic; }-*/;
  public final native String id() /*-{ return this.id; }-*/;
  private final native String statusRaw() /*-{ return this.status; }-*/;
  public final native String subject() /*-{ return this.subject; }-*/;
  public final native AccountInfo owner() /*-{ return this.owner; }-*/;
  private final native String updatedRaw() /*-{ return this.updated; }-*/;
  public final native boolean starred() /*-{ return this.starred; }-*/;
  public final native String _sortkey() /*-{ return this._sortkey; }-*/;
  final native int _number() /*-{ return this._number; }-*/;
  final native boolean _more_changes() /*-{ return this._more_changes; }-*/;

  // TODO(sop): Compute isLatest signal where necessary.
  final boolean isLatest() { return true; }

  public static class AccountInfo extends JavaScriptObject {
    protected AccountInfo() {
    }

    public final native String name() /*-{ return this.name; }-*/;
  }
}
