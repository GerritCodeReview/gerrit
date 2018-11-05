package com.google.gerrit.server.audit;

import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.inject.Singleton;

import javax.inject.Inject;

@Singleton
public class AuditEventDispatcherImpl implements AuditEventDispatcher {

  private final PluginSetContext<AuditListener> auditListeners;

  @Inject
  public AuditEventDispatcherImpl(PluginSetContext<AuditListener> auditListeners) {
    this.auditListeners = auditListeners;
  }

  @Override
  public void dispatch(AuditEvent action) {
    auditListeners.runEach(l -> l.onAuditableAction(action));
  }
}
