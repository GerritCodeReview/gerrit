package com.google.gerrit.server.project;

import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.server.CurrentUser;

public class BranchControl {

  private final CurrentUser user;
  private final Branch.NameKey branch;

  public BranchControl (final CurrentUser user, final Branch.NameKey branch) {
    this.user = user;
    this.branch = branch;
  }

  public boolean canModify() {
    System.err.println("Branch:" + branch.get());
    System.err.println("User:" + user.toString());
    return false;
  }
}
