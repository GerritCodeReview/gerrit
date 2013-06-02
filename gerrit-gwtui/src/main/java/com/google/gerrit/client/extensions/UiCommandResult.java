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

package com.google.gerrit.client.extensions;

import com.google.gwt.core.client.JavaScriptObject;

public class UiCommandResult extends JavaScriptObject {
  public static enum Action {
    /* No action */
    NONE,
    /* Reload current page */
    RELOAD,
    /* Redirect request to location */
    REDIRECT,
  };

  public final native String message() /*-{ return this.message; }-*/;

  public final native String action() /*-{ return this.action; }-*/;

  public final native String location() /*-{ return this.location; }-*/;

  protected UiCommandResult() {
  }
}
