package com.google.gerrit.server.git.validators;

import org.eclipse.jgit.transport.ReceiveCommand;

public interface RejectionHandler {

  public void reject(final ReceiveCommand cmd, final String why);

  public void addMessage(String message);

  public void addError(String error);

}
