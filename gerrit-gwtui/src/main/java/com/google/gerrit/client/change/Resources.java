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

package com.google.gerrit.client.change;

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.ImageResource;

public interface Resources extends ClientBundle {
  public static final Resources I = GWT.create(Resources.class);
  static final Constants C = GWT.create(Constants.class);
  static final Messages M = GWT.create(Messages.class);

  @Source("star_open.png") ImageResource star_open();
  @Source("star_filled.png") ImageResource star_filled();
  @Source("reload_black.png") ImageResource reload_black();
  @Source("reload_white.png") ImageResource reload_white();
  @Source("remove_reviewer.png") ImageResource remove_reviewer();
  @Source("common.css") Style style();

  public interface Style extends CssResource {
    String button();
    String popup();
    String popupContent();
    String section();
  }
}
