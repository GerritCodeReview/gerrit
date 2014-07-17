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
import com.google.gwt.resources.client.TextResource;

public interface Modes extends ClientBundle {
  public static final Modes I = GWT.create(Modes.class);

  @Source("mode_map") TextResource mode_map();
  @Source("clike/clike.js") @DoNotEmbed DataResource clike();
  @Source("clojure/clojure.js") @DoNotEmbed DataResource clojure();
  @Source("commonlisp/commonlisp.js") @DoNotEmbed DataResource commonlisp();
  @Source("coffeescript/coffeescript.js") @DoNotEmbed DataResource coffeescript();
  @Source("css/css.js") @DoNotEmbed DataResource css();
  @Source("d/d.js") @DoNotEmbed DataResource d();
  @Source("diff/diff.js") @DoNotEmbed DataResource diff();
  @Source("dtd/dtd.js") @DoNotEmbed DataResource dtd();
  @Source("erlang/erlang.js") @DoNotEmbed DataResource erlang();
  @Source("gas/gas.js") @DoNotEmbed DataResource gas();
  @Source("gerrit/commit.js") @DoNotEmbed DataResource gerrit_commit();
  @Source("gfm/gfm.js") @DoNotEmbed DataResource gfm();
  @Source("go/go.js") @DoNotEmbed DataResource go();
  @Source("groovy/groovy.js") @DoNotEmbed DataResource groovy();
  @Source("haskell/haskell.js") @DoNotEmbed DataResource haskell();
  @Source("htmlmixed/htmlmixed.js") @DoNotEmbed DataResource htmlmixed();
  @Source("javascript/javascript.js") @DoNotEmbed DataResource javascript();
  @Source("lua/lua.js") @DoNotEmbed DataResource lua();
  @Source("markdown/markdown.js") @DoNotEmbed DataResource markdown();
  @Source("perl/perl.js") @DoNotEmbed DataResource perl();
  @Source("php/php.js") @DoNotEmbed DataResource php();
  @Source("pig/pig.js") @DoNotEmbed DataResource pig();
  @Source("properties/properties.js") @DoNotEmbed DataResource properties();
  @Source("python/python.js") @DoNotEmbed DataResource python();
  @Source("r/r.js") @DoNotEmbed DataResource r();
  @Source("rst/rst.js") @DoNotEmbed DataResource rst();
  @Source("ruby/ruby.js") @DoNotEmbed DataResource ruby();
  @Source("scheme/scheme.js") @DoNotEmbed DataResource scheme();
  @Source("shell/shell.js") @DoNotEmbed DataResource shell();
  @Source("smalltalk/smalltalk.js") @DoNotEmbed DataResource smalltalk();
  @Source("sql/sql.js") @DoNotEmbed DataResource sql();
  @Source("stex/stex.js") @DoNotEmbed DataResource stex();
  @Source("tcl/tcl.js") @DoNotEmbed DataResource tcl();
  @Source("velocity/velocity.js") @DoNotEmbed DataResource velocity();
  @Source("verilog/verilog.js") @DoNotEmbed DataResource verilog();
  @Source("xml/xml.js") @DoNotEmbed DataResource xml();
  @Source("yaml/yaml.js") @DoNotEmbed DataResource yaml();

  // When adding a resource, update static initializer in ModeInjector.
}
