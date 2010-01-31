// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.prettify.server;

import com.google.gerrit.prettify.common.PrettyFactory;
import com.google.gerrit.prettify.common.PrettyFormatter;
import com.google.inject.AbstractModule;

public class PrettifyModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(ServerPrettyFactory.class);
    bind(PrettyFactory.class).to(ServerPrettyFactory.class);
    bind(PrettyFormatter.class).toProvider(ServerPrettyFactory.class);
  }
}
