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
import static com.google.gerrit.common.data.GlobalCapability.PRIORITY;
import static com.google.gerrit.common.data.GlobalCapability.QUERY;
import static com.google.gerrit.common.data.GlobalCapability.RUN_GC;
import static com.google.gerrit.common.data.GlobalCapability.START_REPLICATION;
import static com.google.gerrit.common.data.GlobalCapability.STREAM_EVENTS;
import static com.google.gerrit.common.data.GlobalCapability.VIEW_CACHES;
import static com.google.gerrit.common.data.GlobalCapability.VIEW_CONNECTIONS;
import static com.google.gerrit.common.data.GlobalCapability.VIEW_QUEUE;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
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

import org.kohsuke.args4j.Option;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

class GetCapabilities implements RestReadView<AccountResource> {
  @Deprecated
  @Option(name = "--format", usage = "(deprecated) output format")
  private OutputFormat format;

  @Option(name = "-q", metaVar = "CAP", multiValued = true, usage = "Capability to inspect")
  void addQuery(String name) {
    if (query == null) {
      query = Sets.newHashSet();
    }
    Iterables.addAll(query, OptionUtil.splitOptionValue(name));
  }
  private Set<String> query;

  private final Provider<CurrentUser> self;

  @Inject
  GetCapabilities(Provider<CurrentUser> self) {
    this.self = self;
  }

  @Override
  public Object apply(AccountResource resource)
      throws BadRequestException, Exception {
    if (self.get() != resource.getUser()
        && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("restricted to administrator");
    }

    CapabilityControl cc = resource.getUser().getCapabilities();
    Map<String, Object> have = Maps.newLinkedHashMap();
    for (String name : GlobalCapability.getAllNames()) {
      if (!name.equals(PRIORITY) && want(name) && cc.canPerform(name)) {
        if (GlobalCapability.hasRange(name)) {
          have.put(name, new Range(cc.getRange(name)));
        } else {
          have.put(name, true);
        }
      }
    }

    have.put(CREATE_ACCOUNT, cc.canCreateAccount());
    have.put(CREATE_GROUP, cc.canCreateGroup());
    have.put(CREATE_PROJECT, cc.canCreateProject());
    have.put(EMAIL_REVIEWERS, cc.canEmailReviewers());
    have.put(KILL_TASK, cc.canKillTask());
    have.put(VIEW_CACHES, cc.canViewCaches());
    have.put(FLUSH_CACHES, cc.canFlushCaches());
    have.put(VIEW_CONNECTIONS, cc.canViewConnections());
    have.put(VIEW_QUEUE, cc.canViewQueue());
    have.put(QUERY, cc.canQuery());
    have.put(RUN_GC, cc.canRunGC());
    have.put(START_REPLICATION, cc.canStartReplication());
    have.put(STREAM_EVENTS, cc.canStreamEvents());
    have.put(ACCESS_DATABASE, cc.canAccessDatabase());

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

    if (format == OutputFormat.TEXT) {
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<String, Object> e : have.entrySet()) {
        sb.append(e.getKey());
        if (!(e.getValue() instanceof Boolean)) {
          sb.append(": ");
          sb.append(e.getValue().toString());
        }
        sb.append('\n');
      }
      return BinaryResult.create(sb.toString());
    } else {
      return OutputFormat.JSON.newGson().toJsonTree(
        have,
        new TypeToken<Map<String, Object>>() {}.getType());
    }
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

  static class CheckOne implements RestReadView<AccountResource.Capability> {
    @Override
    public Object apply(Capability resource) {
      return BinaryResult.create("ok\n");
    }
  }
}
