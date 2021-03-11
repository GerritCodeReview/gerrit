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

import {StorageObject, StorageService} from './gr-storage';

const defaultStorageObject: StorageObject = {
  message: '',
  updated: 123,
};

export const grStorageMock: StorageService = {
  getDraftComment(): StorageObject | null {
    return defaultStorageObject;
  },

  setDraftComment(): void {},

  eraseDraftComment(): void {},

  getEditableContentItem(): StorageObject | null {
    return defaultStorageObject;
  },

  setEditableContentItem(): void {},

  getRespectfulTipVisibility(): StorageObject | null {
    return defaultStorageObject;
  },

  setRespectfulTipVisibility(): void {},

  eraseEditableContentItem(): void {},
};
