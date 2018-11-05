package com.google.gerrit.testing;

import com.google.gerrit.server.audit.AuditEvent;
import com.google.gerrit.server.audit.AuditEventDispatcher;
import com.google.inject.AbstractModule;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class FakeAuditEventDispatcherService implements AuditEventDispatcher {

  public static class Module extends AbstractModule {
    @Override
    public void configure() {
      bind(AuditEventDispatcher.class).to(FakeAuditEventDispatcherService.class);
    }
  }

  public List<AuditEvent> auditEvents = new ArrayList();

  public void clearEvents() {
    auditEvents.clear();
  }

  @Override
  public void dispatch(AuditEvent action) {
    auditEvents.add(action);
  }
}
