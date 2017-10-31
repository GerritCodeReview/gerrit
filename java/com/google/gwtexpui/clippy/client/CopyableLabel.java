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

package com.google.gwtexpui.clippy.client;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import com.google.gwtexpui.user.client.Tooltip;
import com.google.gwtexpui.user.client.UserAgent;

/**
 * Label which permits the user to easily copy the complete content.
 *
 * <p>If the Flash plugin is available a "movie" is embedded that provides one-click copying of the
 * content onto the system clipboard. The label (if visible) can also be clicked, switching from a
 * label to an input box, allowing the user to copy the text with a keyboard shortcut.
 */
public class CopyableLabel extends Composite implements HasText {
  private static final int SWF_WIDTH = 110;
  private static final int SWF_HEIGHT = 14;
  private static boolean flashEnabled = true;

  static {
    ClippyResources.I.css().ensureInjected();
  }

  public static boolean isFlashEnabled() {
    return flashEnabled;
  }

  public static void setFlashEnabled(boolean on) {
    flashEnabled = on;
  }

  private static String swfUrl() {
    return ClippyResources.I.swf().getSafeUri().asString();
  }

  private final FlowPanel content;
  private String text;
  private int visibleLen;
  private Label textLabel;
  private TextBox textBox;
  private Button copier;
  private Element swf;

  public CopyableLabel() {
    this("");
  }

  /**
   * Create a new label
   *
   * @param str initial content
   */
  public CopyableLabel(String str) {
    this(str, true);
  }

  /**
   * Create a new label
   *
   * @param str initial content
   * @param showLabel if true, the content is shown, if false it is hidden from view and only the
   *     copy icon is displayed.
   */
  public CopyableLabel(String str, boolean showLabel) {
    content = new FlowPanel();
    initWidget(content);

    text = str;
    visibleLen = text.length();

    if (showLabel) {
      textLabel = new InlineLabel(getText());
      textLabel.setStyleName(ClippyResources.I.css().label());
      textLabel.addClickHandler(
          new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
              showTextBox();
            }
          });
      content.add(textLabel);
    }

    if (UserAgent.hasJavaScriptClipboard()) {
      copier =
          new Button(
              new SafeHtmlBuilder()
                  .openElement("img")
                  .setAttribute("src", ClippyResources.I.clipboard().getSafeUri().asString())
                  .setWidth(14)
                  .setHeight(14)
                  .closeSelf());
      copier.setStyleName(ClippyResources.I.css().copier());
      Tooltip.addStyle(copier);
      Tooltip.setLabel(copier, CopyableLabelText.I.tooltip());
      copier.addClickHandler(
          new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
              copy();
            }
          });
      copier.addMouseOutHandler(
          new MouseOutHandler() {
            @Override
            public void onMouseOut(MouseOutEvent event) {
              Tooltip.setLabel(copier, CopyableLabelText.I.tooltip());
            }
          });

      FlowPanel p = new FlowPanel();
      p.getElement().getStyle().setDisplay(Display.INLINE_BLOCK);
      p.add(copier);
      content.add(p);
    } else {
      embedMovie();
    }
  }

  /**
   * Change the text which is displayed in the clickable label.
   *
   * @param text the new preview text, should be shorter than the original text which would be
   *     copied to the clipboard.
   */
  public void setPreviewText(String text) {
    if (textLabel != null) {
      textLabel.setText(text);
    }
  }

  private void embedMovie() {
    if (copier == null && flashEnabled && !text.isEmpty() && UserAgent.Flash.isInstalled()) {
      final String flashVars = "text=" + URL.encodeQueryString(getText());
      final SafeHtmlBuilder h = new SafeHtmlBuilder();

      h.openElement("div");
      h.setStyleName(ClippyResources.I.css().swf());

      h.openElement("object");
      h.setWidth(SWF_WIDTH);
      h.setHeight(SWF_HEIGHT);
      h.setAttribute("classid", "clsid:d27cdb6e-ae6d-11cf-96b8-444553540000");
      h.paramElement("movie", swfUrl());
      h.paramElement("FlashVars", flashVars);

      h.openElement("embed");
      h.setWidth(SWF_WIDTH);
      h.setHeight(SWF_HEIGHT);
      h.setAttribute("wmode", "transparent");
      h.setAttribute("type", "application/x-shockwave-flash");
      h.setAttribute("src", swfUrl());
      h.setAttribute("FlashVars", flashVars);
      h.closeSelf();

      h.closeElement("object");
      h.closeElement("div");

      if (swf != null) {
        getElement().removeChild(swf);
      }
      DOM.appendChild(getElement(), swf = SafeHtml.parse(h));
    }
  }

  @Override
  public String getText() {
    return text;
  }

  @Override
  public void setText(String newText) {
    text = newText;
    visibleLen = newText.length();

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
      textBox.setVisibleLength(visibleLen);
      textBox.setReadOnly(true);
      textBox.addKeyPressHandler(
          new KeyPressHandler() {
            @Override
            public void onKeyPress(KeyPressEvent event) {
              if (event.isControlKeyDown() || event.isMetaKeyDown()) {
                switch (event.getCharCode()) {
                  case 'c':
                  case 'x':
                    textBox.addKeyUpHandler(
                        new KeyUpHandler() {
                          @Override
                          public void onKeyUp(KeyUpEvent event) {
                            Scheduler.get()
                                .scheduleDeferred(
                                    new Command() {
                                      @Override
                                      public void execute() {
                                        hideTextBox();
                                      }
                                    });
                          }
                        });
                    break;
                }
              }
            }
          });
      textBox.addBlurHandler(
          new BlurHandler() {
            @Override
            public void onBlur(BlurEvent event) {
              hideTextBox();
            }
          });
      content.insert(textBox, 1);
    }

    textLabel.setVisible(false);
    textBox.setVisible(true);
    Scheduler.get()
        .scheduleDeferred(
            new Command() {
              @Override
              public void execute() {
                textBox.selectAll();
                textBox.setFocus(true);
              }
            });
  }

  private void hideTextBox() {
    if (textBox != null) {
      textBox.removeFromParent();
      textBox = null;
    }
    textLabel.setVisible(true);
  }

  private void copy() {
    TextBox t = new TextBox();
    try {
      t.setText(getText());
      content.add(t);
      t.setFocus(true);
      t.selectAll();

      boolean ok = execCommand("copy");
      Tooltip.setLabel(copier, ok ? CopyableLabelText.I.copied() : CopyableLabelText.I.failed());
      if (!ok) {
        // Disable JavaScript clipboard and try flash movie in another instance.
        UserAgent.disableJavaScriptClipboard();
      }
    } finally {
      t.removeFromParent();
    }
  }

  private static boolean execCommand(String command) {
    try {
      return nativeExec(command);
    } catch (Exception e) {
      return false;
    }
  }

  private static native boolean nativeExec(String c) /*-{ return !! $doc.execCommand(c) }-*/;
}
