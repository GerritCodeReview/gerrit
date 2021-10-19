// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import static java.util.Comparator.comparing;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.httpd.GitOverHttpServlet;
import com.google.gerrit.server.AuditEvent;
import com.google.gerrit.server.audit.AuditListener;
import com.google.gerrit.server.audit.AuditService;
import com.google.gerrit.server.audit.HttpAuditEvent;
import com.google.gerrit.server.audit.group.GroupAuditListener;
import com.google.gerrit.server.group.GroupAuditService;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class FakeGroupAuditService extends AuditService {
  public static class FakeGroupAuditServiceModule extends AbstractModule {
    @Override
    public void configure() {
      DynamicSet.setOf(binder(), GroupAuditListener.class);
      DynamicSet.setOf(binder(), AuditListener.class);

      // Use this fake service at the Guice level rather than depending on tests binding their own
      // audit listeners. If we used per-test listeners, then there would be a race between
      // dispatching the audit events from HTTP requests performed during test setup in
      // AbstractDaemonTest, and the later test setup binding the audit listener. Using a separate
      // audit service implementation ensures all events get recorded.
      bind(GroupAuditService.class).to(FakeGroupAuditService.class);
    }
  }

  private final GitOverHttpServlet.Metrics httpMetrics;
  private final BlockingQueue<HttpAuditEvent> httpEvents;
  private final AtomicLong drainedSoFar;

  @Inject
  FakeGroupAuditService(
      PluginSetContext<AuditListener> auditListeners,
      PluginSetContext<GroupAuditListener> groupAuditListeners,
      GitOverHttpServlet.Metrics httpMetrics) {
    super(auditListeners, groupAuditListeners);
    this.httpMetrics = httpMetrics;
    this.httpEvents = new LinkedBlockingQueue<>();
    this.drainedSoFar = new AtomicLong();
  }

  @Override
  public void dispatch(AuditEvent action) {
    super.dispatch(action);
    if (action instanceof HttpAuditEvent) {
      httpEvents.add((HttpAuditEvent) action);
    }
  }

  public ImmutableList<HttpAuditEvent> drainHttpAuditEvents() throws Exception {
    // Assumes that all HttpAuditEvents are produced by GitOverHttpServlet.
    int expectedSize = Ints.checkedCast(httpMetrics.getRequestsStarted() - drainedSoFar.get());
    List<HttpAuditEvent> result = new ArrayList<>();
    for (int i = 0; i < expectedSize; i++) {
      HttpAuditEvent e = httpEvents.poll(30, SECONDS);
      if (e == null) {
        throw new AssertionError(
            String.format("Timeout after receiving %d/%d audit events", i, expectedSize));
      }
      drainedSoFar.incrementAndGet();
      result.add(e);
    }
    return ImmutableList.sortedCopyOf(comparing(e -> e.when), result);
  }
}
