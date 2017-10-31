// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.extensions.systemstatus;

/** Exports current server information to an extension. */
public interface ServerInformation {
  /** Current state of the server. */
  enum State {
    /**
     * The server is starting up, and network connections are not yet being accepted. Plugins or
     * extensions starting during this time are starting for the first time in this process.
     */
    STARTUP,

    /**
     * The server is running and handling requests. Plugins starting during this state may be
     * reloading, or being installed into a running system.
     */
    RUNNING,

    /**
     * The server is attempting a graceful halt of operations and will exit (or be killed by the
     * operating system) soon.
     */
    SHUTDOWN
  }

  State getState();
}
