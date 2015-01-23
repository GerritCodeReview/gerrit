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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.kohsuke.args4j.Option;

public class GetChange implements RestReadView<ChangeResource> {
  private final ChangeJson json;

  @Option(name = "-o", usage = "Output options")
  void addOption(ListChangesOption o) {
    json.addOption(o);
  }

  @Option(name = "-O", usage = "Output option flags, in hex")
  void setOptionFlagsHex(String hex) {
    json.addOptions(ListChangesOption.fromBits(Integer.parseInt(hex, 16)));
  }

  @Inject
  GetChange(ChangeJson json) {
    this.json = json;
  }

  @Override
  public Response<ChangeInfo> apply(ChangeResource rsrc) throws OrmException {
    return Response.withMustRevalidate(json.format(rsrc));
  }

  Response<ChangeInfo> apply(RevisionResource rsrc) throws OrmException {
    return Response.withMustRevalidate(json.format(rsrc));
  }
}
