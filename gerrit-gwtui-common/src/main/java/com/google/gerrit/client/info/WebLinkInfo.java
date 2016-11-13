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

package com.google.gerrit.client.info;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Image;

public class WebLinkInfo extends JavaScriptObject {

  public final native String name() /*-{ return this.name; }-*/;

  public final native String imageUrl() /*-{ return this.image_url; }-*/;

  public final native String url() /*-{ return this.url; }-*/;

  public final native String target() /*-{ return this.target; }-*/;

  protected WebLinkInfo() {}

  public final Anchor toAnchor() {
    Anchor a = new Anchor();
    a.setHref(url());
    if (target() != null && !target().isEmpty()) {
      a.setTarget(target());
    }
    if (imageUrl() != null && !imageUrl().isEmpty()) {
      Image img = new Image();
      img.setAltText(name());
      img.setUrl(imageUrl());
      img.setTitle(name());
      a.getElement().appendChild(img.getElement());
    } else {
      a.setText("(" + name() + ")");
    }
    return a;
  }
}
