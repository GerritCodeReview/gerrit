package com.google.gerrit.server.events;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;

import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;

public class CommitReceivedEvent extends ChangeEvent {
  public final ReceiveCommand command;
  public final Project project;
  public final String refName;
  public final RevCommit commit;
  public final IdentifiedUser user;

  public CommitReceivedEvent(ReceiveCommand command, Project project,
      String refName, RevCommit commit, IdentifiedUser user) {
    this.command = command;
    this.project = project;
    this.refName = refName;
    this.commit = commit;
    this.user = user;
  }
}
