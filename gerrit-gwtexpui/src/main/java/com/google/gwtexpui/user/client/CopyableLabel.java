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
import com.google.gwt.core.client.Scheduler;
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
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;

/** Label which permits the user to easily copy the complete content. */
public class CopyableLabel extends Composite implements HasText {
  interface Resources extends ClientBundle {
    static final Resources I = GWT.create(Resources.class);

    @Source("copyable_label.css")
    Css css();
  }

  interface Css extends CssResource {
    String label();
    String copier();
  }

  static {
    Resources.I.css().ensureInjected();
  }

  private final FlowPanel content;
  private String text;
  private int visibleLen;
  private Label textLabel;
  private TextBox textBox;
  private Button copier;

  public CopyableLabel() {
    this("");
  }

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
    initWidget(content);

    text = str;
    visibleLen = text.length();

    if (showLabel) {
      textLabel = new InlineLabel(getText());
      textLabel.setStyleName(Resources.I.css().label());
      textLabel.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          showTextBox();
        }
      });
      content.add(textLabel);
    }

    if (UserAgent.hasCopy) {
      copier = new Button("&#x1f4cb;"); // CLIPBOARD
      copier.setStyleName(Resources.I.css().copier());
      Tooltip.addStyle(copier);
      Tooltip.setLabel(copier, CopyableLabelText.I.tooltip());
      copier.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          copy();
        }
      });
      copier.addMouseOutHandler(new MouseOutHandler() {
        @Override
        public void onMouseOut(MouseOutEvent event) {
          Tooltip.setLabel(copier, CopyableLabelText.I.tooltip());
        }
      });
      content.add(copier);
    }
  }

  /**
   * Change the text which is displayed in the clickable label.
   *
   * @param text the new preview text, should be shorter than the original text
   *        which would be copied to the clipboard.
   */
  public void setPreviewText(String text) {
    if (textLabel != null) {
      textLabel.setText(text);
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
  }

  private void showTextBox() {
    if (textBox == null) {
      textBox = new TextBox();
      textBox.setText(getText());
      textBox.setVisibleLength(visibleLen);
      textBox.setReadOnly(true);
      textBox.addKeyPressHandler(new KeyPressHandler() {
        @Override
        public void onKeyPress(final KeyPressEvent event) {
          if (event.isControlKeyDown() || event.isMetaKeyDown()) {
            switch (event.getCharCode()) {
              case 'c':
              case 'x':
                textBox.addKeyUpHandler(new KeyUpHandler() {
                  @Override
                  public void onKeyUp(final KeyUpEvent event) {
                    Scheduler.get().scheduleDeferred(new Command() {
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
      textBox.addBlurHandler(new BlurHandler() {
        @Override
        public void onBlur(final BlurEvent event) {
          hideTextBox();
        }
      });
      content.insert(textBox, 1);
    }

    textLabel.setVisible(false);
    textBox.setVisible(true);
    Scheduler.get().scheduleDeferred(new Command() {
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
      t.selectAll();

      boolean ok = execCommand("copy");
      Tooltip.setLabel(copier, ok
          ? CopyableLabelText.I.copied()
          : CopyableLabelText.I.failed());
      if (!ok) {
        UserAgent.hasCopy = false;
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

  private static native boolean nativeExec(String c)
  /*-{ return !! $doc.execCommand(c) }-*/;
}
