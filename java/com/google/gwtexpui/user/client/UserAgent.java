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
 *
 * <p>Some features aren't worth creating full permutations in GWT for, as each new boolean
 * permutation (only two settings) doubles the compile time required. If the setting only affects a
 * couple of lines of JavaScript code, the slightly larger cache files for user agents that lack the
 * functionality requested is trivial compared to the time developers lose building their
 * application.
 */
public class UserAgent {
  private static boolean jsClip = guessJavaScriptClipboard();

  public static boolean hasJavaScriptClipboard() {
    return jsClip;
  }

  public static void disableJavaScriptClipboard() {
    jsClip = false;
  }

  private static native boolean nativeHasCopy()
      /*-{ return $doc['queryCommandSupported'] && $doc.queryCommandSupported('copy') }-*/ ;

  private static boolean guessJavaScriptClipboard() {
    String ua = Window.Navigator.getUserAgent();
    int chrome = major(ua, "Chrome/");
    if (chrome > 0) {
      return 42 <= chrome;
    }

    int ff = major(ua, "Firefox/");
    if (ff > 0) {
      return 41 <= ff;
    }

    int opera = major(ua, "OPR/");
    if (opera > 0) {
      return 29 <= opera;
    }

    int msie = major(ua, "MSIE ");
    if (msie > 0) {
      return 9 <= msie;
    }

    if (nativeHasCopy()) {
      // Firefox 39.0 lies and says it supports copy, then fails.
      // So we try this after the browser specific test above.
      return true;
    }

    // Safari is not planning to support document.execCommand('copy').
    // Assume the browser does not have the feature.
    return false;
  }

  private static int major(String ua, String product) {
    int entry = ua.indexOf(product);
    if (entry >= 0) {
      String s = ua.substring(entry + product.length());
      String p = s.split("[ /;,.)]", 2)[0];
      try {
        return Integer.parseInt(p);
      } catch (NumberFormatException nan) {
        // Ignored
      }
    }
    return -1;
  }

  public static class Flash {
    private static boolean checked;
    private static boolean installed;

    /**
     * Does the browser have ShockwaveFlash plugin installed?
     *
     * <p>This method may still return true if the user has disabled Flash or set the plugin to
     * "click to run".
     */
    public static boolean isInstalled() {
      if (!checked) {
        installed = hasFlash();
        checked = true;
      }
      return installed;
    }

    private static native boolean hasFlash() /*-{
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
  }

  /**
   * Test for and disallow running this application in an &lt;iframe&gt;.
   *
   * <p>If the application is running within an iframe this method requests a browser generated
   * redirect to pop the application out of the iframe into the top level window, and then aborts
   * execution by throwing an exception. This is call should be placed early within the module's
   * onLoad() method, before any real UI can be initialized that an attacking site could try to snip
   * out and present in a confusing context.
   *
   * <p>If the break out works, execution will restart automatically in a proper top level window,
   * where the script has full control over the display. If the break out fails, execution will
   * abort and stop immediately, preventing UI widgets from being created, leaving the user with an
   * empty frame.
   */
  public static void assertNotInIFrame() {
    if (GWT.isScript() && amInsideIFrame()) {
      bustOutOfIFrame(Window.Location.getHref());
      throw new RuntimeException();
    }
  }

  private static native boolean amInsideIFrame() /*-{ return top.location != $wnd.location; }-*/;

  private static native void bustOutOfIFrame(String newloc) /*-{ top.location.href = newloc }-*/;

  /**
   * Test if Gerrit is running on a mobile browser. This check could be incomplete, but should cover
   * most cases. Regexes shamelessly borrowed from CodeMirror.
   */
  public static native boolean isMobile() /*-{
    var ua = $wnd.navigator.userAgent;
    var ios = /AppleWebKit/.test(ua) && /Mobile\/\w+/.test(ua);
    return ios
        || /Android|webOS|BlackBerry|Opera Mini|Opera Mobi|IEMobile/i.test(ua);
  }-*/;

  /** Check if the height of the browser view is greater than its width. */
  public static boolean isPortrait() {
    return Window.getClientHeight() > Window.getClientWidth();
  }

  private UserAgent() {}
}
