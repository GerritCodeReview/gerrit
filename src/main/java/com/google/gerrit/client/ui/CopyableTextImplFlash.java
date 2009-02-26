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

package com.google.gerrit.client.ui;

import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;

class CopyableTextImplFlash extends CopyableTextImpl {
  private static final int SWF_WIDTH = 110;
  private static final int SWF_HEIGHT = 14;
  private static final String swfUrl =
      GWT.getModuleBaseURL() + "clippy1.cache.swf";

  @Override
  void inject(final CopyableText widget) {
    final String flashVars = "text=" + URL.encodeComponent(widget.getText());
    final StringBuilder html = new StringBuilder();

    final Element span = DOM.createSpan();
    DOM.setElementProperty(span, "className", "gerrit-CopyableText-SWF");
    html.append("<object");
    html.append(" classid=\"clsid:d27cdb6e-ae6d-11cf-96b8-444553540000\"");
    html.append(" width=\"" + SWF_WIDTH + "\"");
    html.append(" height=\"" + SWF_HEIGHT + "\"");
    html.append(">");
    param(html, "movie", swfUrl);
    param(html, "FlashVars", flashVars);

    html.append("<embed ");
    html.append(" type=\"application/x-shockwave-flash\"");
    html.append(" width=\"" + SWF_WIDTH + "\"");
    html.append(" height=\"" + SWF_HEIGHT + "\"");
    attribute(html, "src", swfUrl);
    attribute(html, "FlashVars", flashVars);
    html.append("/>");

    html.append("</object>");

    DOM.setInnerHTML(span, html.toString());
    DOM.appendChild(widget.getElement(), span);
  }

  private static void param(StringBuilder html, String name, String value) {
    html.append("<param");
    attribute(html, "name", name);
    attribute(html, "value", value);
    html.append("/>");
  }

  private static void attribute(StringBuilder html, String name, String value) {
    html.append(" ");
    html.append(name);
    html.append("=\"");
    html.append(DomUtil.escape(value));
    html.append("\"");
  }
}
