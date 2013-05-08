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

package com.google.gerrit.server.common;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.common.Capability;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.group.GroupJson.GroupInfo;
import com.google.gson.reflect.TypeToken;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import java.util.List;
import java.util.Map;

/** List capabilities visible to the calling user. */
public class ListCapabilities implements RestReadView<TopLevelResource> {
  private final DynamicSet<Capability> capabilities;

  @Inject
  protected ListCapabilities(final DynamicSet<Capability> capabilities) {
    this.capabilities = capabilities;
  }

  @Override
  public Object apply(TopLevelResource resource) throws AuthException,
      BadRequestException, ResourceConflictException, Exception {
    final Map<String, CapabilityInfo> output = Maps.newTreeMap();
    Class<? extends CapabilityConstants> bundleClass = CapabilityConstants.get().getClass();
    CapabilityConstants c = CapabilityConstants.get();
    // core
    for (String s : Splitter.on(", ").split(CapabilityConstants.get().capabilityNames)) {
      String v = (String) bundleClass.getField(s).get(c);
      CapabilityInfo info = new CapabilityInfo(s, v);
      output.put(s, info);
    }
    // plugin
    for (Capability p : capabilities) {
      CapabilityInfo info = new CapabilityInfo(p.getName(), p.getDescr());
      output.put(p.getName(), info);
    }

    return OutputFormat.JSON.newGson().toJsonTree(output,
        new TypeToken<Map<String, GroupInfo>>() {}.getType());
  }

  public List<GroupInfo> get() throws OrmException {
    List<GroupInfo> groupInfos = Lists.newArrayList();
    return groupInfos;
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
