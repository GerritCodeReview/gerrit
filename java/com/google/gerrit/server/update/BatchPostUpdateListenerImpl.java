package com.google.gerrit.server.update;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.extensions.events.AttentionSetObserver;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.query.change.ChangeData;
import javax.inject.Inject;

public class BatchPostUpdateListenerImpl implements BatchChangeUpdateListener {
  private final AttentionSetObserver attentionSetObserver;
  private final Multimap<ChangeNotes, AttentionSetUpdate> attentionSetUpdates = ArrayListMultimap.create();
  class ChangeUpdateListenerImpl implements ChangeUpdate.ChangeUpdateListener {
    private final ChangeNotes change;
    public ChangeUpdateListenerImpl(ChangeNotes change) {
      this.change = change;
    }
    @Override
    public void attentionSetUpdate(AttentionSetUpdate attentionSetUpdate) {
      attentionSetUpdates.put(change, attentionSetUpdate);
    }
  }
  @Inject
  BatchPostUpdateListenerImpl(AttentionSetObserver attentionSetObserver) {
    this.attentionSetObserver = attentionSetObserver;
  }

  @Override
  public ChangeUpdate.ChangeUpdateListener getChangeUpdateListener(ChangeNotes change) {
    return new ChangeUpdateListenerImpl(change);
  }

  @Override
  public void postUpdate(PostUpdateContext ctx) {
    for(ChangeNotes changeNotes: attentionSetUpdates.keySet()) {
      ChangeData changeData = ctx.getChangeData(changeNotes);
      AccountState accountState = ctx.getAccount();
      attentionSetUpdates.get(changeNotes).forEach(attentionSetUpdate -> attentionSetObserver.fire(changeData, accountState, attentionSetUpdate, ctx.getWhen()));
    }
  }
}
