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

package com.google.gerrit.client.info;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayString;

public class GpgKeyInfo extends JavaScriptObject {
  public enum Status {
    BAD,
    OK,
    TRUSTED;
  }

  public final native String id() /*-{ return this.id; }-*/;

  public final native String fingerprint() /*-{ return this.fingerprint; }-*/;

  public final native JsArrayString userIds() /*-{ return this.user_ids; }-*/;

  public final native String key() /*-{ return this.key; }-*/;

  private native String statusRaw() /*-{ return this.status; }-*/;

  public final Status status() {
    String s = statusRaw();
    if (s == null) {
      return null;
    }
    return Status.valueOf(s);
  }

  public final native boolean hasProblems() /*-{ return this.hasOwnProperty('problems'); }-*/;

  public final native JsArrayString problems() /*-{ return this.problems; }-*/;

  protected GpgKeyInfo() {}
}
