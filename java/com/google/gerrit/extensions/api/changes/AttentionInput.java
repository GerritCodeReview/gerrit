package com.google.gerrit.extensions.api.changes;

import com.google.gerrit.extensions.restapi.DefaultInput;

/** See {@link com.google.gerrit.entities.AttentionUpdate}. */
public class AttentionInput {
  @DefaultInput public String account;
  public String reason;
  public boolean removal;
}
