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

package com.google.gerrit.client.info;

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.http.client.URL;

public class GroupInfo extends GroupBaseInfo {
  public final AccountGroup.Id getGroupId() {
    return new AccountGroup.Id(group_id());
  }

  public final native GroupOptionsInfo options() /*-{ return this.options; }-*/;

  public final native String description() /*-{ return this.description; }-*/;

  public final native String url() /*-{ return this.url; }-*/;

  public final native String owner() /*-{ return this.owner; }-*/;

  public final native void owner(String o) /*-{ if(o)this.owner=o; }-*/;

  public final native JsArray<AccountInfo> members() /*-{ return this.members; }-*/;

  public final native JsArray<GroupInfo> includes() /*-{ return this.includes; }-*/;

  private native int group_id() /*-{ return this.group_id; }-*/;

  private native String owner_id() /*-{ return this.owner_id; }-*/;

  private native void owner_id(String o) /*-{ if(o)this.owner_id=o; }-*/;

  public final AccountGroup.UUID getOwnerUUID() {
    String owner = owner_id();
    if (owner != null) {
      return new AccountGroup.UUID(URL.decodeQueryString(owner));
    }
    return null;
  }

  public final void setOwnerUUID(AccountGroup.UUID uuid) {
    owner_id(URL.encodeQueryString(uuid.get()));
  }

  protected GroupInfo() {}

  public static class GroupOptionsInfo extends JavaScriptObject {
    public final native boolean
        isVisibleToAll() /*-{ return this['visible_to_all'] ? true : false; }-*/;

    protected GroupOptionsInfo() {}
  }
}
