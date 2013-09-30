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

package com.google.gerrit.client.account;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;

public class AccountInfo extends JavaScriptObject {
  public final native int _account_id() /*-{ return this._account_id || 0; }-*/;
  public final native String name() /*-{ return this.name; }-*/;
  public final native String email() /*-{ return this.email; }-*/;

  /**
   * @return true if the server supplied avatar information about this account.
   *         The information may be an empty list, indicating no avatars are
   *         available, such as when no plugin is installed. This method returns
   *         false if the server did not check on avatars for the account.
   */
  public final native boolean has_avatar_info()
  /*-{ return this.hasOwnProperty('avatars') }-*/;

  public final AvatarInfo avatar(int sz) {
    JsArray<AvatarInfo> a = avatars();
    for (int i = 0; a != null && i < a.length(); i++) {
      AvatarInfo r = a.get(i);
      if (r.height() == sz) {
        return r;
      }
    }
    return null;
  }

  private final native JsArray<AvatarInfo> avatars()
  /*-{ return this.avatars }-*/;

  public static native AccountInfo create(int id, String name,
      String email) /*-{
    return {'_account_id': id, 'name': name, 'email': email};
  }-*/;

  protected AccountInfo() {
  }

  public static class AvatarInfo extends JavaScriptObject {
    public final static int DEFAULT_SIZE = 26;
    public final native String url() /*-{ return this.url }-*/;
    public final native int height() /*-{ return this.height || 0 }-*/;
    public final native int width() /*-{ return this.width || 0 }-*/;

    protected AvatarInfo() {
    }
  }
}
