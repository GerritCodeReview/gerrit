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

package com.google.gerrit.server.account;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.restapi.CacheControl;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.avatar.AvatarProvider;
import com.google.inject.Inject;
import java.util.concurrent.TimeUnit;
import org.kohsuke.args4j.Option;

public class GetAvatar implements RestReadView<AccountResource> {
  private final DynamicItem<AvatarProvider> avatarProvider;

  private int size;

  @Option(
    name = "--size",
    aliases = {"-s"},
    usage = "recommended size in pixels, height and width"
  )
  public void setSize(int s) {
    size = s;
  }

  @Inject
  GetAvatar(DynamicItem<AvatarProvider> avatarProvider) {
    this.avatarProvider = avatarProvider;
  }

  @Override
  public Response.Redirect apply(AccountResource rsrc) throws ResourceNotFoundException {
    AvatarProvider impl = avatarProvider.get();
    if (impl == null) {
      throw (new ResourceNotFoundException()).caching(CacheControl.PUBLIC(1, TimeUnit.DAYS));
    }

    String url = impl.getUrl(rsrc.getUser(), size);
    if (Strings.isNullOrEmpty(url)) {
      throw (new ResourceNotFoundException()).caching(CacheControl.PUBLIC(1, TimeUnit.HOURS));
    }
    return Response.redirect(url);
  }
}
