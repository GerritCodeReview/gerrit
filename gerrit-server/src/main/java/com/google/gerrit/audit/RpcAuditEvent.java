package com.google.gerrit.audit;

import com.google.common.collect.Multimap;
import com.google.gerrit.server.CurrentUser;

public class RpcAuditEvent extends HttpAuditEvent {

  /**
   * Creates a new audit event with results
   *
   * @param sessionId session id the event belongs to
   * @param who principal that has generated the event
   * @param what object of the event
   * @param when time-stamp of when the event started
   * @param params parameters of the event
   * @param result result of the event
   */
  public RpcAuditEvent(String sessionId, CurrentUser who, String what,
      long when, Multimap<String, ?> params, String httpMethod, Object input,
      int status, Object result) {
    super(sessionId, who, what, when, params, httpMethod, input, status, result);
  }
}
