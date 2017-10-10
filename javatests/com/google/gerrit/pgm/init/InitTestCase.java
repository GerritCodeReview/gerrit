// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.pgm.init;

import java.io.IOException;
import java.nio.file.Path;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.junit.Ignore;

@Ignore
public abstract class InitTestCase extends LocalDiskRepositoryTestCase {
  protected Path newSitePath() throws IOException {
    return createWorkRepository().getWorkTree().toPath().resolve("test_site");
  }
}
