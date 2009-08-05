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

package com.google.gerrit.server.rpc.account;

import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.http.RpcServletModule;
import com.google.gerrit.server.rpc.UiRpcModule;

public class AccountModule extends RpcServletModule {
  public AccountModule() {
    super(UiRpcModule.PREFIX);
  }

  @Override
  protected void configureServlets() {
    install(new FactoryModule() {
      @Override
      protected void configure() {
        factory(AgreementInfoFactory.Factory.class);
        factory(ExternalIdDetailFactory.Factory.class);
      }
    });
    rpc(AccountSecurityImpl.class);
    rpc(AccountServiceImpl.class);
  }
}
