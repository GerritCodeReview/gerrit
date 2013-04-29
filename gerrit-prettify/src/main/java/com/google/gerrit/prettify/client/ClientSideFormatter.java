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

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.ui.RootPanel;

/** Evaluates prettify using the host browser's JavaScript engine. */
public class ClientSideFormatter extends PrettyFormatter {
  public static final PrettyFactory FACTORY = new PrettyFactory() {
    @Override
    public PrettyFormatter get() {
      return new ClientSideFormatter();
    }
  };

  private static final PrivateScopeImpl prettify;

  static {
    Resources.I.prettify_css().ensureInjected();
    Resources.I.gerrit_css().ensureInjected();

    prettify = GWT.create(PrivateScopeImpl.class);
    RootPanel.get().add(prettify);

    prettify.compile(Resources.I.core());
    prettify.compile(Resources.I.lang_apollo());
    prettify.compile(Resources.I.lang_clj());
    prettify.compile(Resources.I.lang_css());
    prettify.compile(Resources.I.lang_dart());
    prettify.compile(Resources.I.lang_go());
    prettify.compile(Resources.I.lang_hs());
    prettify.compile(Resources.I.lang_lisp());
    prettify.compile(Resources.I.lang_lua());
    prettify.compile(Resources.I.lang_ml());
    prettify.compile(Resources.I.lang_n());
    prettify.compile(Resources.I.lang_proto());
    prettify.compile(Resources.I.lang_scala());
    prettify.compile(Resources.I.lang_sql());
    prettify.compile(Resources.I.lang_tex());
    prettify.compile(Resources.I.lang_vb());
    prettify.compile(Resources.I.lang_vhdl());
    prettify.compile(Resources.I.lang_wiki());
    prettify.compile(Resources.I.lang_xq());
    prettify.compile(Resources.I.lang_yaml());
  }

  @Override
  protected String prettify(String html, String type) {
    return go(prettify.getContext(), html, type, diffPrefs.getTabSize());
  }

  private static native String go(JavaScriptObject ctx, String srcText,
      String srcType, int tabSize)
  /*-{
     ctx.PR_TAB_WIDTH = tabSize;
     return ctx.prettyPrintOne(srcText, srcType);
  }-*/;
}
