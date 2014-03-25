// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.client.extensions.TopMenuItem;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;

import java.util.List;

public class Preferences extends JavaScriptObject {
  public static Preferences create(List<TopMenuItem> myMenus) {
    Preferences p = createObject().cast();
    p.setMyMenus(myMenus);
    return p;
  }

  public final native JsArray<TopMenuItem> my() /*-{ return this.my; }-*/;

  final void setMyMenus(List<TopMenuItem> myMenus) {
    initMy();
    for (TopMenuItem n : myMenus) {
      addMy(n);
    }

  }
  final native void initMy() /*-{ this.my = []; }-*/;
  final native void addMy(TopMenuItem m) /*-{ this.my.push(m); }-*/;

  protected Preferences() {
  }
}
