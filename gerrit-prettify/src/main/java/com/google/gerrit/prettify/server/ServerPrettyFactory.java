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

package com.google.gerrit.prettify.server;

import com.google.gerrit.prettify.common.PrettyFactory;
import com.google.gerrit.prettify.common.PrettyFormatter;
import com.google.gerrit.prettify.common.PrettySettings;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

/** Runs prettify via Mozilla Rhino JavaScript engine. */
@Singleton
class ServerPrettyFactory implements PrettyFactory, Provider<PrettyFormatter> {
  private final ContextFactory contextFactory;
  private final ScriptableObject sharedScope;
  private final Scriptable sharedWindow;

  @Inject
  ServerPrettyFactory() {
    contextFactory = new ContextFactory() {
      @Override
      protected boolean hasFeature(Context cx, int featureIndex) {
        if (featureIndex == Context.FEATURE_DYNAMIC_SCOPE) {
          return true;
        }
        return super.hasFeature(cx, featureIndex);
      }
    };

    Context cx = contextFactory.enterContext();
    try {
      cx.setOptimizationLevel(9);

      sharedScope = cx.initStandardObjects(null, true);
      sharedWindow = cx.newObject(sharedScope);
      sharedScope.put("window", sharedScope, sharedWindow);

      compile(cx, "prettify.js");
      compile(cx, "server-env.js");

      compile(cx, "lang-apollo.js");
      compile(cx, "lang-css.js");
      compile(cx, "lang-hs.js");
      compile(cx, "lang-lisp.js");
      compile(cx, "lang-lua.js");
      compile(cx, "lang-ml.js");
      compile(cx, "lang-proto.js");
      compile(cx, "lang-sql.js");
      compile(cx, "lang-vb.js");
      compile(cx, "lang-wiki.js");
    } finally {
      Context.exit();
    }
  }

  @Override
  public PrettyFormatter get() {
    return new PrettyFormatter() {
      @Override
      protected String prettify(String html) {
        return prettyPrintOne(html, settings);
      }
    };
  }

  private String prettyPrintOne(String srcText, PrettySettings how) {
    String srcType = how.getFilename();
    int dot = srcType.lastIndexOf('.');
    if (0 < dot) {
      srcType = srcType.substring(dot + 1);
    }

    Context cx = contextFactory.enterContext();
    try {
      Scriptable callScope = cx.newObject(sharedScope);
      callScope.setPrototype(sharedScope);
      callScope.setParentScope(null);

      // We have to clone and shadow the window object, so we can
      // set a per-call window.PR_TAB_WIDTH value. Above we ensured
      // we compiled our code in a dynamic scope so the window object
      // resolution will happen to our shadowed value.
      //
      Scriptable callWindow = cx.newObject(callScope);
      callWindow.setPrototype(sharedWindow);
      callWindow.put("PR_TAB_WIDTH", callWindow, how.getTabSize());

      callScope.put("window", callScope, callWindow);
      callScope.put("srcText", callScope, srcText);
      callScope.put("srcType", callScope, srcType);
      String call = "prettyPrintOne(srcText, srcType)";

      return cx.evaluateString(callScope, call, "<call>", 1, null).toString();
    } finally {
      Context.exit();
    }
  }

  private void compile(Context cx, String name) {
    name = "com/google/gerrit/prettify/client/" + name;

    InputStream in = getClass().getClassLoader().getResourceAsStream(name);
    if (in == null) {
      throw new RuntimeException("Cannot find " + name);
    }
    try {
      final InputStreamReader r = new InputStreamReader(in, "UTF-8");
      try {
        cx.compileReader(r, name, 1, null).exec(cx, sharedScope);
      } finally {
        r.close();
      }
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("Cannot compile " + name, e);
    } catch (IOException e) {
      throw new RuntimeException("Cannot compile " + name, e);
    }
  }
}
