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

import com.google.gerrit.client.rpc.GerritCallback;
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

import java.util.logging.Level;
import java.util.logging.Logger;

class Loader {
  private static native boolean isLibLoaded()
  /*-{ return $wnd.hasOwnProperty('CodeMirror'); }-*/;

  static void initLibrary(final AsyncCallback<Void> cb) {
    if (isLibLoaded()) {
      cb.onSuccess(null);
    } else {
      injectCss(Lib.I.css());
      injectScript(Lib.I.js().getSafeUri(), new GerritCallback<Void>(){
        @Override
        public void onSuccess(Void result) {
          initVimKeys();
          cb.onSuccess(null);
        }
      });
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

  private static void initVimKeys() {
    // TODO: Better custom keybindings, remove temporary navigation hacks.
    KeyMap km = CodeMirror.cloneKeyMap("vim");
    for (String s : new String[] {
        "A", "C", "O", "R", "U", "Ctrl-C", "Ctrl-O"}) {
      km.remove(s);
    }
    CodeMirror.addKeyMap("vim_ro", km);
  }

  private static void error(Exception e) {
    Logger log = Logger.getLogger("net.codemirror");
    log.log(Level.SEVERE, "Cannot load portions of CodeMirror", e);
  }

  private Loader() {
  }
}
