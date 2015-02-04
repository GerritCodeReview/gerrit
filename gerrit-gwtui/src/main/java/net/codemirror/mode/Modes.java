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

package net.codemirror.mode;

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.DataResource;
import com.google.gwt.resources.client.DataResource.DoNotEmbed;

public interface Modes extends ClientBundle {
  public static final Modes I = GWT.create(Modes.class);

  @Source("clike.js") @DoNotEmbed DataResource clike();
  @Source("clojure.js") @DoNotEmbed DataResource clojure();
  @Source("coffeescript.js") @DoNotEmbed DataResource coffeescript();
  @Source("commonlisp.js") @DoNotEmbed DataResource commonlisp();
  @Source("css.js") @DoNotEmbed DataResource css();
  @Source("d.js") @DoNotEmbed DataResource d();
  @Source("dart.js") @DoNotEmbed DataResource dart();
  @Source("diff.js") @DoNotEmbed DataResource diff();
  @Source("dockerfile.js") @DoNotEmbed DataResource dockerfile();
  @Source("dtd.js") @DoNotEmbed DataResource dtd();
  @Source("erlang.js") @DoNotEmbed DataResource erlang();
  @Source("gas.js") @DoNotEmbed DataResource gas();
  @Source("gerrit/commit.js") @DoNotEmbed DataResource gerrit_commit();
  @Source("gfm.js") @DoNotEmbed DataResource gfm();
  @Source("go.js") @DoNotEmbed DataResource go();
  @Source("groovy.js") @DoNotEmbed DataResource groovy();
  @Source("haskell.js") @DoNotEmbed DataResource haskell();
  @Source("htmlmixed.js") @DoNotEmbed DataResource htmlmixed();
  @Source("javascript.js") @DoNotEmbed DataResource javascript();
  @Source("lua.js") @DoNotEmbed DataResource lua();
  @Source("markdown.js") @DoNotEmbed DataResource markdown();
  @Source("perl.js") @DoNotEmbed DataResource perl();
  @Source("php.js") @DoNotEmbed DataResource php();
  @Source("pig.js") @DoNotEmbed DataResource pig();
  @Source("properties.js") @DoNotEmbed DataResource properties();
  @Source("python.js") @DoNotEmbed DataResource python();
  @Source("r.js") @DoNotEmbed DataResource r();
  @Source("rst.js") @DoNotEmbed DataResource rst();
  @Source("ruby.js") @DoNotEmbed DataResource ruby();
  @Source("scheme.js") @DoNotEmbed DataResource scheme();
  @Source("shell.js") @DoNotEmbed DataResource shell();
  @Source("smalltalk.js") @DoNotEmbed DataResource smalltalk();
  @Source("soy.js") @DoNotEmbed DataResource soy();
  @Source("sql.js") @DoNotEmbed DataResource sql();
  @Source("stex.js") @DoNotEmbed DataResource stex();
  @Source("tcl.js") @DoNotEmbed DataResource tcl();
  @Source("velocity.js") @DoNotEmbed DataResource velocity();
  @Source("verilog.js") @DoNotEmbed DataResource verilog();
  @Source("xml.js") @DoNotEmbed DataResource xml();
  @Source("yaml.js") @DoNotEmbed DataResource yaml();

  // When adding a resource, update static initializer in ModeInfo.
}
