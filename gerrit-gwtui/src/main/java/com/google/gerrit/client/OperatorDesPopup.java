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

package com.google.gerrit.client;

import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwtexpui.globalkey.client.KeyResources;

import java.util.Map;

public class OperatorDesPopup extends PopupPanel implements
  KeyPressHandler{

  private final FocusPanel focus;
  private final FlowPanel body = new FlowPanel();

  public OperatorDesPopup(Map<String, String> desMap, String keyName) {
    super(true/* autohide */, true/* modal */);
    setStyleName(KeyResources.I.css().OperatorDesPopup());

    addLabel(desMap.get(keyName + "Des"));

    if(desMap.get(keyName + "FormatNum") != null) {
      addHeader(OperatorConstants.O.format());
      int num = (desMap.get(keyName + "FormatNum").length() - 1);
      for(int i = 0; i < num; i += 1) {
        addLabel(desMap.get(keyName + "Format" + Integer.toString(i)));
      }
    } else if(desMap.get(keyName + "Format") != null) {
      addHeader(OperatorConstants.O.format());
      addLabel(desMap.get(keyName + "Format"));
    }

    if(desMap.get(keyName + "ConstNum") != null) {
      int num = (desMap.get(keyName + "ConstNum").length() - 1);
      addHeader(OperatorConstants.O.constraints());
      for(int i = 0; i < num; i += 1) {
        addLabel(desMap.get(keyName + "Const" + Integer.toString(i)));
      }
    } else if (desMap.get(keyName + "Const") != null) {
      addHeader(OperatorConstants.O.constraints());
      addLabel(desMap.get(keyName + "Const"));
    }
    focus = new FocusPanel(body);
    focus.addKeyPressHandler(this);
    focus.getElement().getStyle().setProperty("outline", "0px");
    focus.getElement().setAttribute("hideFocus", "true");
    add(focus);
  }

  private Label addLabel(String text) {
    final Label label = new Label(text);
    label.setStyleName(KeyResources.I.css().OperatorDes());
    body.add(label);
    return label;
  }

  private Label addHeader(String text) {
    final Label label = new Label(text);
    label.setStyleName(KeyResources.I.css().OperatorHeader());
    body.add(label);
    return label;
  }

  @Override
  public void onKeyPress(KeyPressEvent event) {
    hide();
  }
}