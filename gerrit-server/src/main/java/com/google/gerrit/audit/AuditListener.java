package com.google.gerrit.audit;

public interface AuditListener {

  void track(AuditEvent action);

}
