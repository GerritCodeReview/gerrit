// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gwtexpui.user.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.SimpleEventBus;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Widget;

/**
 * User agent feature tests we don't create permutations for.
 * <p>
 * Some features aren't worth creating full permutations in GWT for, as each new
 * boolean permutation (only two settings) doubles the compile time required. If
 * the setting only affects a couple of lines of JavaScript code, the slightly
 * larger cache files for user agents that lack the functionality requested is
 * trivial compared to the time developers lose building their application.
 */
public class UserAgent {
  static final boolean hasCopy = hasCopy();
  private static final EventBus bus = new SimpleEventBus();

  private static boolean hasCopy() {
    String ua = userAgent();
    if (ua.indexOf("Safari/") >= 0 && ua.indexOf("Chrome/") < 0) {
      return false;
    }
    return true;
  }

  private static native String userAgent() /*-{ return navigator.userAgent }-*/;

  public static HandlerRegistration addDialogVisibleHandler(
      DialogVisibleHandler handler) {
    return bus.addHandler(DialogVisibleEvent.getType(), handler);
  }

  static void fireDialogVisible(Widget w, boolean visible) {
    bus.fireEvent(new DialogVisibleEvent(w, visible));
  }

  /**
   * Test for and disallow running this application in an &lt;iframe&gt;.
   * <p>
   * If the application is running within an iframe this method requests a
   * browser generated redirect to pop the application out of the iframe into
   * the top level window, and then aborts execution by throwing an exception.
   * This is call should be placed early within the module's onLoad() method,
   * before any real UI can be initialized that an attacking site could try to
   * snip out and present in a confusing context.
   * <p>
   * If the break out works, execution will restart automatically in a proper
   * top level window, where the script has full control over the display. If
   * the break out fails, execution will abort and stop immediately, preventing
   * UI widgets from being created, leaving the user with an empty frame.
   */
  public static void assertNotInIFrame() {
    if (GWT.isScript() && amInsideIFrame()) {
      bustOutOfIFrame(Window.Location.getHref());
      throw new RuntimeException();
    }
  }

  private static native boolean amInsideIFrame()
  /*-{ return top.location != $wnd.location; }-*/;

  private static native void bustOutOfIFrame(String newloc)
  /*-{ top.location.href = newloc }-*/;

  private UserAgent() {
  }
}
