/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import {routerChangeNum$} from '../router/router-model';
import {updateState} from './change-model';
import {ParsedChangeInfo} from '../../types/types';

export class ChangeService {
  constructor() {
    // TODO: In the future we will want to make restApiService.getChangeDetail()
    // calls from a switchMap() here. For now just make sure to invalidate the
    // change when no changeNum is set.
    routerChangeNum$.subscribe(changeNum => {
      if (!changeNum) updateState(undefined);
    });
  }

  /**
   * This is a temporary indirection between change-view, which currently
   * manages what the current change is, and the change-model, which will
   * become the source of truth in the future. We will extract a substantial
   * amount of code from change-view and move it into this change-service. This
   * will take some time ...
   */
  updateChange(change: ParsedChangeInfo) {
    updateState(change);
  }
}
