package com.google.gerrit.httpd.rpc.changedetail;

import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSet.Id;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

/** Creates a commit message string from a {@link PatchSet} */
public class PatchSetCommitMessageFactory extends Handler<String> {
  interface Factory {
    PatchSetCommitMessageFactory create(PatchSet.Id id);
  }

  private PatchSetInfoFactory infoFactory;
  private Id psId;

  @Inject
  public PatchSetCommitMessageFactory(final PatchSetInfoFactory psif,
      @Assisted final PatchSet.Id id) {
    this.infoFactory = psif;
    this.psId = id;
  }

  @Override
  public String call() throws Exception {
    return infoFactory.get(psId).getMessage();
  }
}
