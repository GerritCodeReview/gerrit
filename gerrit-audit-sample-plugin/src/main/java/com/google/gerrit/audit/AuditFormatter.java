package com.google.gerrit.audit;

public abstract interface AuditFormatter {

  String format(Object result);

}
