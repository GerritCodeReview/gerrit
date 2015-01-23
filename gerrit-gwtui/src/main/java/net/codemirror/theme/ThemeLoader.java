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
      Themes.I.eclipse(),
      Themes.I.elegant(),
      Themes.I.midnight(),
      Themes.I.neat(),
      Themes.I.night(),
      Themes.I.twilight(),
  };

  private static final EnumSet<Theme> loaded = EnumSet.of(Theme.DEFAULT);

  public static final void loadTheme(final Theme theme,
      final AsyncCallback<Void> cb) {
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
      resource.getText(new ResourceCallback<TextResource>() {
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

  private static final ExternalTextResource findTheme(Theme theme) {
    for (ExternalTextResource r : THEMES) {
      if (theme.name().toLowerCase().equals(r.getName())) {
        return r;
      }
    }
    return null;
  }

  private ThemeLoader() {
  }
}
