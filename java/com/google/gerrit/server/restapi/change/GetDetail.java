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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.server.DynamicOptions.DynamicBean;
import com.google.gerrit.server.change.ChangeResource;
import com.google.inject.Inject;
import org.kohsuke.args4j.Option;

public class GetDetail implements RestReadView<ChangeResource>, DynamicOptions.BeanReceiver {
  private final GetChange delegate;

  @Option(name = "-o", usage = "Output options")
  void addOption(ListChangesOption o) {
    delegate.addOption(o);
  }

  @Option(name = "-O", usage = "Output option flags, in hex")
  void setOptionFlagsHex(String hex) {
    delegate.setOptionFlagsHex(hex);
  }

  @Inject
  GetDetail(GetChange delegate) {
    this.delegate = delegate;
    delegate.addOption(ListChangesOption.LABELS);
    delegate.addOption(ListChangesOption.DETAILED_LABELS);
    delegate.addOption(ListChangesOption.DETAILED_ACCOUNTS);
    delegate.addOption(ListChangesOption.MESSAGES);
    delegate.addOption(ListChangesOption.REVIEWER_UPDATES);
  }

  @Override
  public void setDynamicBean(String plugin, DynamicBean dynamicBean) {
    delegate.setDynamicBean(plugin, dynamicBean);
  }

  @Override
  public Class<? extends DynamicOptions.BeanReceiver> getExportedBeanReceiver() {
    return delegate.getExportedBeanReceiver();
  }

  @Override
  public Response<ChangeInfo> apply(ChangeResource rsrc) throws StorageException {
    return delegate.apply(rsrc);
  }
}
