package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.Branch;

import java.util.ArrayList;
import java.util.List;

/**
 * It holds list of branches and boolean to indicate
 * if it is allowed to add new branches.
 */
public final class ListBranchesResult {
  private boolean canAdd;

  private List<Branch> branches;

  protected ListBranchesResult() {
    this(new ArrayList<Branch>(), false);
  }

  public ListBranchesResult(final List<Branch> branches, boolean canAdd) {
    this.branches = branches;
    this.canAdd = canAdd;
  }

  public boolean getCanAdd() {
    return canAdd;
  }

  public void setCanAdd(boolean canAdd) {
    this.canAdd = canAdd;
  }

  public List<Branch> getBranches() {
    return branches;
  }

  public void setBranches(List<Branch> branches) {
    this.branches = branches;
  }
}
