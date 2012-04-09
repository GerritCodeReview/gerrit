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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.Gerrit;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtexpui.safehtml.client.RegexFindReplace;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtjsonrpc.common.VoidResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommentLinkProcessor {
  public static SafeHtml apply(SafeHtml buf) {
    try {
      return buf.replaceAll(Gerrit.getConfig().getCommentLinks());

    } catch (RuntimeException err) {
      // One or more of the patterns isn't valid on this browser.
      // Try to filter the list down and remove the invalid ones.

      List<RegexFindReplace> safe = new ArrayList<RegexFindReplace>();
      List<PatternError> bad = new ArrayList<PatternError>();
      for (RegexFindReplace r : Gerrit.getConfig().getCommentLinks()) {
        try {
          buf.replaceAll(Collections.singletonList(r));
          safe.add(r);
        } catch (RuntimeException why) {
          bad.add(new PatternError(r, why.getMessage()));
        }
      }

      if (!bad.isEmpty()) {
        StringBuilder msg = new StringBuilder();
        msg.append("Invalid commentlink pattern(s):");
        for (PatternError e : bad) {
          msg.append("\n");
          msg.append("\"");
          msg.append(e.pattern.find());
          msg.append("\": ");
          msg.append(e.errorMessage);
        }
        Gerrit.SYSTEM_SVC.clientError(msg.toString(),
            new AsyncCallback<VoidResult>() {
              @Override
              public void onFailure(Throwable caught) {
              }

              @Override
              public void onSuccess(VoidResult result) {
              }
            });
      }

      try {
        Gerrit.getConfig().setCommentLinks(safe);
        return buf.replaceAll(safe);
      } catch (RuntimeException err2) {
        // To heck with it. The patterns passed individually above but
        // failed as a group? Just drop them all and render without.
        //
        Gerrit.getConfig().setCommentLinks(null);
        return buf;
      }
    }
  }

  private static class PatternError {
    RegexFindReplace pattern;
    String errorMessage;

    PatternError(RegexFindReplace r, String w) {
      pattern = r;
      errorMessage = w;
    }
  }

  private CommentLinkProcessor() {
  }
}
