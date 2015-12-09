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
import com.google.gerrit.server.account.GetPreferences.PreferenceInfo;
import com.google.gerrit.server.account.SetPreferences.Input;
import com.google.gerrit.server.account.VersionedAccountPreferences;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;

import java.io.IOException;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@Singleton
public class SetPreferences implements RestModifyView<ConfigResource, Input> {
  private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
  private final AllUsersName allUsersName;

  @Inject
  SetPreferences(Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      AllUsersName allUsersName) {
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.allUsersName = allUsersName;
  }

  @Override
  public Object apply(ConfigResource rsrc, Input i) throws BadRequestException,
      IOException, ConfigInvalidException {
    if (i.changesPerPage != null || i.showSiteHeader != null
        || i.useFlashClipboard != null || i.downloadScheme != null
        || i.downloadCommand != null
        || i.dateFormat != null || i.timeFormat != null
        || i.relativeDateInChangeTable != null
        || i.sizeBarInChangeTable != null
        || i.legacycidInChangeTable != null
        || i.muteCommonPathPrefixes != null
        || i.reviewCategoryStrategy != null
        || i.emailStrategy != null) {
      throw new BadRequestException("unsupported option");
    }

    VersionedAccountPreferences p;
    MetaDataUpdate md = metaDataUpdateFactory.get().create(allUsersName);
    try {
      p = VersionedAccountPreferences.forDefault();
      p.load(md);
      com.google.gerrit.server.account.SetPreferences.storeMyMenus(p, i.my);
      p.commit(md);
      return new PreferenceInfo(null, p, md.getRepository());
    } finally {
      md.close();
    }
  }
}
