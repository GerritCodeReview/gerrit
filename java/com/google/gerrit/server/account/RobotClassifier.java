package com.google.gerrit.server.account;

import com.google.gerrit.entities.Account;

public interface RobotClassifier {
  /** Returns {@code true} if the given user is considered a {@code robot} user. */
  boolean isRobot(Account.Id user);

  /** An instance that can be used for testing and will consider no user to be a robot. */
  class NoOp implements RobotClassifier {
    @Override
    public boolean isRobot(Account.Id user) {
      return false;
    }
  }
}
