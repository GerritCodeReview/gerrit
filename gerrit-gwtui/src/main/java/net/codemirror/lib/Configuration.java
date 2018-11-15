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

import com.google.gwt.core.client.JavaScriptObject;

/**
 * Simple map-like structure to pass configuration to CodeMirror.
 *
 * @see <a href="http://codemirror.net/doc/manual.html#config">CodeMirror config</a>
 * @see CodeMirror#create(com.google.gwt.dom.client.Element, Configuration)
 */
public class Configuration extends JavaScriptObject {
  public static Configuration create() {
    return createObject().cast();
  }

  public final native Configuration set(String name, String val)
      /*-{ this[name] = val; return this; }-*/ ;

  public final native Configuration set(String name, int val)
      /*-{ this[name] = val; return this; }-*/ ;

  public final native Configuration set(String name, double val)
      /*-{ this[name] = val; return this; }-*/ ;

  public final native Configuration set(String name, boolean val)
      /*-{ this[name] = val; return this; }-*/ ;

  public final native Configuration set(String name, JavaScriptObject val)
      /*-{ this[name] = val; return this; }-*/ ;

  protected Configuration() {}
}
