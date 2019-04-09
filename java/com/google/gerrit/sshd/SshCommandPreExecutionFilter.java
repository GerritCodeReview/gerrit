package com.google.gerrit.sshd;

import java.util.List;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.sshd.BaseCommand.UnloggedFailure;

@ExtensionPoint
public interface SshCommandPreExecutionFilter {

  /**
   * Check the command and throw an exception if this command must not be run
   * @param command the command
   * @param arguments the list of arguments
   * @throws UnloggedFailure
   */
  void accept(String command, List<String> arguments) throws UnloggedFailure;
}
