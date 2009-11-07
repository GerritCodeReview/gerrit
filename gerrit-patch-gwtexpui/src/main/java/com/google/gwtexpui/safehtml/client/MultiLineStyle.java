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

package com.google.gwtexpui.safehtml.client;


public abstract class MultiLineStyle {
  public MultiLineStyle isStart(String line) {
    return null;
  }

  public boolean isEnd(String line) {
    return false;
  }

  public String restart(String line) {
    return line;
  }

  public String unrestart(String line) {
    return line;
  }

  static class Simple extends MultiLineStyle {
    private final String begin;
    private final String end;

    Simple(String b, String e) {
      begin = b;
      end = e;
    }

    @Override
    public MultiLineStyle isStart(String line) {
      final int lastBegin = line.lastIndexOf(begin);
      if (lastBegin < 0) {
        return null;
      }

      final int lastEnd = line.lastIndexOf(end);
      return lastBegin > lastEnd ? this : null;
    }

    @Override
    public boolean isEnd(String line) {
      final int firstEnd = line.indexOf(end);
      if (firstEnd < 0) {
        return false;
      }

      final int lastBegin = line.lastIndexOf(begin);
      return lastBegin < firstEnd;
    }

    @Override
    public String restart(String line) {
      return begin + "\n" + line;
    }

    @Override
    public String unrestart(String formattedHtml) {
      final int beginPos = formattedHtml.indexOf(begin);
      final String lineBegin = formattedHtml.substring(0, beginPos);
      String lineEnd = formattedHtml.substring(beginPos + begin.length());
      if (lineEnd.startsWith("<br")) {
        lineEnd = lineEnd.substring(lineEnd.indexOf('>') + 1);
      }
      return lineBegin + lineEnd;
    }
  }
}
