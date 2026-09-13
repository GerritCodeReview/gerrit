// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.sshd.CommandMetaData;

/** Prints the caller's OAuth access token, refreshing it first if it has expired. */
@CommandMetaData(
    name = "get",
    description = "Print the caller's OAuth access token, refreshing it if expired")
final class OAuthTokenGetCommand extends OAuthTokenSubcommand {
  @Override
  protected void run() throws Failure {
    stdout.print(currentToken().getToken() + "\n");
  }
}
