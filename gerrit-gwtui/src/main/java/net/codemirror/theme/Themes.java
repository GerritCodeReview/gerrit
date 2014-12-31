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
  public static final Themes I = GWT.create(Themes.class);

  @Source("eclipse.css") ExternalTextResource eclipse();
  @Source("elegant.css") ExternalTextResource elegant();
  @Source("midnight.css") ExternalTextResource midnight();
  @Source("neat.css") ExternalTextResource neat();
  @Source("night.css") ExternalTextResource night();
  @Source("twilight.css") ExternalTextResource twilight();

  // When adding a resource, update:
  // - static initializer in ThemeLoader
  // - enum value in com.google.gerrit.extensions.common.Theme
}
