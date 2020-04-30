package com.google.gerrit.server.mail.send;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class MessageId {

  public abstract String id();
}
