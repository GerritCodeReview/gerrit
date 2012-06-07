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

package com.google.gerrit.httpd.rpc.account;

import static com.google.gerrit.common.data.GlobalCapability.CREATE_ACCOUNT;
import static com.google.gerrit.common.data.GlobalCapability.CREATE_GROUP;
import static com.google.gerrit.common.data.GlobalCapability.CREATE_PROJECT;
import static com.google.gerrit.common.data.GlobalCapability.FLUSH_CACHES;
import static com.google.gerrit.common.data.GlobalCapability.KILL_TASK;
import static com.google.gerrit.common.data.GlobalCapability.PRIORITY;
import static com.google.gerrit.common.data.GlobalCapability.START_REPLICATION;
import static com.google.gerrit.common.data.GlobalCapability.VIEW_CACHES;
import static com.google.gerrit.common.data.GlobalCapability.VIEW_CONNECTIONS;
import static com.google.gerrit.common.data.GlobalCapability.VIEW_QUEUE;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.httpd.RestApiServlet;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.git.QueueProvider;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.kohsuke.args4j.Option;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class AccountCapabilitiesServlet extends RestApiServlet {
  private static final long serialVersionUID = 1L;
  private final ParameterParser paramParser;
  private final Provider<Impl> factory;

  @Inject
  AccountCapabilitiesServlet(final Provider<CurrentUser> currentUser,
      ParameterParser paramParser, Provider<Impl> factory) {
    super(currentUser);
    this.paramParser = paramParser;
    this.factory = factory;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    Impl impl = factory.get();
    if (acceptsJson(req)) {
      impl.format = OutputFormat.JSON_COMPACT;
    }
    if (paramParser.parse(impl, req, res)) {
      impl.compute();

      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      OutputStreamWriter out = new OutputStreamWriter(buf, "UTF-8");
      if (impl.format.isJson()) {
        res.setContentType(JSON_TYPE);
        buf.write(JSON_MAGIC);
        impl.format.newGson().toJson(
            impl.have,
            new TypeToken<Map<String, Object>>() {}.getType(),
            out);
        out.flush();
        buf.write('\n');
      } else {
        res.setContentType("text/plain");
        for (Map.Entry<String, Object> e : impl.have.entrySet()) {
          out.write(e.getKey());
          if (!(e.getValue() instanceof Boolean)) {
            out.write(": ");
            out.write(e.getValue().toString());
          }
          out.write('\n');
        }
        out.flush();
      }
      res.setCharacterEncoding("UTF-8");
      send(req, res, buf.toByteArray());
    }
  }

  static class Impl {
    private final CapabilityControl cc;
    private final Map<String, Object> have;

    @Option(name = "--format", metaVar = "FMT", usage = "Output display format")
    private OutputFormat format = OutputFormat.TEXT;

    @Option(name = "-q", metaVar = "CAP", multiValued = true, usage = "Capability to inspect")
    void addQuery(String name) {
      if (query == null) {
        query = Sets.newHashSet();
      }
      query.add(name.toLowerCase());
    }
    private Set<String> query;

    @Inject
    Impl(CurrentUser user) {
      cc = user.getCapabilities();
      have = Maps.newLinkedHashMap();
    }

    void compute() {
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
      have.put(KILL_TASK, cc.canKillTask());
      have.put(VIEW_CACHES, cc.canViewCaches());
      have.put(FLUSH_CACHES, cc.canFlushCaches());
      have.put(VIEW_CONNECTIONS, cc.canViewConnections());
      have.put(VIEW_QUEUE, cc.canViewQueue());
      have.put(START_REPLICATION, cc.canStartReplication());

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
    }

    private boolean want(String name) {
      return query == null || query.contains(name.toLowerCase());
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
}
