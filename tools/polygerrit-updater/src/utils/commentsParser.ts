// Copyright (C) 2019 The Android Open Source Project
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

enum CommentScannerState {
  Text,
  SingleLineComment,
  MultLineComment
}
export class CommentsParser {
  public static collectAllComments(text: string): string[] {
    const result: string[] = [];
    let state = CommentScannerState.Text;
    let pos = 0;
    function readSingleLineComment() {
      const startPos = pos;
      while(pos < text.length && text[pos] !== '\n') {
        pos++;
      }
      return text.substring(startPos, pos);
    }
    function readMultiLineComment() {
      const startPos = pos;
      while(pos < text.length) {
        if(pos < text.length - 1 && text[pos] === '*' && text[pos + 1] === '/') {
          pos += 2;
          break;
        }
        pos++;
      }
      return text.substring(startPos, pos);
    }

    function skipString(lastChar: string) {
      pos++;
      while(pos < text.length) {
        if(text[pos] === lastChar) {
          pos++;
          return;
        } else if(text[pos] === '\\') {
          pos+=2;
          continue;
        }
        pos++;
      }
    }


    while(pos < text.length - 1) {
      if(text[pos] === '/' && text[pos + 1] === '/') {
        result.push(readSingleLineComment());
      } else if(text[pos] === '/' && text[pos + 1] === '*') {
        result.push(readMultiLineComment());
      } else if(text[pos] === "'") {
        skipString("'");
      } else if(text[pos] === '"') {
        skipString('"');
      } else if(text[pos] === '`') {
        skipString('`');
      } else if(text[pos] == '/') {
        skipString('/');
      } {
        pos++;
      }

    }
    return result;
  }
}
