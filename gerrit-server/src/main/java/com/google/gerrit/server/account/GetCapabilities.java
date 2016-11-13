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

package com.google.gerrit.server.account;

import static com.google.gerrit.common.data.GlobalCapability.ACCESS_DATABASE;
import static com.google.gerrit.common.data.GlobalCapability.CREATE_ACCOUNT;
import static com.google.gerrit.common.data.GlobalCapability.CREATE_GROUP;
import static com.google.gerrit.common.data.GlobalCapability.CREATE_PROJECT;
import static com.google.gerrit.common.data.GlobalCapability.EMAIL_REVIEWERS;
import static com.google.gerrit.common.data.GlobalCapability.FLUSH_CACHES;
import static com.google.gerrit.common.data.GlobalCapability.KILL_TASK;
import static com.google.gerrit.common.data.GlobalCapability.MAINTAIN_SERVER;
import static com.google.gerrit.common.data.GlobalCapability.MODIFY_ACCOUNT;
import static com.google.gerrit.common.data.GlobalCapability.PRIORITY;
import static com.google.gerrit.common.data.GlobalCapability.RUN_GC;
import static com.google.gerrit.common.data.GlobalCapability.STREAM_EVENTS;
import static com.google.gerrit.common.data.GlobalCapability.VIEW_ALL_ACCOUNTS;
import static com.google.gerrit.common.data.GlobalCapability.VIEW_CACHES;
import static com.google.gerrit.common.data.GlobalCapability.VIEW_CONNECTIONS;
import static com.google.gerrit.common.data.GlobalCapability.VIEW_PLUGINS;
import static com.google.gerrit.common.data.GlobalCapability.VIEW_QUEUE;

import com.google.common.collect.Iterables;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.extensions.config.CapabilityDefinition;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.OptionUtil;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.account.AccountResource.Capability;
import com.google.gerrit.server.git.QueueProvider;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.Iterator;
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

  private final Provider<CurrentUser> self;
  private final DynamicMap<CapabilityDefinition> pluginCapabilities;

  @Inject
  GetCapabilities(Provider<CurrentUser> self, DynamicMap<CapabilityDefinition> pluginCapabilities) {
    this.self = self;
    this.pluginCapabilities = pluginCapabilities;
  }

  @Override
  public Object apply(AccountResource resource) throws AuthException {
    if (self.get() != resource.getUser() && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("restricted to administrator");
    }

    CapabilityControl cc = resource.getUser().getCapabilities();
    Map<String, Object> have = new LinkedHashMap<>();
    for (String name : GlobalCapability.getAllNames()) {
      if (!name.equals(PRIORITY) && want(name) && cc.canPerform(name)) {
        if (GlobalCapability.hasRange(name)) {
          have.put(name, new Range(cc.getRange(name)));
        } else {
          have.put(name, true);
        }
      }
    }
    for (String pluginName : pluginCapabilities.plugins()) {
      for (String capability : pluginCapabilities.byPlugin(pluginName).keySet()) {
        String name = String.format("%s-%s", pluginName, capability);
        if (want(name) && cc.canPerform(name)) {
          have.put(name, true);
        }
      }
    }

    have.put(ACCESS_DATABASE, cc.canAccessDatabase());
    have.put(CREATE_ACCOUNT, cc.canCreateAccount());
    have.put(CREATE_GROUP, cc.canCreateGroup());
    have.put(CREATE_PROJECT, cc.canCreateProject());
    have.put(EMAIL_REVIEWERS, cc.canEmailReviewers());
    have.put(FLUSH_CACHES, cc.canFlushCaches());
    have.put(KILL_TASK, cc.canKillTask());
    have.put(MAINTAIN_SERVER, cc.canMaintainServer());
    have.put(MODIFY_ACCOUNT, cc.canModifyAccount());
    have.put(RUN_GC, cc.canRunGC());
    have.put(STREAM_EVENTS, cc.canStreamEvents());
    have.put(VIEW_ALL_ACCOUNTS, cc.canViewAllAccounts());
    have.put(VIEW_CACHES, cc.canViewCaches());
    have.put(VIEW_CONNECTIONS, cc.canViewConnections());
    have.put(VIEW_PLUGINS, cc.canViewPlugins());
    have.put(VIEW_QUEUE, cc.canViewQueue());

    QueueProvider.QueueType queue = cc.getQueueType();
    if (queue != QueueProvider.QueueType.INTERACTIVE
        || (query != null && query.contains(PRIORITY))) {
      have.put(PRIORITY, queue);
    }

    Iterator<Map.Entry<String, Object>> itr = have.entrySet().iterator();
    while (itr.hasNext()) {
      Map.Entry<String, Object> e = itr.next();
      if (!want(e.getKey())) {
        itr.remove();
      } else if (e.getValue() instanceof Boolean && !((Boolean) e.getValue())) {
        itr.remove();
      }
    }

    return OutputFormat.JSON
        .newGson()
        .toJsonTree(have, new TypeToken<Map<String, Object>>() {}.getType());
  }

  private boolean want(String name) {
    return query == null || query.contains(name.toLowerCase());
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
    @Override
    public BinaryResult apply(Capability resource) {
      return BinaryResult.create("ok\n");
    }
  }
}
