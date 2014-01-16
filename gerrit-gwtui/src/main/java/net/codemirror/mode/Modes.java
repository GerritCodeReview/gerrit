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
  @Source("css/css.js") @DoNotEmbed DataResource css();
  @Source("go/go.js") @DoNotEmbed DataResource go();
  @Source("groovy/groovy.js") @DoNotEmbed DataResource groovy();
  @Source("htmlmixed/htmlmixed.js") @DoNotEmbed DataResource htmlmixed();
  @Source("javascript/javascript.js") @DoNotEmbed DataResource javascript();
  @Source("perl/perl.js") @DoNotEmbed DataResource perl();
  @Source("properties/properties.js") @DoNotEmbed DataResource properties();
  @Source("python/python.js") @DoNotEmbed DataResource python();
  @Source("ruby/ruby.js") @DoNotEmbed DataResource ruby();
  @Source("shell/shell.js") @DoNotEmbed DataResource shell();
  @Source("sql/sql.js") @DoNotEmbed DataResource sql();
  @Source("velocity/velocity.js") @DoNotEmbed DataResource velocity();
  @Source("xml/xml.js") @DoNotEmbed DataResource xml();

  // When adding a resource, update static initializer in ModeInjector.
}
