package com.google.gerrit.audit;

public interface AuditService {
  public boolean flush();

  public void track(AuditRecord action);
}
