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

package com.google.gerrit.extensions.webui;

import java.net.URI;

/** Default result for {@link UiAction}s with no JavaScript. */
public class UiResult {
  /** Display an alert message to the user. */
  public static UiResult alert(String message) {
    UiResult r = new UiResult();
    r.alert = message;
    return r;
  }

  /** Launch URL in a new window. */
  public static UiResult openUrl(URI uri) {
    return openUrl(uri.toString());
  }

  /** Launch URL in a new window. */
  public static UiResult openUrl(String url) {
    UiResult r = new UiResult();
    r.url = url;
    r.openWindow = true;
    return r;
  }

  /** Redirect the browser to a new URL. */
  public static UiResult redirectUrl(String url) {
    UiResult r = new UiResult();
    r.url = url;
    return r;
  }

  /** Alert the user with a message. */
  protected String alert;

  /** If present redirect browser to this URL. */
  protected String url;

  /** When true open {@link #url} in a new tab/window. */
  protected Boolean openWindow;

  private UiResult() {}
}
