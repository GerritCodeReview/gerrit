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

import com.google.gerrit.prettify.common.PrettyFactory;
import com.google.gerrit.prettify.common.PrettyFormatter;
import com.google.gwt.resources.client.TextResource;
import com.google.gwt.user.client.ui.NamedFrame;
import com.google.gwt.user.client.ui.RootPanel;

/** Evaluates prettify using the host browser's JavaScript engine. */
public class ClientSideFormatter extends PrettyFormatter {
  public static final PrettyFactory FACTORY = new PrettyFactory() {
    @Override
    public PrettyFormatter get() {
      return new ClientSideFormatter();
    }
  };

  static {
    Resources.I.prettify_css().ensureInjected();
    Resources.I.gerrit_css().ensureInjected();

    createFrame();
    compile(Resources.I.core());
    compile(Resources.I.lang_css());
    compile(Resources.I.lang_hs());
    compile(Resources.I.lang_lisp());
    compile(Resources.I.lang_lua());
    compile(Resources.I.lang_ml());
    compile(Resources.I.lang_proto());
    compile(Resources.I.lang_sql());
    compile(Resources.I.lang_vb());
    compile(Resources.I.lang_wiki());
  }

  private static void createFrame() {
    NamedFrame frame = new NamedFrame("_prettify");
    frame.setUrl("javascript:");
    frame.setVisible(false);
    RootPanel.get().add(frame);
  }

  private static void compile(TextResource core) {
    eval(core.getText());
  }

  private static native void eval(String js)
  /*-{ $wnd._prettify.eval(js); }-*/;

  @Override
  protected String prettify(String html, String type) {
    return go(html, type, settings.getTabSize());
  }

  private static native String go(String srcText, String srcType, int tabSize)
  /*-{
     $wnd._prettify.PR_TAB_WIDTH = tabSize;
     return $wnd._prettify.prettyPrintOne(srcText, srcType);
  }-*/;
}
