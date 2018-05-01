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

package com.google.gerrit.server;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Date;
import java.util.TimeZone;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.util.SystemReader;

@Singleton
public class GerritPersonIdentFactory {
  private final GerritServerIdent serverIdent;
  private final TimeZone timeZone;

  @Inject
  GerritPersonIdentFactory(GerritServerIdent serverIdent, TimeZone timeZone) {
    this.serverIdent = serverIdent;
    this.timeZone = timeZone;
  }

  public PersonIdent createAtCurrentTime() {
    return create(new Date(SystemReader.getInstance().getCurrentTime()));
  }

  public PersonIdent create(Date when) {
    return new PersonIdent(serverIdent.name(), serverIdent.email(), when, timeZone);
  }
}
