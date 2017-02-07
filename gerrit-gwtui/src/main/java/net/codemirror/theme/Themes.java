// Copyright (C) 2014 The Android Open Source Project
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

package net.codemirror.theme;

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.ExternalTextResource;

public interface Themes extends ClientBundle {
  Themes I = GWT.create(Themes.class);

  @Source("3024-day.css")
  ExternalTextResource day_3024();

  @Source("3024-night.css")
  ExternalTextResource night_3024();

  @Source("abcdef.css")
  ExternalTextResource abcdef();

  @Source("ambiance.css")
  ExternalTextResource ambiance();

  @Source("base16-dark.css")
  ExternalTextResource base16_dark();

  @Source("base16-light.css")
  ExternalTextResource base16_light();

  @Source("bespin.css")
  ExternalTextResource bespin();

  @Source("blackboard.css")
  ExternalTextResource blackboard();

  @Source("cobalt.css")
  ExternalTextResource cobalt();

  @Source("colorforth.css")
  ExternalTextResource colorforth();

  @Source("dracula.css")
  ExternalTextResource dracula();

  @Source("eclipse.css")
  ExternalTextResource eclipse();

  @Source("elegant.css")
  ExternalTextResource elegant();

  @Source("erlang-dark.css")
  ExternalTextResource erlang_dark();

  @Source("hopscotch.css")
  ExternalTextResource hopscotch();

  @Source("icecoder.css")
  ExternalTextResource icecoder();

  @Source("isotope.css")
  ExternalTextResource isotope();

  @Source("lesser-dark.css")
  ExternalTextResource lesser_dark();

  @Source("liquibyte.css")
  ExternalTextResource liquibyte();

  @Source("material.css")
  ExternalTextResource material();

  @Source("mbo.css")
  ExternalTextResource mbo();

  @Source("mdn-like.css")
  ExternalTextResource mdn_like();

  @Source("midnight.css")
  ExternalTextResource midnight();

  @Source("monokai.css")
  ExternalTextResource monokai();

  @Source("neat.css")
  ExternalTextResource neat();

  @Source("neo.css")
  ExternalTextResource neo();

  @Source("night.css")
  ExternalTextResource night();

  @Source("paraiso-dark.css")
  ExternalTextResource paraiso_dark();

  @Source("paraiso-light.css")
  ExternalTextResource paraiso_light();

  @Source("pastel-on-dark.css")
  ExternalTextResource pastel_on_dark();

  @Source("railscasts.css")
  ExternalTextResource railscasts();

  @Source("rubyblue.css")
  ExternalTextResource rubyblue();

  @Source("seti.css")
  ExternalTextResource seti();

  @Source("solarized.css")
  ExternalTextResource solarized();

  @Source("the-matrix.css")
  ExternalTextResource the_matrix();

  @Source("tomorrow-night-bright.css")
  ExternalTextResource tomorrow_night_bright();

  @Source("tomorrow-night-eighties.css")
  ExternalTextResource tomorrow_night_eighties();

  @Source("ttcn.css")
  ExternalTextResource ttcn();

  @Source("twilight.css")
  ExternalTextResource twilight();

  @Source("vibrant-ink.css")
  ExternalTextResource vibrant_ink();

  @Source("xq-dark.css")
  ExternalTextResource xq_dark();

  @Source("xq-light.css")
  ExternalTextResource xq_light();

  @Source("yeti.css")
  ExternalTextResource yeti();

  @Source("zenburn.css")
  ExternalTextResource zenburn();

  // When adding a resource, update:
  // - static initializer in ThemeLoader
  // - enum value in com.google.gerrit.extensions.common.Theme
}
