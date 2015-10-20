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

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.DataResource;
import com.google.gwt.resources.client.DataResource.DoNotEmbed;
import com.google.gwt.resources.client.ExternalTextResource;

interface Lib extends ClientBundle {
  static final Lib I = GWT.create(Lib.class);

  @Source("cm.css")
  ExternalTextResource css();

  @Source("cm.js")
  @DoNotEmbed
  DataResource js();

  @Source("annotation.css")
  ExternalTextResource annotationCss();

  @Source("lint.js")
  @DoNotEmbed
  DataResource lint();

  @Source("style.css")
  CodeMirror.Style style();
}
