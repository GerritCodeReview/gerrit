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

import com.google.gerrit.extensions.client.Theme;
import com.google.gwt.dom.client.StyleInjector;
import com.google.gwt.resources.client.ExternalTextResource;
import com.google.gwt.resources.client.ResourceCallback;
import com.google.gwt.resources.client.ResourceException;
import com.google.gwt.resources.client.TextResource;
import com.google.gwt.user.client.rpc.AsyncCallback;
import java.util.EnumSet;

/** Dynamically loads a known CodeMirror theme's CSS */
public class ThemeLoader {
  private static final ExternalTextResource[] THEMES = {
    Themes.I.day_3024(),
    Themes.I.night_3024(),
    Themes.I.abcdef(),
    Themes.I.ambiance(),
    Themes.I.base16_dark(),
    Themes.I.base16_light(),
    Themes.I.bespin(),
    Themes.I.blackboard(),
    Themes.I.cobalt(),
    Themes.I.colorforth(),
    Themes.I.dracula(),
    Themes.I.eclipse(),
    Themes.I.elegant(),
    Themes.I.erlang_dark(),
    Themes.I.hopscotch(),
    Themes.I.icecoder(),
    Themes.I.isotope(),
    Themes.I.lesser_dark(),
    Themes.I.liquibyte(),
    Themes.I.material(),
    Themes.I.mbo(),
    Themes.I.mdn_like(),
    Themes.I.midnight(),
    Themes.I.monokai(),
    Themes.I.neat(),
    Themes.I.neo(),
    Themes.I.night(),
    Themes.I.paraiso_dark(),
    Themes.I.paraiso_light(),
    Themes.I.pastel_on_dark(),
    Themes.I.railscasts(),
    Themes.I.rubyblue(),
    Themes.I.seti(),
    Themes.I.solarized(),
    Themes.I.the_matrix(),
    Themes.I.tomorrow_night_bright(),
    Themes.I.tomorrow_night_eighties(),
    Themes.I.ttcn(),
    Themes.I.twilight(),
    Themes.I.vibrant_ink(),
    Themes.I.xq_dark(),
    Themes.I.xq_light(),
    Themes.I.yeti(),
    Themes.I.zenburn(),
  };

  private static final EnumSet<Theme> loaded = EnumSet.of(Theme.DEFAULT);

  public static final void loadTheme(Theme theme, AsyncCallback<Void> cb) {
    if (loaded.contains(theme)) {
      cb.onSuccess(null);
      return;
    }

    ExternalTextResource resource = findTheme(theme);
    if (resource == null) {
      cb.onFailure(new Exception("unknown theme " + theme));
      return;
    }

    try {
      resource.getText(
          new ResourceCallback<TextResource>() {
            @Override
            public void onSuccess(TextResource resource) {
              StyleInjector.inject(resource.getText());
              loaded.add(theme);
              cb.onSuccess(null);
            }

            @Override
            public void onError(ResourceException e) {
              cb.onFailure(e);
            }
          });
    } catch (ResourceException e) {
      cb.onFailure(e);
    }
  }

  private static ExternalTextResource findTheme(Theme theme) {
    for (ExternalTextResource r : THEMES) {
      if (theme.name().toLowerCase().equals(r.getName())) {
        return r;
      }
    }
    return null;
  }

  private ThemeLoader() {}
}
