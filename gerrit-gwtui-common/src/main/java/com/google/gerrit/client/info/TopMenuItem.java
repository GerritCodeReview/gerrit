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

import com.google.gwt.core.client.JavaScriptObject;

public class TopMenuItem extends JavaScriptObject {
  public static TopMenuItem create(String name, String url) {
    TopMenuItem i = createObject().cast();
    i.name(name);
    i.url(url);
    return i;
  }

  public final native String getName() /*-{ return this.name; }-*/;

  public final native String getUrl() /*-{ return this.url; }-*/;

  public final native String getTarget() /*-{ return this.target; }-*/;

  public final native String getId() /*-{ return this.id; }-*/;

  public final native void name(String n) /*-{ this.name = n }-*/;

  public final native void url(String u) /*-{ this.url = u }-*/;

  protected TopMenuItem() {}
}
