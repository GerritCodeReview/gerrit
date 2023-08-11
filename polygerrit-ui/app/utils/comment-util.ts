/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  CommentInfo,
  PatchSetNum,
  UrlEncodedCommentId,
  PatchRange,
  PARENT,
  ContextLine,
  BasePatchSetNum,
  RevisionPatchSetNum,
  AccountInfo,
  AccountDetailInfo,
  VotingRangeInfo,
  FixSuggestionInfo,
  FixId,
  PatchSetNumber,
  CommentThread,
  DraftInfo,
  ChangeMessage,
  isRobot,
  isDraft,
  Comment,
  CommentIdToCommentThreadMap,
  SavingState,
  NewDraftInfo,
  isNew,
  CommentInput,
} from '../types/common';
import {CommentSide, SpecialFilePath} from '../constants/constants';
import {parseDate} from './date-util';
import {isMergeParent, getParentIndex} from './patch-set-util';
import {DiffInfo} from '../types/diff';
import {FormattedReviewerUpdateInfo} from '../types/types';
import {extractMentionedUsers} from './account-util';
import {assertIsDefined, uuid} from './common-util';
import {FILE} from '../api/diff';

export function isFormattedReviewerUpdate(
  message: ChangeMessage
): message is ChangeMessage & FormattedReviewerUpdateInfo {
  return message.type === 'REVIEWER_UPDATE';
}

export type LabelExtreme = {[labelName: string]: VotingRangeInfo};

export const NEWLINE_PATTERN = /\n/g;

export const PATCH_SET_PREFIX_PATTERN =
  /^(?:Uploaded\s*)?[Pp]atch [Ss]et \d+:\s*(.*)/;

/**
 * We need a way to uniquely identify drafts. That is easy for all drafts that
 * were already known to the backend at the time of change page load: They will
 * have an `id` that we can use.
 *
 * For newly created drafts we start by setting a `client_id`, so that we can
 * identify the draft even, if no `id` is available yet.
 *
 * If a comment with a `client_id` gets saved, then id gets an `id`, but we have
 * to keep using the `client_id`, because that is what the UI is already using,
 * e.g. in `repeat()` directives.
 */
export function id(comment: Comment): UrlEncodedCommentId {
  if (isDraft(comment)) {
    if (isNew(comment)) {
      assertIsDefined(comment.client_id);
      return comment.client_id;
    }
    if (comment.client_id) {
      return comment.client_id;
    }
  }
  assertIsDefined(comment.id);
  return comment.id;
}

export function sortComments<T extends Comment>(comments: T[]): T[] {
  return comments.slice(0).sort((c1, c2) => {
    const n1 = isNew(c1);
    const n2 = isNew(c2);
    if (n1 !== n2) return n1 ? 1 : -1;

    const d1 = isDraft(c1);
    const d2 = isDraft(c2);
    if (d1 !== d2) return d1 ? 1 : -1;

    if (c1.updated && c2.updated) {
      const date1 = parseDate(c1.updated);
      const date2 = parseDate(c2.updated);
      const dateDiff = date1.valueOf() - date2.valueOf();
      if (dateDiff !== 0) return dateDiff;
    }

    const id1 = id(c1);
    const id2 = id(c2);
    return id1.localeCompare(id2);
  });
}

export function createNew(
  message?: string,
  unresolved?: boolean
): NewDraftInfo {
  const newDraft: NewDraftInfo = {
    savingState: SavingState.OK,
    client_id: uuid() as UrlEncodedCommentId,
    id: undefined,
    updated: undefined,
  };
  if (message !== undefined) newDraft.message = message;
  if (unresolved !== undefined) newDraft.unresolved = unresolved;
  return newDraft;
}

export function createNewPatchsetLevel(
  patchNum?: PatchSetNumber,
  message?: string,
  unresolved?: boolean
): DraftInfo {
  return {
    ...createNew(message, unresolved),
    patch_set: patchNum,
    path: SpecialFilePath.PATCHSET_LEVEL_COMMENTS,
  };
}

export function createNewReply(
  replyingTo: CommentInfo,
  message: string,
  unresolved: boolean
): DraftInfo {
  return {
    ...createNew(message, unresolved),
    path: replyingTo.path,
    patch_set: replyingTo.patch_set,
    side: replyingTo.side,
    line: replyingTo.line,
    range: replyingTo.range,
    parent: replyingTo.parent,
    in_reply_to: replyingTo.id,
  };
}

export function createCommentThreads(comments: Comment[]) {
  const sortedComments = sortComments(comments);
  const threads: CommentThread[] = [];
  const idThreadMap: CommentIdToCommentThreadMap = {};
  for (const comment of sortedComments) {
    // thread and append to it.
    if (comment.in_reply_to) {
      const thread = idThreadMap[comment.in_reply_to];
      if (thread) {
        thread.comments.push(comment);
        if (id(comment)) idThreadMap[id(comment)] = thread;
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
      rootId: id(comment),
    };
    if (!comment.line && !comment.range) {
      newThread.line = FILE;
    }
    threads.push(newThread);
    if (id(comment)) idThreadMap[id(comment)] = newThread;
  }
  return threads;
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

export function getLastComment(
  thread: CommentThread
): CommentInfo | DraftInfo | undefined {
  const len = thread.comments.length;
  return thread.comments[len - 1];
}

export function getLastPublishedComment(
  thread: CommentThread
): CommentInfo | DraftInfo | undefined {
  const publishedComments = thread.comments.filter(c => !isDraft(c));
  const len = publishedComments.length;
  return publishedComments[len - 1];
}

export function getFirstComment(
  thread: CommentThread
): CommentInfo | DraftInfo | undefined {
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

/**
 * Returns true, if the thread consists only of one comment that has not yet
 * been saved to the backend.
 */
export function isNewThread(thread: CommentThread): boolean {
  return isNew(getFirstComment(thread));
}

export function isMentionedThread(
  thread: CommentThread,
  account?: AccountInfo
) {
  if (!account?.email) return false;
  return getMentionedUsers(thread)
    .map(v => v.email)
    .includes(account.email);
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
    range.basePatchNum === PARENT &&
    comment.side === CommentSide.PARENT &&
    comment.patch_set === range.patchNum
  ) {
    return true;
  }
  // If the base of the range is not the parent of the patch:
  return (
    range.basePatchNum !== PARENT &&
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
export function isInPatchRange(comment: Comment, range: PatchRange): boolean {
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
    return {
      patchNum: comment.patch_set,
      basePatchNum: PARENT,
    };
  } else if (latestPatchNum === comment.patch_set) {
    return {
      patchNum: latestPatchNum,
      basePatchNum: PARENT,
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
 * Add `savingState: SavingState.OK` to all drafts returned from server so that
 * they can be told apart from published comments easily.
 */
export function addDraftProp(
  draftsByPath: {[path: string]: CommentInfo[]} = {}
) {
  const updated: {[path: string]: DraftInfo[]} = {};
  for (const filePath of Object.keys(draftsByPath)) {
    updated[filePath] = (draftsByPath[filePath] ?? []).map(draft => {
      return {...draft, savingState: SavingState.OK};
    });
  }
  return updated;
}

export function reportingDetails(comment: Comment) {
  return {
    id: comment?.id,
    message_length: comment?.message?.trim().length,
    in_reply_to: comment?.in_reply_to,
    unresolved: comment?.unresolved,
    path_length: comment?.path?.length,
    line: comment?.range?.start_line ?? comment?.line,
    unsaved: isNew(comment),
  };
}

export const USER_SUGGESTION_INFO_STRING = 'suggestion';
export const USER_SUGGESTION_START_PATTERN = `\`\`\`${USER_SUGGESTION_INFO_STRING}\n`;

// This can either mean a user or a checks provided fix.
// "Provided" means that the fix is sent along with the request
// when previewing and applying the fix. This is in contrast to
// robot comment fixes, which are stored in the backend, and they
// are referenced by a unique `FixId`;
export const PROVIDED_FIX_ID = 'provided_fix' as FixId;

export function hasUserSuggestion(comment: Comment) {
  return comment.message?.includes(USER_SUGGESTION_START_PATTERN) ?? false;
}

export function getUserSuggestionFromString(content: string) {
  const start =
    content.indexOf(USER_SUGGESTION_START_PATTERN) +
    USER_SUGGESTION_START_PATTERN.length;
  const end = content.indexOf('\n```', start);
  return content.substring(start, end);
}

export function getUserSuggestion(comment: Comment) {
  if (!comment.message) return;
  return getUserSuggestionFromString(comment.message);
}

export function getContentInCommentRange(
  fileContent: string,
  comment: Comment
) {
  const lines = fileContent.split('\n');
  if (comment.range) {
    const range = comment.range;
    return lines.slice(range.start_line - 1, range.end_line).join('\n');
  }
  return lines[comment.line! - 1];
}

export function createUserFixSuggestion(
  comment: Comment,
  line: string,
  replacement: string
): FixSuggestionInfo[] {
  const lastLine = line.split('\n').pop();
  return [
    {
      fix_id: PROVIDED_FIX_ID,
      description: 'User suggestion',
      replacements: [
        {
          path: comment.path!,
          range: {
            start_line: comment.range?.start_line ?? comment.line!,
            start_character: 0,
            end_line: comment.range?.end_line ?? comment.line!,
            end_character: lastLine!.length,
          },
          replacement,
        },
      ],
    },
  ];
}

function getMentionedUsers(thread: CommentThread) {
  return thread.comments.map(c => extractMentionedUsers(c.message)).flat();
}

export function getMentionedThreads(
  threads: CommentThread[],
  account: AccountInfo
) {
  if (!account.email) return [];
  return threads.filter(t =>
    getMentionedUsers(t)
      .map(v => v.email)
      .includes(account.email)
  );
}

export function findComment(
  comments: {
    [path: string]: (CommentInfo | DraftInfo)[];
  },
  commentId: UrlEncodedCommentId
) {
  if (!commentId) return undefined;
  let comment;
  for (const path of Object.keys(comments)) {
    comment = comment || comments[path].find(c => c.id === commentId);
  }
  return comment;
}

export function convertToCommentInput(comment: Comment): CommentInput {
  const output: CommentInput = {
    message: comment.message,
    unresolved: comment.unresolved,
  };

  if (comment.id) {
    output.id = comment.id;
  }
  if (comment.path) {
    output.path = comment.path;
  }
  if (comment.side) {
    output.side = comment.side;
  }
  if (comment.line) {
    output.line = comment.line;
  }
  if (comment.range) {
    output.range = comment.range;
  }
  if (comment.in_reply_to) {
    output.in_reply_to = comment.in_reply_to;
  }
  if (comment.updated) {
    output.updated = comment.updated;
  }
  if (comment.tag) {
    output.tag = comment.tag;
  }
  return output;
}
