package com.google.gerrit.audit;

import com.google.common.collect.Multimap;
import com.google.gerrit.server.CurrentUser;

public class SshAuditEvent extends AuditEvent {

  public SshAuditEvent(String sessionId, CurrentUser who, String what,
      long when, Multimap<String, ?> params, Object result) {
    super(sessionId, who, what, when, params, result);
  }
}
