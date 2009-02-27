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

package com.google.gwtexpui.clippy.client;

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
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.KeyboardListenerAdapter;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import com.google.gwtexpui.user.client.UserAgent;

/**
 * Label which permits the user to easily copy the complete content.
 * <p>
 * If the Flash plugin is available a "movie" is embedded that provides
 * one-click copying of the content onto the system clipboard. The label (if
 * visible) can also be clicked, switching from a label to an input box,
 * allowing the user to copy the text with a keyboard shortcut.
 * <p>
 * Style name: <code>gwtexpui-Clippy</code>
 */
public class CopyableLabel extends Composite implements HasText {
  private static final int SWF_WIDTH = 110;
  private static final int SWF_HEIGHT = 14;
  private static String swfUrl;

  private static String swfUrl() {
    if (swfUrl == null) {
      swfUrl = GWT.getModuleBaseURL() + "gwtexpui_clippy1.cache.swf";
    }
    return swfUrl;
  }

  private final FlowPanel content;
  private String text;
  private Label textLabel;
  private TextBox textBox;
  private Element swf;

  /**
   * Create a new label
   * 
   * @param str initial content
   */
  public CopyableLabel(final String str) {
    this(str, true);
  }

  /**
   * Create a new label
   * 
   * @param str initial content
   * @param showLabel if true, the content is shown, if false it is hidden from
   *        view and only the copy icon is displayed.
   */
  public CopyableLabel(final String str, final boolean showLabel) {
    content = new FlowPanel();
    content.setStyleName("gwtexpui-Clippy");
    initWidget(content);

    text = str;
    if (showLabel) {
      textLabel = new InlineLabel(getText());
      textLabel.setStyleName("gwtexpui-Clippy-Label");
      textLabel.addClickListener(new ClickListener() {
        public void onClick(final Widget sender) {
          showTextBox();
        }
      });
      content.add(textLabel);
    }
    embedMovie();
  }

  private void embedMovie() {
    if (UserAgent.hasFlash) {
      final String flashVars = "text=" + URL.encodeComponent(getText());
      final SafeHtmlBuilder h = new SafeHtmlBuilder();

      h.openElement("span");
      h.setStyleName("gwtexpui-Clippy-Control");

      h.openElement("object");
      h.setWidth(SWF_WIDTH);
      h.setHeight(SWF_HEIGHT);
      h.setAttribute("classid", "clsid:d27cdb6e-ae6d-11cf-96b8-444553540000");
      h.paramElement("movie", swfUrl());
      h.paramElement("FlashVars", flashVars);

      h.openElement("embed");
      h.setWidth(SWF_WIDTH);
      h.setHeight(SWF_HEIGHT);
      h.setAttribute("type", "application/x-shockwave-flash");
      h.setAttribute("src", swfUrl());
      h.setAttribute("FlashVars", flashVars);
      h.closeSelf();

      h.closeElement("object");
      h.closeElement("span");

      if (swf != null) {
        DOM.removeChild(getElement(), swf);
      }
      DOM.appendChild(getElement(), swf = SafeHtml.parse(h));
    }
  }

  public String getText() {
    return text;
  }

  public void setText(final String newText) {
    text = newText;

    if (textLabel != null) {
      textLabel.setText(getText());
    }
    if (textBox != null) {
      textBox.setText(getText());
      textBox.selectAll();
    }
    embedMovie();
  }

  private void showTextBox() {
    if (textBox == null) {
      textBox = new TextBox();
      textBox.setText(getText());
      textBox.setVisibleLength(getText().length());
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
      textBox.removeFromParent();
      textBox = null;
    }
    textLabel.setVisible(true);
  }
}
