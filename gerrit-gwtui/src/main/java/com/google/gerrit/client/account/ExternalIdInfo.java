// Copyright (C) 2017 The Android Open Source Project
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

public class ExternalIdInfo extends JavaScriptObject {
  public final native String identity()
    /*-{ return this.identity; }-*/;
  public final native String emailAddress()
    /*-{ return this.email_address; }-*/;
  public final native boolean trusted()
    /*-{ return this['trusted'] ? true : false; }-*/;
  public final native boolean canDelete()
    /*-{ return this['can_delete'] ? true : false; }-*/;

  protected ExternalIdInfo() {
  }
}
