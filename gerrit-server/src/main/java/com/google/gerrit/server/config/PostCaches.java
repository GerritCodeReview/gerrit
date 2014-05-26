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
import com.google.gerrit.server.config.PostCaches.Input;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@RequiresCapability(GlobalCapability.VIEW_CACHES)
@Singleton
public class PostCaches implements RestModifyView<ConfigResource, Input> {
  public static class Input {
    public Operation operation;
  }

  public static enum Operation {
    LIST;
  }

  private final Provider<ListCaches> listCaches;

  @Inject
  public PostCaches(Provider<ListCaches> listCaches) {
    this.listCaches = listCaches;
  }

  @Override
  public Object apply(ConfigResource rsrc, Input input)
      throws BadRequestException {
    if (!Operation.LIST.equals(input.operation)) {
      throw new BadRequestException("unsupported operation: " + input.operation);
    }
    return listCaches.get().getCaches().keySet();
  }
}
