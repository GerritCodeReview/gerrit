/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {MessageTag} from '../constants/constants';
import {ChangeId, ChangeMessageInfo} from '../types/common';

function getRevertChangeIdFromMessage(msg: ChangeMessageInfo): ChangeId {
  const REVERT_REGEX = /^Created a revert of this change as (.*)$/;
  const changeId = msg.message.match(REVERT_REGEX)?.[1];
  if (!changeId) throw new Error('revert changeId not found');
  return changeId as ChangeId;
}

export function getRevertCreatedChangeIds(messages: ChangeMessageInfo[]) {
  return messages
    .filter(m => m.tag === MessageTag.TAG_REVERT)
    .map(m => getRevertChangeIdFromMessage(m));
}
