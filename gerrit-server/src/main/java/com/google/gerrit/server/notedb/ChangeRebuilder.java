package com.google.gerrit.server.notedb;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.transport.ReceiveCommand;

import java.io.IOException;
import java.util.List;


public interface ChangeRebuilder {

  ListenableFuture<List<ReceiveCommand>> rebuildAsync(Change.Id id,
      ListeningExecutorService executor);

  List<ReceiveCommand> rebuild(ReviewDb db, Change.Id changeId)
      throws NoSuchChangeException, IOException, OrmException,
      ConfigInvalidException;

}
