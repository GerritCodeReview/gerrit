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

import com.google.common.collect.Maps;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;

import java.util.Map;

/** List capabilities visible to the calling user. */
public class ListCapabilities implements RestReadView<ConfigResource> {
  @Override
  public Map<String, CapabilityInfo> apply(ConfigResource resource)
      throws AuthException, BadRequestException, ResourceConflictException,
      IllegalArgumentException, SecurityException, IllegalAccessException,
      NoSuchFieldException {
    Map<String, CapabilityInfo> output = Maps.newTreeMap();
    Class<? extends CapabilityConstants> bundleClass =
        CapabilityConstants.get().getClass();
    CapabilityConstants c = CapabilityConstants.get();
    for (String id : GlobalCapability.getAllNames()) {
      String name = (String) bundleClass.getField(id).get(c);
      output.put(id, new CapabilityInfo(id, name));
    }
    return output;
  }

  public static class CapabilityInfo {
    final String kind = "gerritcodereview#capability";
    public String id;
    public String name;

    public CapabilityInfo(String id, String name) {
      this.id = id;
      this.name = name;
    }
  }
}
