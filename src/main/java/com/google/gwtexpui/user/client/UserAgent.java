// Copyright 2009 Google Inc.
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

  private UserAgent() {
  }
}
