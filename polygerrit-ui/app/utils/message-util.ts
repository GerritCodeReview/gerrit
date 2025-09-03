/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {MessageTag} from '../constants/constants';
import {LABEL_TITLE_SCORE_PATTERN} from '../elements/change/gr-message-scores/gr-message-scores';
import {
  ChangeId,
  ChangeInfo,
  ChangeMessage,
  ChangeMessageInfo,
  PatchSetNum,
} from '../types/common';
import {LabelExtreme, PATCH_SET_PREFIX_PATTERN} from './comment-util';
import {hasOwnProperty} from './common-util';
import {getVotingRange, StandardLabels} from './label-util';

export const VOTE_RESET_TEXT = '0 (vote reset)';

export interface Score {
  label?: string;
  value?: string;
}

function getRevertChangeIdFromMessage(msg: ChangeMessageInfo): ChangeId {
  const REVERT_REGEX =
    /^Created a revert of this change as .*?(I[0-9a-f]{40})$/;
  const changeId = msg.message.match(REVERT_REGEX)?.[1];
  if (!changeId) throw new Error('revert changeId not found');
  return changeId as ChangeId;
}

export function getRevertCreatedChangeIds(messages: ChangeMessageInfo[]) {
  return messages
    .filter(m => m.tag === MessageTag.TAG_REVERT)
    .map(m => getRevertChangeIdFromMessage(m));
}

export function getScores(
  message?: ChangeMessage,
  labelExtremes?: LabelExtreme
): Score[] {
  if (!message || !message.message || !labelExtremes) {
    return [];
  }
  const line = message.message.split('\n', 1)[0];
  const patchSetPrefix = PATCH_SET_PREFIX_PATTERN;
  if (!line.match(patchSetPrefix)) {
    return [];
  }
  const scoresRaw = line.split(patchSetPrefix)[1];
  if (!scoresRaw) {
    return [];
  }
  return scoresRaw
    .split(' ')
    .map(s => s.match(LABEL_TITLE_SCORE_PATTERN))
    .filter(ms => ms && ms.length === 4 && hasOwnProperty(labelExtremes, ms[2]))
    .map(ms => {
      const label = ms?.[2];
      const value = ms?.[1] === '-' ? VOTE_RESET_TEXT : ms?.[3];
      return {label, value};
    });
}

export function getCodeReviewVotesFromMessage(
  change?: ChangeInfo
): Map<PatchSetNum, {label: string; value: number}> {
  const codeReviewVotes = new Map<
    PatchSetNum,
    {label: string; value: number}
  >();
  if (!change?.messages || !change?.labels) {
    return codeReviewVotes;
  }

  const labelExtremes: LabelExtreme = {};
  for (const labelName of Object.keys(change.labels)) {
    const labelInfo = change.labels[labelName];
    const range = getVotingRange(labelInfo);
    if (range) {
      labelExtremes[labelName] = range;
    }
  }

  for (const message of change.messages) {
    if (!message._revision_number) continue;

    const scores = getScores(message as ChangeMessage, labelExtremes);
    for (const score of scores) {
      if (score.label === StandardLabels.CODE_REVIEW && score.value) {
        const value = score.value === VOTE_RESET_TEXT ? 0 : Number(score.value);
        codeReviewVotes.set(message._revision_number, {
          label: score.label,
          value,
        });
      }
    }
  }
  return codeReviewVotes;
}
