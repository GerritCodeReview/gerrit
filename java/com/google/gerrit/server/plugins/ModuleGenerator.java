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

package com.google.gerrit.server.plugins;

import com.google.gerrit.extensions.annotations.Export;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;

public interface ModuleGenerator {
  void setPluginName(String name);

  void export(Export export, Class<?> type) throws InvalidPluginException;

  void listen(TypeLiteral<?> tl, Class<?> clazz);

  Module create() throws InvalidPluginException;

  class NOP implements ModuleGenerator {

    @Override
    public void setPluginName(String name) {
      // do nothing
    }

    @Override
    public void listen(TypeLiteral<?> tl, Class<?> clazz) {
      // do nothing
    }

    @Override
    public void export(Export export, Class<?> type) {
      // do nothing
    }

    @Override
    public Module create() throws InvalidPluginException {
      return null;
    }
  }
}
