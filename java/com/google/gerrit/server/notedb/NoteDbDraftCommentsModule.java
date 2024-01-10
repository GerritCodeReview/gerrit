package com.google.gerrit.server.notedb;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.ChangeDraftUpdateExecutor;
import com.google.gerrit.server.DraftCommentsReader;
import com.google.inject.Singleton;

public class NoteDbDraftCommentsModule extends FactoryModule {
  @Override
  public void configure() {
    factory(ChangeDraftNotesUpdate.Factory.class);
    factory(ChangeDraftNotesUpdate.Executor.Factory.class);
    factory(DraftCommentNotes.Factory.class);

    bind(DraftCommentsReader.class).to(DraftCommentsNotesReader.class).in(Singleton.class);
    bind(ChangeDraftUpdateExecutor.AbstractFactory.class)
        .to(ChangeDraftNotesUpdate.Executor.Factory.class)
        .in(Singleton.class);
  }
}
