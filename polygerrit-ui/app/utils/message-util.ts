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

import {REVERT_TAG} from '../constants/constants';
import {ChangeInfo} from '../types/common';
import {ParsedChangeInfo} from '../types/types';

export function getRevertCommitHash(change: ChangeInfo | ParsedChangeInfo) {
  const msg = change.messages?.find(m => m.tag === REVERT_TAG);
  if (!msg) throw new Error('revert message not found');
  const REVERT_REGEX = /^Created a revert of this change as (.*)$/;
  const commit = msg.message.match(REVERT_REGEX)?.[1];
  if (!commit) throw new Error('revert commit not found');
  return {
    commit,
  };
}
