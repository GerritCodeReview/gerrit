// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.prettify.client;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.resources.client.TextResource;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.NamedFrame;

/**
 * Creates a private JavaScript environment, typically inside an IFrame.
 * <p>
 * Instances must be created through {@code GWT.create(PrivateScopeImpl.class)}.
 * A scope must remain attached to the primary document for its entire life.
 * Behavior is undefined if a scope is detached and attached again later. It is
 * best to attach the scope with {@code RootPanel.get().add(scope)} as soon as
 * it has been created.
 */
public class PrivateScopeImpl extends Composite {
  private static int scopeId;

  protected final String scopeName;

  public PrivateScopeImpl() {
    scopeName = nextScopeName();

    NamedFrame frame = new NamedFrame(scopeName);
    frame.setUrl("javascript:''");
    initWidget(frame);

    setVisible(false);
  }

  public void compile(TextResource js) {
    eval(js.getText());
  }

  public void eval(String js) {
    nativeEval(getContext(), js);
  }

  public JavaScriptObject getContext() {
    return nativeGetContext(scopeName);
  }

  private static String nextScopeName() {
    return "_PrivateScope" + (++scopeId);
  }

  private static native void nativeEval(JavaScriptObject ctx, String js)
  /*-{ ctx.eval(js); }-*/;

  private static native JavaScriptObject nativeGetContext(String scopeName)
  /*-{ return $wnd[scopeName]; }-*/;
}
