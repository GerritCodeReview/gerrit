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

  @Source("lang-apollo.js")
  TextResource lang_apollo();

  @Source("lang-css.js")
  TextResource lang_css();

  @Source("lang-hs.js")
  TextResource lang_hs();

  @Source("lang-lisp.js")
  TextResource lang_lisp();

  @Source("lang-lua.js")
  TextResource lang_lua();

  @Source("lang-ml.js")
  TextResource lang_ml();

  @Source("lang-proto.js")
  TextResource lang_proto();

  @Source("lang-sql.js")
  TextResource lang_sql();

  @Source("lang-vb.js")
  TextResource lang_vb();

  @Source("lang-wiki.js")
  TextResource lang_wiki();
}
