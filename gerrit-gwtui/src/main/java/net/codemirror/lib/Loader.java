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
// limitations under the License.

package net.codemirror.lib;

import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.ScriptInjector;
import com.google.gwt.dom.client.ScriptElement;
import com.google.gwt.dom.client.StyleInjector;
import com.google.gwt.resources.client.ExternalTextResource;
import com.google.gwt.resources.client.ResourceCallback;
import com.google.gwt.resources.client.ResourceException;
import com.google.gwt.resources.client.TextResource;
import com.google.gwt.safehtml.shared.SafeUri;
import com.google.gwt.user.client.rpc.AsyncCallback;

import net.codemirror.addon.Addons;
import net.codemirror.keymap.Keymap;

import java.util.logging.Level;
import java.util.logging.Logger;

class Loader {
  private static native boolean isLibLoaded()
  /*-{ return $wnd.hasOwnProperty('CodeMirror'); }-*/;

  static void initLibrary(AsyncCallback<Void> cb) {
    if (isLibLoaded()) {
      cb.onSuccess(null);
    } else {
      CallbackGroup group = new CallbackGroup();
      injectCss(Lib.I.css());
      injectCss(Addons.I.dialogCss());
      injectScript(Lib.I.js().getSafeUri(),
          group.add(CallbackGroup.<Void>emptyCallback()));
      injectScript(Keymap.I.vim().getSafeUri(),
          group.add(CallbackGroup.<Void>emptyCallback()));
      injectScript(Addons.I.dialog().getSafeUri(),
          group.add(CallbackGroup.<Void>emptyCallback()));
      injectScript(Addons.I.searchcursor().getSafeUri(),
          group.add(CallbackGroup.<Void>emptyCallback()));
      injectScript(Addons.I.search().getSafeUri(),
          group.add(CallbackGroup.<Void>emptyCallback()));
      injectScript(Addons.I.mark_selection().getSafeUri(),
          group.add(CallbackGroup.<Void>emptyCallback()));
      injectScript(Addons.I.trailingspace().getSafeUri(),
          group.addFinal(cb));
    }
  }

  private static void injectCss(ExternalTextResource css) {
    try {
      css.getText(new ResourceCallback<TextResource>() {
        @Override
        public void onSuccess(TextResource resource) {
          StyleInjector.inject(resource.getText());
        }

        @Override
        public void onError(ResourceException e) {
          error(e);
        }
      });
    } catch (ResourceException e) {
      error(e);
    }
  }

  static void injectScript(SafeUri js, final AsyncCallback<Void> callback) {
    final ScriptElement[] script = new ScriptElement[1];
    script[0] = ScriptInjector.fromUrl(js.asString())
      .setWindow(ScriptInjector.TOP_WINDOW)
      .setCallback(new Callback<Void, Exception>() {
        @Override
        public void onSuccess(Void result) {
          script[0].removeFromParent();
          callback.onSuccess(result);
        }

        @Override
        public void onFailure(Exception reason) {
          error(reason);
          callback.onFailure(reason);
        }
       })
      .inject()
      .cast();
  }

  private static void error(Exception e) {
    Logger log = Logger.getLogger("net.codemirror");
    log.log(Level.SEVERE, "Cannot load portions of CodeMirror", e);
  }

  private Loader() {
  }
}
