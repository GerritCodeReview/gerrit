package com.google.gerrit.server.update;

import com.google.common.collect.ImmutableList;
import org.eclipse.jgit.lib.BatchRefUpdate;

class BatchUpdateListenerChain implements BatchUpdateListener {

  private ImmutableList<BatchUpdateListener> listeners;

  public static BatchUpdateListenerChain of(BatchUpdateListener... listeners) {
    return new BatchUpdateListenerChain(ImmutableList.copyOf(listeners));
  }

  private BatchUpdateListenerChain(ImmutableList<BatchUpdateListener> listeners) {
    this.listeners = listeners;
  }

  @Override
  public void afterUpdateRepos() throws Exception {
    for (BatchUpdateListener listener : listeners) {
      listener.afterUpdateRepos();
    }
  }

  @Override
  public BatchRefUpdate beforeUpdateRefs(BatchRefUpdate bru) {
    for (BatchUpdateListener listener : listeners) {
      bru = listener.beforeUpdateRefs(bru);
    }
    return bru;
  }

  @Override
  public void afterUpdateRefs() throws Exception {
    for (BatchUpdateListener listener : listeners) {
      listener.afterUpdateRefs();
    }
  }

  @Override
  public void afterUpdateChanges() throws Exception {
    for (BatchUpdateListener listener : listeners) {
      listener.afterUpdateChanges();
    }
  }
}
