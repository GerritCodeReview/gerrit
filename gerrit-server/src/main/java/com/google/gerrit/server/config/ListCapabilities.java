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

package com.google.gerrit.server.config;

import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.group.GroupJson.GroupInfo;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;

import java.util.Map;

/** List capabilities visible to the calling user. */
public class ListCapabilities implements RestReadView<ConfigResource> {

  @Inject
  protected ListCapabilities() {
  }

  @Override
  public Object apply(ConfigResource resource) throws AuthException,
      BadRequestException, ResourceConflictException, Exception {
    final Map<String, CapabilityInfo> output = Maps.newTreeMap();
    Class<? extends CapabilityConstants> bundleClass = CapabilityConstants.get().getClass();
    CapabilityConstants c = CapabilityConstants.get();
    for (String s : Splitter.on(", ").split(CapabilityConstants.get().capabilityNames)) {
      String v = (String) bundleClass.getField(s).get(c);
      CapabilityInfo info = new CapabilityInfo(s, v);
      output.put(s, info);
    }
    return OutputFormat.JSON.newGson().toJsonTree(output,
        new TypeToken<Map<String, GroupInfo>>() {}.getType());
  }

  public static class CapabilityInfo {
    public CapabilityInfo(final String id, final String name) {
      this.id = id;
      this.name = name;
    }
    final String kind = "gerritcodereview#capability";
    public String id;
    public String name;
  }
}
