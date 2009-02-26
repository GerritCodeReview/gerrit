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
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusListener;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.KeyboardListenerAdapter;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;

public class CopyableText extends Composite implements ClickListener {
  private static final int SWF_WIDTH = 110;
  private static final int SWF_HEIGHT = 14;
  private static final String swfUrl =
      GWT.getModuleBaseURL() + "clippy1.cache.swf";

  private final FlowPanel content;
  private Label textLabel;
  private TextBox textBox;

  public CopyableText(final String str) {
    this(str, true);
  }

  public CopyableText(final String str, final boolean showLabel) {
    content = new FlowPanel();
    content.setStyleName("gerrit-CopyableText");
    initWidget(content);

    final String flashVars = "text=" + URL.encodeComponent(str);
    final StringBuilder html = new StringBuilder();

    if (showLabel) {
      textLabel = new InlineLabel(str);
      textLabel.setStyleName("gerrit-CopyableText-Label");
      textLabel.addClickListener(this);
      content.add(textLabel);
    }

    final Element span = DOM.createSpan();
    setStyleName(span, "gerrit-CopyableText-SWF");
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
    DOM.appendChild(content.getElement(), span);
  }

  public void onClick(final Widget source) {
    if (textLabel == source) {
      showTextBox();
    }
  }

  private void showTextBox() {
    if (textBox == null) {
      textBox = new TextBox();
      textBox.setText(textLabel.getText());
      textBox.setVisibleLength(textBox.getText().length());
      textBox.addKeyboardListener(new KeyboardListenerAdapter() {
        @Override
        public void onKeyPress(final Widget sender, final char kc, final int mod) {
          if ((mod & MODIFIER_CTRL) == MODIFIER_CTRL
              || (mod & MODIFIER_META) == MODIFIER_META) {
            switch (kc) {
              case 'c':
              case 'x':
                DeferredCommand.addCommand(new Command() {
                  public void execute() {
                    hideTextBox();
                  }
                });
                break;
            }
          }
        }
      });
      textBox.addFocusListener(new FocusListener() {
        public void onFocus(Widget arg0) {
        }

        public void onLostFocus(Widget arg0) {
          hideTextBox();
        }
      });
      content.insert(textBox, 1);
    }

    textLabel.setVisible(false);
    textBox.setVisible(true);
    textBox.selectAll();
    textBox.setFocus(true);
  }

  private void hideTextBox() {
    if (textBox != null) {
      textBox.setText(textLabel.getText());
      textBox.removeFromParent();
      textBox = null;
    }
    textLabel.setVisible(true);
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
