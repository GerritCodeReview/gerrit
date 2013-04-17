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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gwt.core.client.JavaScriptObject;

public class AccountInfo extends JavaScriptObject {
  public final Account.Id getAccountId() {
    return new Account.Id(_account_id());
  }

  public final native int _account_id() /*-{ return this._account_id || 0; }-*/;
  public final native String name() /*-{ return this.name; }-*/;
  public final native String email() /*-{ return this.email; }-*/;

  public static native AccountInfo create(int id, String name,
      String email) /*-{
    return {'_account_id': id, 'name': name, 'email': email};
  }-*/;

  protected AccountInfo() {
  }
}
