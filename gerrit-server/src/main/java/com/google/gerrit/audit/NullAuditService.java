package com.google.gerrit.audit;


public class NullAuditService implements AuditService {

  @Override
  public boolean flush() {
    return true;
  }

  @Override
  public void track(AuditRecord action) {
  }

}
