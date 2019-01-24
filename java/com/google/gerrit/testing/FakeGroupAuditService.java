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

package com.google.gerrit.testing;

import static java.util.Comparator.comparing;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.registration.DynamicSet;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Singleton
public class FakeGroupAuditService extends AuditService {
  public static class Module extends AbstractModule {
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

  private final BlockingQueue<AuditEvent> auditEvents;

  @Inject
  FakeGroupAuditService(
      PluginSetContext<AuditListener> auditListeners,
      PluginSetContext<GroupAuditListener> groupAuditListeners) {
    super(auditListeners, groupAuditListeners);
    auditEvents = new LinkedBlockingQueue<>();
  }

  @Override
  public void dispatch(AuditEvent action) {
    super.dispatch(action);
    auditEvents.add(action);
  }

  public void clearEvents() throws Exception {
    // There's no way to know whether there might be events in-flight, for example in the finally
    // block after writing data over an HTTP connection that the caller has read. We could
    // potentially keep track of in-flight events, but that would be an invasive change to the
    // AuditService interface and its callers. Instead, just sleep.
    SECONDS.sleep(5);
    auditEvents.clear();
  }

  public ImmutableList<AuditEvent> drainEvents(int expectedSize) throws Exception {
    ArrayList<HttpAuditEvent> result = new ArrayList<>();
    for (int i = 0; i < expectedSize; i++) {
      AuditEvent e = auditEvents.poll(30, SECONDS);
      if (e == null) {
        throw new AssertionError(
            String.format("Timeout after receiving %d/%d audit events", i, expectedSize));
      }
      result.add((HttpAuditEvent) e);
    }
    result.sort(comparing(e -> e.when));

    // Poll one more time, but use a shorter timeout to avoid slowing down tests too much. This can
    // lead to false positives (the test flakily passing when it shouldn't). Unfortunately the
    // current audit interface doesn't let us know that there are pending audit events that might be
    // in flight; fixing that would require something like sending separate audit start/end events.
    AuditEvent e = auditEvents.poll(1, SECONDS);
    if (e != null) {
      throw new AssertionError(
          String.format(
              "Audit event remaining in queue after draining item #%d:\n  %s\n"
                  + "Previous events:\n  %s",
              expectedSize, e, result.stream().map(Object::toString).collect(joining("\n  "))));
    }

    return ImmutableList.copyOf(result);
  }
}
