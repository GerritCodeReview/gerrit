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

import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DeferredCommand;
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
  private static final CopyableTextImpl impl;

  static {
    if (UserAgent.hasFlash) {
      impl = new CopyableTextImplFlash();
    } else {
      impl = new CopyableTextImpl();
    }
  }

  private final FlowPanel content;
  private final String text;
  private Label textLabel;
  private TextBox textBox;

  public CopyableText(final String str) {
    this(str, true);
  }

  public CopyableText(final String str, final boolean showLabel) {
    content = new FlowPanel();
    content.setStyleName("gerrit-CopyableText");
    initWidget(content);

    text = str;
    if (showLabel) {
      textLabel = new InlineLabel(getText());
      textLabel.setStyleName("gerrit-CopyableText-Label");
      textLabel.addClickListener(this);
      content.add(textLabel);
    }
    impl.inject(this);
  }

  public String getText() {
    return text;
  }

  public void onClick(final Widget source) {
    if (textLabel == source) {
      showTextBox();
    }
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
