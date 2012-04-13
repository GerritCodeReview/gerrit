package com.google.gerrit.audit;

import java.util.List;

public interface AuditPersistence {

  public void store(List<AuditRecord> records);

}
