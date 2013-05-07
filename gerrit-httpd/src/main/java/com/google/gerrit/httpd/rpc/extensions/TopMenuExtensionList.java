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

package com.google.gerrit.httpd.rpc.extensions;

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.webui.TopMenuExtension;
import com.google.inject.Inject;

import java.util.List;

public class TopMenuExtensionList implements RestReadView<TopLevelResource> {
  private DynamicSet<TopMenuExtension> extensions;

  @Inject
  TopMenuExtensionList(DynamicSet<TopMenuExtension> extensions) {
    this.extensions = extensions;
  }

  @Override
  public Object apply(TopLevelResource resource) throws AuthException,
      BadRequestException, ResourceConflictException, Exception {
    List<TopMenuExtension.MenuEntry> entrys = Lists.newArrayList();
    for (TopMenuExtension extension : extensions) {
      entrys.addAll(extension.getEntrys());
    }
    return entrys;
  }
}
