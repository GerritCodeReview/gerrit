package com.google.gerrit.server.git;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.Inject;

import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

import java.util.Collection;

class LazyPostReceiveHookChain implements PostReceiveHook {
  private final DynamicSet<PostReceiveHook> hooks;

  @Inject
  LazyPostReceiveHookChain(DynamicSet<PostReceiveHook> hooks) {
    this.hooks = hooks;
  }

  @Override
  public void onPostReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
    for (PostReceiveHook h : hooks) {
      h.onPostReceive(rp, commands);
    }
  }
}
