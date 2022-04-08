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
import {
  CommentBasics,
  CommentInfo,
  PatchSetNum,
  RobotCommentInfo,
  Timestamp,
  UrlEncodedCommentId,
  CommentRange,
  PatchRange,
  ParentPatchSetNum,
  ContextLine,
  BasePatchSetNum,
  RevisionPatchSetNum,
  AccountInfo,
  AccountDetailInfo,
  ChangeMessageInfo,
  VotingRangeInfo,
} from '../types/common';
import {CommentSide, SpecialFilePath} from '../constants/constants';
import {parseDate} from './date-util';
import {CommentIdToCommentThreadMap} from '../elements/diff/gr-comment-api/gr-comment-api';
import {isMergeParent, getParentIndex} from './patch-set-util';
import {DiffInfo} from '../types/diff';
import {LineNumber} from '../api/diff';
import {FormattedReviewerUpdateInfo} from '../types/types';

export interface DraftCommentProps {
  // This must be true for all drafts. Drafts received from the backend will be
  // modified immediately with __draft:true before allowing them to get into
  // the application state.
  __draft: boolean;
}

export interface UnsavedCommentProps {
  // This must be true for all unsaved comment drafts. An unsaved draft is
  // always just local to a comment component like <gr-comment> or
  // <gr-comment-thread>. Unsaved drafts will never appear in the application
  // state.
  __unsaved: boolean;
}

export type DraftInfo = CommentInfo & DraftCommentProps;

export type UnsavedInfo = CommentBasics & UnsavedCommentProps;

export type Comment = UnsavedInfo | DraftInfo | CommentInfo | RobotCommentInfo;

export type CommentMap = {[path: string]: boolean};

export function isRobot<T extends CommentBasics>(
  x: T | DraftInfo | RobotCommentInfo | undefined
): x is RobotCommentInfo {
  return !!x && !!(x as RobotCommentInfo).robot_id;
}

export function isDraft<T extends CommentBasics>(
  x: T | DraftInfo | undefined
): x is DraftInfo {
  return !!x && !!(x as DraftInfo).__draft;
}

export function isUnsaved<T extends CommentBasics>(
  x: T | UnsavedInfo | undefined
): x is UnsavedInfo {
  return !!x && !!(x as UnsavedInfo).__unsaved;
}

export function isDraftOrUnsaved<T extends CommentBasics>(
  x: T | DraftInfo | UnsavedInfo | undefined
): x is UnsavedInfo | DraftInfo {
  return isDraft(x) || isUnsaved(x);
}

interface SortableComment {
  updated: Timestamp;
  id: UrlEncodedCommentId;
}

export interface ChangeMessage extends ChangeMessageInfo {
  // TODO(TS): maybe should be an enum instead
  type: string;
  expanded: boolean;
  commentThreads: CommentThread[];
}

export function isFormattedReviewerUpdate(
  message: ChangeMessage
): message is ChangeMessage & FormattedReviewerUpdateInfo {
  return message.type === 'REVIEWER_UPDATE';
}

export type LabelExtreme = {[labelName: string]: VotingRangeInfo};

export const PATCH_SET_PREFIX_PATTERN =
  /^(?:Uploaded\s*)?[Pp]atch [Ss]et \d+:\s*(.*)/;

export function sortComments<T extends SortableComment>(comments: T[]): T[] {
  return comments.slice(0).sort((c1, c2) => {
    const d1 = isDraft(c1);
    const d2 = isDraft(c2);
    if (d1 !== d2) return d1 ? 1 : -1;

    const date1 = parseDate(c1.updated);
    const date2 = parseDate(c2.updated);
    const dateDiff = date1.valueOf() - date2.valueOf();
    if (dateDiff !== 0) return dateDiff;

    const id1 = c1.id;
    const id2 = c2.id;
    return id1.localeCompare(id2);
  });
}

export function createUnsavedComment(thread: CommentThread): UnsavedInfo {
  return {
    path: thread.path,
    patch_set: thread.patchNum,
    side: thread.commentSide ?? CommentSide.REVISION,
    line: typeof thread.line === 'number' ? thread.line : undefined,
    range: thread.range,
    parent: thread.mergeParentNum,
    message: '',
    unresolved: true,
    __unsaved: true,
  };
}

export function createUnsavedReply(
  replyingTo: CommentInfo,
  message: string,
  unresolved: boolean
): UnsavedInfo {
  return {
    path: replyingTo.path,
    patch_set: replyingTo.patch_set,
    side: replyingTo.side,
    line: replyingTo.line,
    range: replyingTo.range,
    parent: replyingTo.parent,
    in_reply_to: replyingTo.id,
    message,
    unresolved,
    __unsaved: true,
  };
}

export function createCommentThreads(comments: CommentInfo[]) {
  const sortedComments = sortComments(comments);
  const threads: CommentThread[] = [];
  const idThreadMap: CommentIdToCommentThreadMap = {};
  for (const comment of sortedComments) {
    // thread and append to it.
    if (comment.in_reply_to) {
      const thread = idThreadMap[comment.in_reply_to];
      if (thread) {
        thread.comments.push(comment);
        if (comment.id) idThreadMap[comment.id] = thread;
        continue;
      }
    }

    // Otherwise, this comment starts its own thread.
    if (!comment.path) {
      throw new Error('Comment missing required "path".');
    }
    const newThread: CommentThread = {
      comments: [comment],
      patchNum: comment.patch_set,
      commentSide: comment.side ?? CommentSide.REVISION,
      mergeParentNum: comment.parent,
      path: comment.path,
      line: comment.line,
      range: comment.range,
      rootId: comment.id,
    };
    if (!comment.line && !comment.range) {
      newThread.line = 'FILE';
    }
    threads.push(newThread);
    if (comment.id) idThreadMap[comment.id] = newThread;
  }
  return threads;
}

export interface CommentThread {
  /**
   * This can only contain at most one draft. And if so, then it is the last
   * comment in this list. This must not contain unsaved drafts.
   */
  comments: Array<CommentInfo | DraftInfo | RobotCommentInfo>;
  /**
   * Identical to the id of the first comment. If this is undefined, then the
   * thread only contains an unsaved draft.
   */
  rootId?: UrlEncodedCommentId;
  /**
   * Note that all location information is typically identical to that of the
   * first comment, but not for ported comments!
   */
  path: string;
  commentSide: CommentSide;
  /* mergeParentNum is the merge parent number only valid for merge commits
     when commentSide is PARENT.
     mergeParentNum is undefined for auto merge commits
     Same as `parent` in CommentInfo.
  */
  mergeParentNum?: number;
  patchNum?: PatchSetNum;
  /* Different from CommentInfo, which just keeps the line undefined for
     FILE comments. */
  line?: LineNumber;
  range?: CommentRange;
  /**
   * Was the thread ported over from its original location to a newer patchset?
   * If yes, then the location information above contains the ported location,
   * but the comments still have the original location set.
   */
  ported?: boolean;
  /**
   * Only relevant when ported:true. Means that no ported range could be
   * computed. `line` and `range` can be undefined then.
   */
  rangeInfoLost?: boolean;
}

export function equalLocation(t1?: CommentThread, t2?: CommentThread) {
  if (t1 === t2) return true;
  if (t1 === undefined || t2 === undefined) return false;
  return (
    t1.path === t2.path &&
    t1.patchNum === t2.patchNum &&
    t1.commentSide === t2.commentSide &&
    t1.line === t2.line &&
    t1.range?.start_line === t2.range?.start_line &&
    t1.range?.start_character === t2.range?.start_character &&
    t1.range?.end_line === t2.range?.end_line &&
    t1.range?.end_character === t2.range?.end_character
  );
}

export function getLastComment(thread: CommentThread): CommentInfo | undefined {
  const len = thread.comments.length;
  return thread.comments[len - 1];
}

export function getLastPublishedComment(
  thread: CommentThread
): CommentInfo | undefined {
  const publishedComments = thread.comments.filter(c => !isDraftOrUnsaved(c));
  const len = publishedComments.length;
  return publishedComments[len - 1];
}

export function getFirstComment(
  thread: CommentThread
): CommentInfo | undefined {
  return thread.comments[0];
}

export function countComments(thread: CommentThread) {
  return thread.comments.length;
}

export function isPatchsetLevel(thread: CommentThread): boolean {
  return thread.path === SpecialFilePath.PATCHSET_LEVEL_COMMENTS;
}

export function isUnresolved(thread: CommentThread): boolean {
  return !isResolved(thread);
}

export function isResolved(thread: CommentThread): boolean {
  const lastUnresolved = getLastComment(thread)?.unresolved;
  return !lastUnresolved ?? false;
}

export function isDraftThread(thread: CommentThread): boolean {
  return isDraft(getLastComment(thread));
}

export function isRobotThread(thread: CommentThread): boolean {
  return isRobot(getFirstComment(thread));
}

export function hasHumanReply(thread: CommentThread): boolean {
  return countComments(thread) > 1 && !isRobot(getLastComment(thread));
}

export function lastUpdated(thread: CommentThread): Date | undefined {
  // We don't want to re-sort comments when you save a draft reply, so
  // we stick to the timestampe of the last *published* comment.
  const lastUpdated =
    getLastPublishedComment(thread)?.updated ?? getLastComment(thread)?.updated;
  return lastUpdated !== undefined ? parseDate(lastUpdated) : undefined;
}
/**
 * Whether the given comment should be included in the base side of the
 * given patch range.
 */
export function isInBaseOfPatchRange(
  comment: {
    patch_set?: PatchSetNum;
    side?: CommentSide;
    parent?: number;
  },
  range: PatchRange
) {
  // If the base of the patch range is a parent of a merge, and the comment
  // appears on a specific parent then only show the comment if the parent
  // index of the comment matches that of the range.
  if (comment.parent && comment.side === CommentSide.PARENT) {
    return (
      isMergeParent(range.basePatchNum) &&
      comment.parent === getParentIndex(range.basePatchNum)
    );
  }

  // If the base of the range is the parent of the patch:
  if (
    range.basePatchNum === ParentPatchSetNum &&
    comment.side === CommentSide.PARENT &&
    comment.patch_set === range.patchNum
  ) {
    return true;
  }
  // If the base of the range is not the parent of the patch:
  return (
    range.basePatchNum !== ParentPatchSetNum &&
    comment.side !== CommentSide.PARENT &&
    comment.patch_set === range.basePatchNum
  );
}

/**
 * Whether the given comment should be included in the revision side of the
 * given patch range.
 */
export function isInRevisionOfPatchRange(
  comment: {
    patch_set?: PatchSetNum;
    side?: CommentSide;
  },
  range: PatchRange
) {
  return (
    comment.side !== CommentSide.PARENT && comment.patch_set === range.patchNum
  );
}

/**
 * Whether the given comment should be included in the given patch range.
 */
export function isInPatchRange(
  comment: CommentBasics,
  range: PatchRange
): boolean {
  return (
    isInBaseOfPatchRange(comment, range) ||
    isInRevisionOfPatchRange(comment, range)
  );
}

export function getPatchRangeForCommentUrl(
  comment: Comment,
  latestPatchNum: RevisionPatchSetNum
) {
  if (!comment.patch_set) throw new Error('Missing comment.patch_set');

  // TODO(dhruvsri): Add handling for comment left on parents of merge commits
  if (comment.side === CommentSide.PARENT) {
    if (comment.patch_set === ParentPatchSetNum)
      throw new Error('comment.patch_set cannot be PARENT');
    return {
      patchNum: comment.patch_set as RevisionPatchSetNum,
      basePatchNum: ParentPatchSetNum,
    };
  } else if (latestPatchNum === comment.patch_set) {
    return {
      patchNum: latestPatchNum,
      basePatchNum: ParentPatchSetNum,
    };
  } else {
    return {
      patchNum: latestPatchNum,
      basePatchNum: comment.patch_set as BasePatchSetNum,
    };
  }
}

export function computeDiffFromContext(
  context: ContextLine[],
  path: string,
  content_type?: string
) {
  // do not render more than 20 lines of context
  context = context.slice(0, 20);
  const diff: DiffInfo = {
    meta_a: {
      name: '',
      content_type: '',
      lines: 0,
      web_links: [],
    },
    meta_b: {
      name: path,
      content_type: content_type || '',
      lines: context.length + context?.[0].line_number,
      web_links: [],
    },
    change_type: 'MODIFIED',
    intraline_status: 'OK',
    diff_header: [],
    content: [
      {
        skip: context[0].line_number - 1,
      },
      {
        b: context.map(line => line.context_line),
      },
    ],
  };
  return diff;
}

export function getCommentAuthors(
  threads?: CommentThread[],
  user?: AccountDetailInfo
) {
  if (!threads || !user) return [];
  const ids = new Set();
  const authors: AccountInfo[] = [];
  threads.forEach(t =>
    t.comments.forEach(c => {
      if (isDraft(c) && !ids.has(user._account_id)) {
        ids.add(user._account_id);
        authors.push(user);
        return;
      }
      if (c.author && !ids.has(c.author._account_id)) {
        ids.add(c.author._account_id);
        authors.push(c.author);
      }
    })
  );
  return authors;
}

/**
 * Add path info to every comment as CommentInfo returned from server does not
 * have that.
 */
export function addPath<T>(comments: {[path: string]: T[]} = {}): {
  [path: string]: Array<T & {path: string}>;
} {
  const updatedComments: {[path: string]: Array<T & {path: string}>} = {};
  for (const filePath of Object.keys(comments)) {
    updatedComments[filePath] = (comments[filePath] || []).map(comment => {
      return {...comment, path: filePath};
    });
  }
  return updatedComments;
}

/**
 * Add __draft:true to all drafts returned from server so that they can be told
 * apart from published comments easily.
 */
export function addDraftProp(
  draftsByPath: {[path: string]: CommentInfo[]} = {}
) {
  const updated: {[path: string]: DraftInfo[]} = {};
  for (const filePath of Object.keys(draftsByPath)) {
    updated[filePath] = (draftsByPath[filePath] ?? []).map(draft => {
      return {...draft, __draft: true};
    });
  }
  return updated;
}

export function reportingDetails(comment: CommentBasics) {
  return {
    id: comment?.id,
    message_length: comment?.message?.trim().length,
    in_reply_to: comment?.in_reply_to,
    unresolved: comment?.unresolved,
    path_length: comment?.path?.length,
    line: comment?.range?.start_line ?? comment?.line,
    unsaved: isUnsaved(comment),
  };
}
