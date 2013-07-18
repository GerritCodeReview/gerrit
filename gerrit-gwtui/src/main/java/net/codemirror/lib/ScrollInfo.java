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

package net.codemirror.lib;

import com.google.gwt.core.client.JavaScriptObject;

/** {left, top, width, height, clientWidth, clientHeight} objects returned by
 * getScrollInfo(). */
public class ScrollInfo extends JavaScriptObject {
  public static ScrollInfo create() {
    return createObject().cast();
  }

  public final native double getLeft() /*-{ return this.left; }-*/;
  public final native double getTop() /*-{ return this.top; }-*/;
  public final native double getWidth() /*-{ return this.width; }-*/;
  public final native double getHeight() /*-{ return this.height; }-*/;
  public final native double getClientWidth() /*-{ return this.clientWidth; }-*/;
  public final native double getClientHeight() /*-{ return this.clientHeight; }-*/;

  protected ScrollInfo() {
  }
}
