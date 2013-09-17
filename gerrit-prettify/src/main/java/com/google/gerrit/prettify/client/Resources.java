// Copyright (C) 2009 The Android Open Source Project
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
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.TextResource;

/** Loads the minimized form of prettify into the client. */
interface Resources extends ClientBundle {
  static final Resources I = GWT.create(Resources.class);

  @Source("prettify.css")
  CssResource prettify_css();

  @Source("gerrit.css")
  CssResource gerrit_css();

  @Source("prettify.js")
  TextResource core();

  @Source("lang-apollo.js") TextResource lang_apollo();
  @Source("lang-basic.js") TextResource lang_basic();
  @Source("lang-clj.js") TextResource lang_clj();
  @Source("lang-css.js") TextResource lang_css();
  @Source("lang-dart.js") TextResource lang_dart();
  @Source("lang-erlang.js") TextResource lang_erlang();
  @Source("lang-go.js") TextResource lang_go();
  @Source("lang-hs.js") TextResource lang_hs();
  @Source("lang-lisp.js") TextResource lang_lisp();
  @Source("lang-llvm.js") TextResource lang_llvm();
  @Source("lang-lua.js") TextResource lang_lua();
  @Source("lang-matlab.js") TextResource lang_matlab();
  @Source("lang-ml.js") TextResource lang_ml();
  @Source("lang-mumps.js") TextResource lang_mumps();
  @Source("lang-n.js") TextResource lang_n();
  @Source("lang-pascal.js") TextResource lang_pascal();
  @Source("lang-proto.js") TextResource lang_proto();
  @Source("lang-r.js") TextResource lang_r();
  @Source("lang-rd.js") TextResource lang_rd();
  @Source("lang-scala.js") TextResource lang_scala();
  @Source("lang-sql.js") TextResource lang_sql();
  @Source("lang-tcl.js") TextResource lang_tcl();
  @Source("lang-tex.js") TextResource lang_tex();
  @Source("lang-vb.js") TextResource lang_vb();
  @Source("lang-vhdl.js") TextResource lang_vhdl();
  @Source("lang-wiki.js") TextResource lang_wiki();
  @Source("lang-xq.js") TextResource lang_xq();
  @Source("lang-yaml.js") TextResource lang_yaml();
}
