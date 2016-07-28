// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.testutil;

import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.inject.Singleton;

/** {@link NotesMigration} with bits that can be flipped live for testing. */
@Singleton
public class TestNotesMigration extends NotesMigration {
  private volatile boolean readChanges;
  private volatile boolean writeChanges;
  private volatile boolean failOnLoad;
  private volatile PrimaryStorage defaultPrimaryStorage =
      PrimaryStorage.NOTE_DB;

  @Override
  public boolean readChanges() {
    return readChanges;
  }

  @Override
  public boolean readChangeSequence() {
    // Unlike ConfigNotesMigration, read change numbers from NoteDb by default
    // when reads are enabled, to improve test coverage.
    return readChanges;
  }

  // Increase visbility from superclass, as tests may want to check whether
  // NoteDb data is written in specific migration scenarios.
  @Override
  public boolean writeChanges() {
    return writeChanges;
  }

  @Override
  public PrimaryStorage defaultPrimaryStorage() {
    return defaultPrimaryStorage;
  }

  @Override
  public boolean readAccounts() {
    return false;
  }

  @Override
  public boolean writeAccounts() {
    return false;
  }

  @Override
  public boolean failOnLoad() {
    return failOnLoad;
  }

  public TestNotesMigration setReadChanges(boolean readChanges) {
    this.readChanges = readChanges;
    return this;
  }

  public TestNotesMigration setWriteChanges(boolean writeChanges) {
    this.writeChanges = writeChanges;
    return this;
  }

  public TestNotesMigration setDefaultPrimaryStorage(
      PrimaryStorage defaultPrimaryStorage) {
    this.defaultPrimaryStorage = defaultPrimaryStorage;
    return this;
  }

  public TestNotesMigration setFailOnLoad(boolean failOnLoad) {
    this.failOnLoad = failOnLoad;
    return this;
  }

  public TestNotesMigration setAllEnabled(boolean enabled) {
    return setReadChanges(enabled).setWriteChanges(enabled);
  }

  public TestNotesMigration setFromEnv() {
    switch (NoteDbMode.get()) {
      case READ_WRITE:
        setWriteChanges(true);
        setReadChanges(true);
        setDefaultPrimaryStorage(PrimaryStorage.REVIEW_DB);
        break;
      case READ_WRITE_NOTE_DB_PRIMARY:
        setWriteChanges(true);
        setReadChanges(true);
        setDefaultPrimaryStorage(PrimaryStorage.NOTE_DB);
        break;
      case WRITE:
        setWriteChanges(true);
        setReadChanges(false);
        break;
      case CHECK:
      case OFF:
      default:
        setWriteChanges(false);
        setReadChanges(false);
        break;
    }
    return this;
  }
}
