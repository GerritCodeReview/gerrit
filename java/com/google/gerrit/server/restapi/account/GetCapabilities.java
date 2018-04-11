// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.restapi.account;

import static com.google.gerrit.common.data.GlobalCapability.PRIORITY;
import static com.google.gerrit.server.permissions.DefaultPermissionMappings.globalOrPluginPermissionName;
import static com.google.gerrit.server.permissions.DefaultPermissionMappings.globalPermissionName;
import static com.google.gerrit.server.permissions.DefaultPermissionMappings.pluginPermissionName;

import com.google.common.collect.Iterables;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.extensions.api.access.GlobalOrPluginPermission;
import com.google.gerrit.extensions.api.access.PluginPermission;
import com.google.gerrit.extensions.config.CapabilityDefinition;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.OptionUtil;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.account.AccountLimits;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountResource.Capability;
import com.google.gerrit.server.git.QueueProvider;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.kohsuke.args4j.Option;

class GetCapabilities implements RestReadView<AccountResource> {
  @Option(name = "-q", metaVar = "CAP", usage = "Capability to inspect")
  void addQuery(String name) {
    if (query == null) {
      query = new HashSet<>();
    }
    Iterables.addAll(query, OptionUtil.splitOptionValue(name));
  }

  private Set<String> query;

  private final PermissionBackend permissionBackend;
  private final AccountLimits.Factory limitsFactory;
  private final Provider<CurrentUser> self;
  private final DynamicMap<CapabilityDefinition> pluginCapabilities;

  @Inject
  GetCapabilities(
      PermissionBackend permissionBackend,
      AccountLimits.Factory limitsFactory,
      Provider<CurrentUser> self,
      DynamicMap<CapabilityDefinition> pluginCapabilities) {
    this.permissionBackend = permissionBackend;
    this.limitsFactory = limitsFactory;
    this.self = self;
    this.pluginCapabilities = pluginCapabilities;
  }

  @Override
  public Object apply(AccountResource rsrc) throws RestApiException, PermissionBackendException {
    permissionBackend.checkUsesDefaultCapabilities();
    PermissionBackend.WithUser perm = permissionBackend.currentUser();
    if (self.get() != rsrc.getUser()) {
      perm.check(GlobalPermission.ADMINISTRATE_SERVER);
      perm = permissionBackend.user(rsrc.getUser());
    }

    Map<String, Object> have = new LinkedHashMap<>();
    for (GlobalOrPluginPermission p : perm.test(permissionsToTest())) {
      have.put(globalOrPluginPermissionName(p), true);
    }

    AccountLimits limits = limitsFactory.create(rsrc.getUser());
    addRanges(have, limits);
    addPriority(have, limits);

    return OutputFormat.JSON
        .newGson()
        .toJsonTree(have, new TypeToken<Map<String, Object>>() {}.getType());
  }

  private Set<GlobalOrPluginPermission> permissionsToTest() {
    Set<GlobalOrPluginPermission> toTest = new HashSet<>();
    for (GlobalPermission p : GlobalPermission.values()) {
      if (want(globalPermissionName(p))) {
        toTest.add(p);
      }
    }

    for (String pluginName : pluginCapabilities.plugins()) {
      for (String capability : pluginCapabilities.byPlugin(pluginName).keySet()) {
        PluginPermission p = new PluginPermission(pluginName, capability);
        if (want(pluginPermissionName(p))) {
          toTest.add(p);
        }
      }
    }
    return toTest;
  }

  private boolean want(String name) {
    return query == null || query.contains(name.toLowerCase());
  }

  private void addRanges(Map<String, Object> have, AccountLimits limits) {
    for (String name : GlobalCapability.getRangeNames()) {
      if (want(name) && limits.hasExplicitRange(name)) {
        have.put(name, new Range(limits.getRange(name)));
      }
    }
  }

  private void addPriority(Map<String, Object> have, AccountLimits limits) {
    QueueProvider.QueueType queue = limits.getQueueType();
    if (queue != QueueProvider.QueueType.INTERACTIVE
        || (query != null && query.contains(PRIORITY))) {
      have.put(PRIORITY, queue);
    }
  }

  private static class Range {
    private transient PermissionRange range;

    @SuppressWarnings("unused")
    private int min;

    @SuppressWarnings("unused")
    private int max;

    Range(PermissionRange r) {
      range = r;
      min = r.getMin();
      max = r.getMax();
    }

    @Override
    public String toString() {
      return range.toString();
    }
  }

  @Singleton
  static class CheckOne implements RestReadView<AccountResource.Capability> {
    private final PermissionBackend permissionBackend;

    @Inject
    CheckOne(PermissionBackend permissionBackend) {
      this.permissionBackend = permissionBackend;
    }

    @Override
    public BinaryResult apply(Capability resource) throws ResourceNotFoundException {
      permissionBackend.checkUsesDefaultCapabilities();
      return BinaryResult.create("ok\n");
    }
  }
}
