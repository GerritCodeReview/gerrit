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

package com.google.gerrit.server.config;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.account.SetPreferences.Input;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.io.IOException;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
public class SetPreferences implements RestModifyView<ConfigResource, Input> {
  private final Provider<com.google.gerrit.server.account.SetPreferences> setPreferences;
  private final Provider<GetPreferences> getDefaultPreferences;

  @Inject
  SetPreferences(
      Provider<com.google.gerrit.server.account.SetPreferences> setPreferences,
      Provider<GetPreferences> getDefaultPreferences) {
    this.setPreferences = setPreferences;
    this.getDefaultPreferences = getDefaultPreferences;
  }

  @Override
  public Object apply(ConfigResource rsrc, Input i) throws BadRequestException,
      IOException {
    if (i.changesPerPage != null || i.showSiteHeader != null
        || i.useFlashClipboard != null || i.downloadScheme != null
        || i.downloadCommand != null || i.copySelfOnEmail != null
        || i.dateFormat != null || i.timeFormat != null
        || i.reversePatchSetOrder != null
        || i.showUsernameInReviewCategory != null
        || i.relativeDateInChangeTable != null
        || i.sizeBarInChangeTable != null
        || i.commentVisibilityStrategy != null || i.diffView != null
        || i.changeScreen != null) {
      throw new BadRequestException("unsupported option");
    }
    if (i.my != null) {
      setPreferences.get().storeMyMenus(RefNames.REFS_USER + "default", i.my);
    }
    return getDefaultPreferences.get().apply(rsrc);
  }
}
