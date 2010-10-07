package com.google.gerrit.server.events;

public class RefChangedEvent extends ChangeEvent {
  public final String type = "ref-changed";
  public ChangeAttribute change;
  public AccountAttribute submitter;
  public String revision;
}
