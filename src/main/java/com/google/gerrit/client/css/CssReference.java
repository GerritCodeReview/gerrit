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

package com.google.gerrit.client.css;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.LinkElement;

/** Reference to a CSS file under the module base. */
public class CssReference {
  private final String url;

  /**
   * Create a new reference to a CSS file.
   * 
   * @param name name of the CSS file, under the module base. Typically this is
   *        a MD-5 or SHA-1 hash of the file content, with
   *        <code>.cache.css</code> suffix, to permit long-lived caching of the
   *        resource by browers and edge caches.
   */
  public CssReference(final String name) {
    url = GWT.getModuleBaseURL() + name;
  }

  /** Inject this resource into the document as a style sheet. */
  public void inject() {
    final LinkElement style = Document.get().createLinkElement();
    style.setRel("stylesheet");
    style.setHref(url);
    Document.get().getElementsByTagName("head").getItem(0).appendChild(style);
  }
}
