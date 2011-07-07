// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.test;

import org.apache.log4j.Level;
import org.slf4j.Logger;

public enum TraceLevel {
  TRACE {
    @Override
    public void log(final Logger log, final String message, final Throwable t) {
      log.trace(message, t);
    }
  },

  DEBUG {
    @Override
    public void log(final Logger log, final String message, final Throwable t) {
      log.debug(message, t);
    }
  },

  INFO {
    @Override
    public void log(final Logger log, final String message, final Throwable t) {
      log.info(message, t);
    }
  },

  WARN {
    @Override
    public void log(final Logger log, final String message, final Throwable t) {
      log.warn(message, t);
    }
  },

  ERROR {
    @Override
    public void log(final Logger log, final String message, final Throwable t) {
      log.error(message, t);
    }
  };

  public Level getLevel() {
    return Level.toLevel(name());
  }

  public final void log(Logger log, String message) {
    log(log, message, null);
  }

  public abstract void log(Logger log, String message, Throwable t);
}
