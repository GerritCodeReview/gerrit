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

public class EmailInfo extends JavaScriptObject {
  public final native String email() /*-{ return this.email; }-*/;

  public final native boolean isPreferred() /*-{ return this['preferred'] ? true : false; }-*/;

  public final native boolean
      isConfirmationPending() /*-{ return this['pending_confirmation'] ? true : false; }-*/;

  protected EmailInfo() {}
}
