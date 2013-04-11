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
import com.google.gwt.user.client.Window;

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
  /** Does the browser have ShockwaveFlash plugin enabled? */
  public static final boolean hasFlash = hasFlash();

  private static native boolean hasFlash()
  /*-{
    if (navigator.plugins && navigator.plugins.length) {
      if (navigator.plugins['Shockwave Flash'])     return true;
      if (navigator.plugins['Shockwave Flash 2.0']) return true;

    } else if (navigator.mimeTypes && navigator.mimeTypes.length) {
      var mimeType = navigator.mimeTypes['application/x-shockwave-flash'];
      if (mimeType && mimeType.enabledPlugin) return true;

    } else {
      try { new ActiveXObject('ShockwaveFlash.ShockwaveFlash.7'); return true; } catch (e) {}
      try { new ActiveXObject('ShockwaveFlash.ShockwaveFlash.6'); return true; } catch (e) {}
      try { new ActiveXObject('ShockwaveFlash.ShockwaveFlash');   return true; } catch (e) {}
    }
    return false;
  }-*/;

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
