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

package com.google.gerrit.client.groups;

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gwt.core.client.JavaScriptObject;

public class GroupInfo extends JavaScriptObject {
  public final AccountGroup.Id getGroupId() {
    return new AccountGroup.Id(groupId());
  }

  public final native int groupId() /*-{ return this.group_id; }-*/;
  public final native String name() /*-{ return this.name; }-*/;
  public final native String uuid() /*-{ return this.uuid; }-*/;
  public final native String description() /*-{ return this.description; }-*/;
  public final native boolean isVisibleToAll() /*-{ return this['is_visible_to_all'] ? true : false; }-*/;
  public final native String ownerUuid() /*-{ return this.owner_uuid; }-*/;

  protected GroupInfo() {
  }
}
