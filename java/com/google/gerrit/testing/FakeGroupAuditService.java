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

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.AuditEvent;
import com.google.gerrit.server.audit.AuditListener;
import com.google.gerrit.server.audit.AuditService;
import com.google.gerrit.server.audit.group.GroupAuditListener;
import com.google.gerrit.server.group.GroupAuditService;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class FakeGroupAuditService extends AuditService {
  public final List<AuditEvent> auditEvents = new ArrayList<>();

  public static class Module extends AbstractModule {
    @Override
    public void configure() {
      DynamicSet.setOf(binder(), GroupAuditListener.class);
      DynamicSet.setOf(binder(), AuditListener.class);
      bind(GroupAuditService.class).to(FakeGroupAuditService.class);
    }
  }

  @Inject
  FakeGroupAuditService(
      PluginSetContext<AuditListener> auditListeners,
      PluginSetContext<GroupAuditListener> groupAuditListeners) {
    super(auditListeners, groupAuditListeners);
  }

  public void clearEvents() {
    auditEvents.clear();
  }

  @Override
  public void dispatch(AuditEvent action) {
    super.dispatch(action);
    synchronized (auditEvents) {
      auditEvents.add(action);
      auditEvents.notifyAll();
    }
  }
}
