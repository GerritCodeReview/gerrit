// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.util.time;

import com.google.common.annotations.VisibleForTesting;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.function.LongSupplier;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;

/** Static utility methods for dealing with dates and times. */
public class TimeUtil {
  private static final LongSupplier SYSTEM_CURRENT_MILLIS_SUPPLIER = System::currentTimeMillis;

  private static volatile LongSupplier currentMillisSupplier = SYSTEM_CURRENT_MILLIS_SUPPLIER;

  public static long nowMs() {
    // We should rather use Instant.now(Clock).toEpochMilli() instead but this would require some
    // changes in our testing code as we wouldn't have clock steps anymore.
    return currentMillisSupplier.getAsLong();
  }

  public static Instant now() {
    return Instant.ofEpochMilli(nowMs());
  }

  public static Timestamp nowTs() {
    return new Timestamp(nowMs());
  }

  public static Timestamp truncateToSecond(Timestamp t) {
    return new Timestamp((t.getTime() / 1000) * 1000);
  }

  @VisibleForTesting
  public static void setCurrentMillisSupplier(LongSupplier customCurrentMillisSupplier) {
    currentMillisSupplier = customCurrentMillisSupplier;

    SystemReader oldSystemReader = SystemReader.getInstance();
    if (!(oldSystemReader instanceof GerritSystemReader)) {
      SystemReader.setInstance(new GerritSystemReader(oldSystemReader));
    }
  }

  @VisibleForTesting
  public static void resetCurrentMillisSupplier() {
    currentMillisSupplier = SYSTEM_CURRENT_MILLIS_SUPPLIER;
    SystemReader.setInstance(null);
  }

  private static class GerritSystemReader extends SystemReader {
    SystemReader delegate;

    GerritSystemReader(SystemReader delegate) {
      this.delegate = delegate;
    }

    @Override
    public String getHostname() {
      return delegate.getHostname();
    }

    @Override
    public String getenv(String variable) {
      return delegate.getenv(variable);
    }

    @Override
    public String getProperty(String key) {
      return delegate.getProperty(key);
    }

    @Override
    public FileBasedConfig openUserConfig(Config parent, FS fs) {
      return delegate.openUserConfig(parent, fs);
    }

    @Override
    public FileBasedConfig openSystemConfig(Config parent, FS fs) {
      return delegate.openSystemConfig(parent, fs);
    }

    @Override
    public FileBasedConfig openJGitConfig(Config parent, FS fs) {
      return delegate.openJGitConfig(parent, fs);
    }

    @Override
    public long getCurrentTime() {
      return currentMillisSupplier.getAsLong();
    }

    @Override
    public int getTimezone(long when) {
      return delegate.getTimezone(when);
    }
  }

  private TimeUtil() {}
}
