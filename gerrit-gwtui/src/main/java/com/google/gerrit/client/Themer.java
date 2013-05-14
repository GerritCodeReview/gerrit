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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.client;

import com.google.gerrit.client.projects.ThemeInfo;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.StyleElement;

public class Themer {
  public static class ThemerIE extends Themer {
    protected ThemerIE() {
    }

    @Override
    protected String getCssText(StyleElement el) {
      return el.getCssText();
    }

    @Override
    protected void setCssText(StyleElement el, String css) {
      el.setCssText(css);
    }
  }

  protected StyleElement cssElement;
  protected Element headerElement;
  protected Element footerElement;
  protected String cssText;
  protected String headerHtml;
  protected String footerHtml;

  protected Themer() {
  }

  public void set(ThemeInfo theme) {
    if (theme == null) {
      set(cssText, headerHtml, footerHtml);
    }
    set(theme.css() != null ? theme.css() : cssText,
        theme.header() != null ? theme.header() : headerHtml,
        theme.footer() != null ? theme.footer() : footerHtml);
  }

  public void clear() {
    set(null);
  }

  void init(Element css, Element header, Element footer) {
    cssElement = StyleElement.as(css);
    headerElement = header;
    footerElement = footer;

    cssText = getCssText(this.cssElement);
    headerHtml = header.getInnerHTML();
    footerHtml = footer.getInnerHTML();
  }

  protected String getCssText(StyleElement el) {
    return el.getInnerHTML();
  }

  protected void setCssText(StyleElement el, String css) {
    el.setInnerHTML(css);
  }

  private void set(String css, String header, String footer) {
    setCssText(cssElement, css);
    headerElement.setInnerHTML(header);
    footerElement.setInnerHTML(footer);
  }
}
