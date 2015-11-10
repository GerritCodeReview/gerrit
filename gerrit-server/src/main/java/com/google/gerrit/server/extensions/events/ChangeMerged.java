package com.google.gerrit.server.extensions.events;

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.events.ChangeMergedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import java.io.IOException;

public class ChangeMerged {

  private final DynamicSet<ChangeMergedListener> listeners;
  private final ChangeEventUtil util;

  @Inject
  ChangeMerged(DynamicSet<ChangeMergedListener> listeners,
      ChangeEventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  public void fire(ChangeInfo change, RevisionInfo revision,
      AccountInfo submitter, String newRevisionId) {
    Event e = new Event(change, revision, submitter, newRevisionId);
    for (ChangeMergedListener l : listeners) {
      l.onChangeMerged(e);
    }
  }

  public void fire(Change change, PatchSet ps, Account submitter,
      String newRevisionId) throws OrmException, PatchListNotAvailableException, GpgException, IOException {
    fire(util.changeInfo(change),
        util.revisionInfo(ps),
        util.accountInfo(submitter),
        newRevisionId);
  }

  private static class Event implements ChangeMergedListener.Event {
    private final ChangeInfo change;
    private final RevisionInfo revision;
    private final AccountInfo submitter;
    private final String newRevisionId;

    Event(ChangeInfo change, RevisionInfo revision, AccountInfo submitter,
        String newRevisionId) {
      this.change = change;
      this.revision = revision;
      this.submitter = submitter;
      this.newRevisionId = newRevisionId;
    }

    @Override
    public RevisionInfo getRevision() {
      return revision;
    }

    @Override
    public ChangeInfo getChange() {
      return change;
    }

    @Override
    public AccountInfo getSubmitter() {
      return submitter;
    }

    @Override
    public String getNewRevisionId() {
      return newRevisionId;
    }
  }
}
