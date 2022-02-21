/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {CommentRange, NumericChangeId, PatchSetNum} from '../../types/common';
import {Finalizable} from '../registry';

export interface StorageLocation {
  changeNum: number;
  patchNum: PatchSetNum | '@change';
  path?: string;
  line?: number;
  range?: CommentRange;
}

export interface StorageObject {
  message?: string;
  updated: number;
}

export interface StorageService extends Finalizable {
  getDraftComment(location: StorageLocation): StorageObject | null;

  setDraftComment(location: StorageLocation, message: string): void;

  eraseDraftComment(location: StorageLocation): void;

  getEditableContentItem(key: string): StorageObject | null;

  setEditableContentItem(key: string, message: string): void;

  eraseEditableContentItem(key: string): void;

  eraseEditableContentItemsForChangeEdit(changeNum?: NumericChangeId): void;
}
