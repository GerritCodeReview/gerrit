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

/** Returned by {@link CodeMirror#getScrollInfo()}. */
public class ScrollInfo extends JavaScriptObject {
  public final native double left() /*-{ return this.left }-*/;

  public final native double top() /*-{ return this.top }-*/;

  /**
   * Pixel height of the full content being scrolled. This may only be an estimate given by
   * CodeMirror. Line widgets further down in the document may not be measured, so line heights can
   * be incorrect until drawn.
   */
  public final native double height() /*-{ return this.height }-*/;

  public final native double width() /*-{ return this.width }-*/;

  /** Visible height of the viewport, excluding scrollbars. */
  public final native double clientHeight() /*-{ return this.clientHeight }-*/;

  public final native double clientWidth() /*-{ return this.clientWidth }-*/;

  protected ScrollInfo() {}
}
