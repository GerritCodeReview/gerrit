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

import {ChangeStatus, MessageTag} from '../constants/constants';
import {RestApiService} from '../services/gr-rest-api/gr-rest-api';
import {ChangeMessageInfo} from '../types/common';
import {ParsedChangeInfo} from '../types/types';

function getCommitFromMessage(msg: ChangeMessageInfo) {
  const REVERT_REGEX = /^Created a revert of this change as (.*)$/;
  const commit = msg.message.match(REVERT_REGEX)?.[1];
  if (!commit) throw new Error('revert commit not found');
  return commit;
}

export function getRevertCommitHash(messages?: ChangeMessageInfo[]) {
  const msg = messages?.find(m => m.tag === MessageTag.TAG_REVERT);
  if (!msg) return undefined;
  return getCommitFromMessage(msg);
}

export function isRevertCreated(messages?: ChangeMessageInfo[]) {
  return messages?.some(m => m.tag === MessageTag.TAG_REVERT);
}

export function isRevertSubmitted(
  messages: ChangeMessageInfo[],
  restAPI: RestApiService
) {
  const revertMessages = messages?.filter(m => m.tag === MessageTag.TAG_REVERT);
  const promises: Promise<ParsedChangeInfo | undefined | null>[] = [];
  revertMessages.forEach(revertMessage => {
    const commit = getCommitFromMessage(revertMessage);
    promises.push(restAPI.getChangeDetail(commit));
  });
  return Promise.all(promises).then(changes => {
    return changes.some(change => change?.status === ChangeStatus.MERGED);
  });
}
