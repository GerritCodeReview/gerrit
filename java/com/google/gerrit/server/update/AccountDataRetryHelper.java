// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.update;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

@Singleton
public class AccountDataRetryHelper extends AbstractRetryHelper {

  @VisibleForTesting
  @Inject
  public AccountDataRetryHelper(@GerritServerConfig Config cfg, Metrics metrics) {
    super(cfg, metrics);
  }

  public <I, O> O execute(I input, Action<I, O> action)
      throws IOException, ConfigInvalidException, OrmException {
    try {
      return doExecute(input, action, defaults());
    } catch (Throwable t) {
      Throwables.throwIfInstanceOf(t, IOException.class);
      Throwables.throwIfInstanceOf(t, ConfigInvalidException.class);
      Throwables.throwIfInstanceOf(t, OrmException.class);
      throw new OrmException(t);
    }
  }
}
