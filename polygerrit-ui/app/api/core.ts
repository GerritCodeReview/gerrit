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
