// Copyright (C) 2008 The Android Open Source Project
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

import com.google.gerrit.client.rpc.RpcConstants;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.rpc.StatusCodeException;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.user.client.AutoCenterDialogBox;
import com.google.gwtjsonrpc.client.RemoteJsonException;

/** A dialog box showing an error message, when bad things happen. */
public class ErrorDialog extends AutoCenterDialogBox {
  private final FlowPanel body;

  protected ErrorDialog() {
    super(/* auto hide */true, /* modal */true);
    setText(Gerrit.C.errorDialogTitle());

    body = new FlowPanel();
    final FlowPanel buttons = new FlowPanel();
    buttons.setStyleName("gerrit-ErrorDialog-Buttons");
    final Button closey = new Button();
    closey.setText(Gerrit.C.errorDialogClose());
    closey.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        hide();
      }
    });
    buttons.add(closey);

    final FlowPanel center = new FlowPanel();
    center.setStyleName("gerrit-ErrorDialog");
    center.add(body);
    center.add(buttons);
    add(center);
  }

  /** Create a dialog box to show a single message string. */
  public ErrorDialog(final String message) {
    this();
    body.add(new Label(message));
  }

  /** Create a dialog box to show a single message string. */
  public ErrorDialog(final SafeHtml message) {
    this();
    body.add(message.toBlockWidget());
  }

  /** Create a dialog box to nicely format an exception. */
  public ErrorDialog(final Throwable what) {
    this();

    String cn;
    if (what instanceof RemoteJsonException) {
      cn = RpcConstants.C.errorRemoteJsonException();

    } else if (what instanceof StatusCodeException) {
      cn = RpcConstants.C.errorServerUnavailable();

    } else {
      cn = what.getClass().getName();
      if (cn.startsWith("java.lang.")) {
        cn = cn.substring("java.lang.".length());
      } else if (cn.startsWith("com.google.gerrit.")) {
        cn = cn.substring(cn.lastIndexOf('.') + 1);
      }
      if (cn.endsWith("Exception")) {
        cn = cn.substring(0, cn.length() - "Exception".length());
      } else if (cn.endsWith("Error")) {
        cn = cn.substring(0, cn.length() - "Error".length());
      }
    }

    body.add(label(cn, "gerrit-ErrorDialog-ErrorType"));
    body.add(new Label(what.getMessage()));
  }

  private static Label label(final String what, final String style) {
    final Label r = new Label(what);
    r.setStyleName(style);
    return r;
  }
}
