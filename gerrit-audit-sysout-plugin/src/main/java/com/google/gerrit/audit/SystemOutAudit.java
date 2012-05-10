package com.google.gerrit.audit;

import com.google.gerrit.audit.AuditEvent;
import com.google.gerrit.audit.AuditListener;
import com.google.gerrit.extensions.annotations.Listen;
import com.google.inject.Singleton;

@Listen
@Singleton
public class SystemOutAudit implements AuditListener {

  @Override
  public void track(AuditEvent event) {
      System.out.println("AUDIT> " + event);
  }

}
