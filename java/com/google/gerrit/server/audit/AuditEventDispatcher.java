package com.google.gerrit.server.audit;

public interface AuditEventDispatcher {

  void dispatch(AuditEvent action);
}
