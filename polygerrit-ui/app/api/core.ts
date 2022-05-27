/**
 * @fileoverview Core API types for Gerrit.
 *
 * Core types are types used in many places in Gerrit, such as the Side enum.
 *
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * The CommentRange entity describes the range of an inline comment.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#comment-range
 *
 * The range includes all characters from the start position, specified by
 * start_line and start_character, to the end position, specified by end_line
 * and end_character. The start position is inclusive and the end position is
 * exclusive.
 *
 * So, a range over part of a line will have start_line equal to end_line;
 * however a range with end_line set to 5 and end_character equal to 0 will not
 * include any characters on line 5.
 */
export declare interface CommentRange {
  /** The start line number of the range. (1-based) */
  start_line: number;

  /** The character position in the start line. (0-based) */
  start_character: number;

  /** The end line number of the range. (1-based) */
  end_line: number;

  /** The character position in the end line. (0-based) */
  end_character: number;
}

/**
 * Return type for cursor moves, that indicate whether a move was possible.
 */
export enum CursorMoveResult {
  /** The cursor was successfully moved. */
  MOVED,
  /** There were no stops - the cursor was reset. */
  NO_STOPS,
  /**
   * There was no more matching stop to move to - the cursor was clipped to the
   * end.
   */
  CLIPPED,
  /** The abort condition would have been fulfilled for the new target. */
  ABORTED,
}

/** A sentinel that can be inserted to disallow moving across. */
export class AbortStop {}

export type Stop = HTMLElement | AbortStop;
