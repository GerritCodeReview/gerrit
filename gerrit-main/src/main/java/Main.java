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


public final class Main {
  // We don't do any real work here because we need to import
  // the archive lookup code and we cannot import a class in
  // the default package.  So this is just a tiny springboard
  // to jump into the real main code.
  //

  public static void main(final String argv[]) throws Exception {
    com.google.gerrit.main.GerritLauncher.main(argv);
  }

  private Main() {
  }
}
