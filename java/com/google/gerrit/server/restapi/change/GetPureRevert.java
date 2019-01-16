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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.common.PureRevertInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.PureRevert;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import org.kohsuke.args4j.Option;

public class GetPureRevert implements RestReadView<ChangeResource> {

  private final PureRevert pureRevert;
  @Nullable private String claimedOriginal;

  @Option(
      name = "--claimed-original",
      aliases = {"-o"},
      usage = "SHA1 (40 digit hex) of the original commit")
  public void setClaimedOriginal(String claimedOriginal) {
    this.claimedOriginal = claimedOriginal;
  }

  @Inject
  GetPureRevert(PureRevert pureRevert) {
    this.pureRevert = pureRevert;
  }

  @Override
  public PureRevertInfo apply(ChangeResource rsrc)
      throws ResourceConflictException, IOException, BadRequestException, AuthException {
    boolean isPureRevert = pureRevert.get(rsrc.getNotes(), Optional.ofNullable(claimedOriginal));
    return new PureRevertInfo(isPureRevert);
  }
}
