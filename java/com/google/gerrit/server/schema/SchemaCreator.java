// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Populates initial NoteDb schema, {@code All-Projects} configuration, etc. */
public interface SchemaCreator {

  /**
   * Create the schema, assuming it does not already exist.
   *
   * <p>Fails if the schema does exist.
   *
   * @throws IOException an error occurred.
   * @throws ConfigInvalidException an error occurred.
   */
  void create() throws IOException, ConfigInvalidException;

  /**
   * Create the schema only if it does not already exist.
   *
   * <p>Succeeds if the schema does exist.
   *
   * @throws IOException an error occurred.
   * @throws ConfigInvalidException an error occurred.
   */
  void ensureCreated() throws IOException, ConfigInvalidException;
}
