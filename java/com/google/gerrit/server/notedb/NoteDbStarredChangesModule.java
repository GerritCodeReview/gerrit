package com.google.gerrit.server.notedb;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.StarredChangesReader;
import com.google.gerrit.server.StarredChangesWriter;
import com.google.inject.Singleton;

public class NoteDbStarredChangesModule extends FactoryModule {
  @Override
  public void configure() {
    bind(StarredChangesReader.class).to(StarredChangesUtilNoteDbImpl.class).in(Singleton.class);
    bind(StarredChangesWriter.class).to(StarredChangesUtilNoteDbImpl.class).in(Singleton.class);
  }
}
