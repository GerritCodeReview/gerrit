// Copyright (C) 2020 The Android Open Source Project
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

import * as path from "path";
import * as fs from "fs";

export type FilePath = string & {__filePath: undefined};
export type TypedFilePath<T> = FilePath & { __type?: T, __typedFilePath: undefined };

export enum FileType{
  HtmlSrc,
  HtmlTarget,
  JsSrc,
  JsTarget
}

export type HtmlSrcFilePath = TypedFilePath<FileType.HtmlSrc>;
export type HtmlTargetFilePath = TypedFilePath<FileType.HtmlTarget>;
export type JsSrcFilePath = TypedFilePath<FileType.JsSrc>;
export type JsTargetFilePath = TypedFilePath<FileType.JsTarget>;

export class FileUtils {
  public static ensureDirExistsForFile(filePath: string) {
    const dirName = path.dirname(filePath);
    if (!fs.existsSync(dirName)) {
      fs.mkdirSync(dirName, {recursive: true, mode: 0o744});
    }
  }

  public static writeContent(file: string, content: string) {
    if(fs.existsSync(file) && fs.lstatSync(file).isSymbolicLink()) {
      throw new Error(`Output file '${file}' is a symbolic link. Inplace update for links are not supported.`);
    }
    FileUtils.ensureDirExistsForFile(file);
    fs.writeFileSync(file, content);
  }

  public static copyFile(src: string, dst: string) {
    FileUtils.ensureDirExistsForFile(dst);
    fs.copyFileSync(src, dst);
  }
}
