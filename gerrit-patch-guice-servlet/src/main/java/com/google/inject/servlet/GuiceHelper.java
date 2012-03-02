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

package com.google.inject.servlet;

import com.google.inject.servlet.GuiceFilter.Context;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class GuiceHelper {
  public static void runInContext(HttpServletRequest req,
      HttpServletResponse rsp, Runnable thunk) {
    Context previous = GuiceFilter.localContext.get();
    try {
      GuiceFilter.localContext.set(new Context(req, rsp));
      thunk.run();
    } finally {
      GuiceFilter.localContext.set(previous);
    }
  }

  private GuiceHelper() {
  }
}
