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

package com.google.gerrit.server;

/** How the {@link CurrentUser} is accessing Gerrit. */
public enum AccessPath {
  /** An unknown access path, probably should not be special. */
  UNKNOWN,

  /** Access through the web UI. */
  WEB_UI,

  /** Access through an SSH command that is not invoked by Git. */
  SSH_COMMAND,

  /** Access from a Git client using any Git protocol. */
  GIT;
}
