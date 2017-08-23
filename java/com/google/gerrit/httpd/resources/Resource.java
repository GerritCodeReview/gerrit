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

package com.google.gerrit.httpd.resources;

import com.google.gwtexpui.server.CacheHeaders;
import java.io.IOException;
import java.io.Serializable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class Resource implements Serializable {
  private static final long serialVersionUID = 1L;

  public static final Resource NOT_FOUND =
      new Resource() {
        private static final long serialVersionUID = 1L;

        @Override
        public int weigh() {
          return 0;
        }

        @Override
        public void send(HttpServletRequest req, HttpServletResponse res) throws IOException {
          CacheHeaders.setNotCacheable(res);
          res.sendError(HttpServletResponse.SC_NOT_FOUND);
        }

        @Override
        public boolean isUnchanged(long latestModifiedDate) {
          return false;
        }

        protected Object readResolve() {
          return NOT_FOUND;
        }
      };

  public abstract boolean isUnchanged(long latestModifiedDate);

  public abstract int weigh();

  public abstract void send(HttpServletRequest req, HttpServletResponse res) throws IOException;
}
