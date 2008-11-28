// Copyright 2008 Google Inc.
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

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.Frame;

/**
 * Prompts the user to sign in to their account.
 * <p>
 * This dialog performs the login within an iframe, allowing normal HTML based
 * login pages to be used, including those which aren't served from the same
 * server as Gerrit. This is important to permit an OpenID provider or some
 * other web based single-sign-on system to be used for authentication.
 * <p>
 * Post login the iframe content is expected to execute the JavaScript snippet:
 * 
 * <pre>
 * parent.gerritPostSignIn(success);
 * </pre>
 * 
 * where success is either <code>true</code> (the user is now signed in) or
 * <code>false</code> (the sign in was aborted/canceled before it completed).
 */
public class SignInDialog extends DialogBox {
  private static SignInDialog current;
  private final AsyncCallback<?> callback;

  /**
   * Create a new dialog to handle user sign in.
   * 
   * @param callback optional; onSuccess will be called if sign is completed.
   *        This can be used to trigger sending an RPC or some other action.
   */
  public SignInDialog(final AsyncCallback<?> callback) {
    super(/* auto hide */true, /* modal */true);

    this.callback = callback;

    final Frame f = new Frame(GWT.getModuleBaseURL() + "login");
    f.setWidth("630px");
    f.setHeight("420px");
    add(f);
    setText(Gerrit.C.signInDialogTitle());
  }

  @Override
  public void show() {
    if (current != null) {
      current.hide();
    }

    super.show();

    current = this;
    exportPostSignIn();
  }

  @Override
  protected void onUnload() {
    if (current == this) {
      unexportPostSignIn();
      current = null;
    }
    super.onUnload();
  }

  static void postSignIn(final boolean success) {
    final SignInDialog d = current;
    assert d != null;
    if (success) {
      Gerrit.postSignIn();
      d.hide();
      final AsyncCallback<?> ac = d.callback;
      if (ac != null) {
        DeferredCommand.addCommand(new Command() {
          public void execute() {
            ac.onSuccess(null);
          }
        });
      }
    } else {
      d.hide();
    }
  }

  private static final native void unexportPostSignIn()/*-{ delete $wnd.gerritPostSignIn; }-*/;

  private static final native void exportPostSignIn()/*-{ $wnd.gerritPostSignIn = @com.google.gerrit.client.SignInDialog::postSignIn(Z); }-*/;
}
