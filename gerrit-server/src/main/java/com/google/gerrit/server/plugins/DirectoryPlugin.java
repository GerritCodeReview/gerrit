//// Copyright (C) 2014 The Android Open Source Project
////
//// Licensed under the Apache License, Version 2.0 (the "License");
//// you may not use this file except in compliance with the License.
//// You may obtain a copy of the License at
////
//// http://www.apache.org/licenses/LICENSE-2.0
////
//// Unless required by applicable law or agreed to in writing, software
//// distributed under the License is distributed on an "AS IS" BASIS,
//// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//// See the License for the specific language governing permissions and
//// limitations under the License.
//
//package com.google.gerrit.server.plugins;
//
//import static com.google.common.base.Objects.firstNonNull;
//import static com.google.common.base.Strings.emptyToNull;
//
//import com.google.common.base.Objects;
//import com.google.common.base.Strings;
//import com.google.gerrit.server.PluginUser;
//
//import org.eclipse.jgit.internal.storage.file.FileSnapshot;
//
//import java.io.File;
//
//class DirectoryPlugin extends ScriptingPlugin {
//
//  DirectoryPlugin(String name, File srcFile, PluginUser pluginUser,
//      FileSnapshot snapshot) {
//    super(name, srcFile, pluginUser, snapshot);
//  }
//
//  @Override
//  public File getSrcFile() {
//    File initJs = new File(super.getSrcFile(), "static/init.js");
//    if (initJs.exists()) {
//      return initJs;
//    }
//    return super.getSrcFile();
//  }
//
//  @Override
//  public String getVersion() {
//    return firstNonNull(emptyToNull(super.getVersion()), "DEV");
//  }
//}
