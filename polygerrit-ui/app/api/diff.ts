/**
 * @fileoverview The API of Gerrit's diff viewer, gr-diff.
 *
 * This includes some types which are also defined as part of Gerrit's JSON API
 * which are used as inputs to gr-diff.
 *
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

import {CommentRange} from './core';

/**
 * Diff type in preferences
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#preferences-input
 */
export enum DiffViewMode {
  SIDE_BY_SIDE = 'SIDE_BY_SIDE',
  UNIFIED = 'UNIFIED_DIFF',
}

/**
 * The DiffInfo entity contains information about the diff of a file in a
 * revision.
 *
 * If the weblinks-only parameter is specified, only the web_links field is set.
 */
export declare interface DiffInfo {
  /** Meta information about the file on side A as a DiffFileMetaInfo entity. */
  meta_a: DiffFileMetaInfo;
  /** Meta information about the file on side B as a DiffFileMetaInfo entity. */
  meta_b: DiffFileMetaInfo;
  /** The type of change (ADDED, MODIFIED, DELETED, RENAMED COPIED, REWRITE). */
  change_type: ChangeType;
  /** Intraline status (OK, ERROR, TIMEOUT). */
  intraline_status: 'OK' | 'Error' | 'Timeout';
  /** The content differences in the file as a list of DiffContent entities. */
  content: DiffContent[];
  /** Whether the file is binary. */
  binary?: boolean;
}

/**
 * The DiffFileMetaInfo entity contains meta information about a file diff.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#diff-file-meta-info
 */
export declare interface DiffFileMetaInfo {
  /** The name of the file. */
  name: string;
  /** The content type of the file. */
  content_type: string;
  /** The total number of lines in the file. */
  lines: number;
  // TODO: Not documented.
  language?: string;
}

export declare type ChangeType =
  | 'ADDED'
  | 'MODIFIED'
  | 'DELETED'
  | 'RENAMED'
  | 'COPIED'
  | 'REWRITE';

/**
 * The DiffContent entity contains information about the content differences in
 * a file.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#diff-content
 */
export declare interface DiffContent {
  /** Content only in the file on side A (deleted in B). */
  a?: string[];
  /** Content only in the file on side B (added in B). */
  b?: string[];
  /** Content in the file on both sides (unchanged). */
  ab?: string[];
  /**
   * Text sections deleted from side A as a DiffIntralineInfo entity.
   *
   * Only present during a replace, i.e. both a and b are present.
   */
  edit_a?: DiffIntralineInfo[];
  /**
   * Text sections inserted in side B as a DiffIntralineInfo entity.
   *
   * Only present during a replace, i.e. both a and b are present.
   */
  edit_b?: DiffIntralineInfo[];
  /** Indicates whether this entry was introduced by a rebase. */
  due_to_rebase?: boolean;

  /**
   * Provides info about a move operation the chunk.
   * It's presence indicates the current chunk exists due to a move.
   */
  move_details?: MoveDetails;
  /**
   * Count of lines skipped on both sides when the file is too large to include
   * all common lines.
   */
  skip?: number;
  /**
   * Set to true if the region is common according to the requested
   * ignore-whitespace parameter, but a and b contain differing amounts of
   * whitespace. When present and true a and b are used instead of ab.
   */
  common?: boolean;
}

/**
 * Details about move operation related to a specific chunk.
 */
export declare interface MoveDetails {
  /** Indicates whether the content of the chunk changes while moving code */
  changed: boolean;
  /**
   * Indicates the range (line numbers) on the other side of the comparison
   * where the code related to the current chunk came from/went to.
   */
  range: {
    start: number;
    end: number;
  };
}

/**
 * The DiffIntralineInfo entity contains information about intraline edits in a
 * file.
 *
 * The information consists of a list of <skip length, mark length> pairs, where
 * the skip length is the number of characters between the end of the previous
 * edit and the start of this edit, and the mark length is the number of edited
 * characters following the skip. The start of the edits is from the beginning
 * of the related diff content lines.
 *
 * Note that the implied newline character at the end of each line is included
 * in the length calculation, and thus it is possible for the edits to span
 * newlines.
 */
export declare type SkipLength = number;
export declare type MarkLength = number;
export declare type DiffIntralineInfo = [SkipLength, MarkLength];

/**
 * The DiffPreferencesInfo entity contains information about the diff
 * preferences of a user.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#diff-preferences-info
 */
export declare interface DiffPreferencesInfo {
  context: number;
  ignore_whitespace: IgnoreWhitespaceType;
  intraline_difference?: boolean;
  line_length: number;
  show_line_endings?: boolean;
  show_tabs?: boolean;
  show_whitespace_errors?: boolean;
  skip_uncommented?: boolean;
  syntax_highlighting?: boolean;
  auto_hide_diff_table_header?: boolean;
  tab_size: number;
  font_size: number;
  // TODO: Missing documentation
  show_file_comment_button?: boolean;
  // TODO: Missing documentation
  theme?: string;
}

export declare interface RenderPreferences {
  hide_left_side?: boolean;
  disable_context_control_buttons?: boolean;
}

/**
 * Whether whitespace changes should be ignored and if yes, which whitespace
 * changes should be ignored
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#diff-preferences-input
 */
export declare type IgnoreWhitespaceType =
  | 'IGNORE_NONE'
  | 'IGNORE_TRAILING'
  | 'IGNORE_LEADING_AND_TRAILING'
  | 'IGNORE_ALL';

export enum Side {
  LEFT = 'left',
  RIGHT = 'right',
}

export enum CoverageType {
  /**
   * start_character and end_character of the range will be ignored for this
   * type.
   */
  COVERED = 'COVERED',
  /**
   * start_character and end_character of the range will be ignored for this
   * type.
   */
  NOT_COVERED = 'NOT_COVERED',
  PARTIALLY_COVERED = 'PARTIALLY_COVERED',
  /**
   * You don't have to use this. If there is no coverage information for a
   * range, then it implicitly means NOT_INSTRUMENTED. start_character and
   * end_character of the range will be ignored for this type.
   */
  NOT_INSTRUMENTED = 'NOT_INSTRUMENTED',
}

export declare interface LineRange {
  start_line: number;
  end_line: number;
}

export declare interface CoverageRange {
  type: CoverageType;
  side: Side;
  code_range: LineRange;
}

export declare type LineNumber = number | 'FILE' | 'LOST';

/** The detail of the 'create-comment' event dispatched by gr-diff. */
export declare interface CreateCommentEventDetail {
  side: Side;
  lineNum: LineNumber;
  range: CommentRange | undefined;
}

export declare interface ContentLoadNeededEventDetail {
  lineRange: {
    left: LineRange;
    right: LineRange;
  };
}

export declare interface MovedLinkClickedEventDetail {
  side: Side;
  lineNum: LineNumber;
}

export enum GrDiffLineType {
  ADD = 'add',
  BOTH = 'both',
  BLANK = 'blank',
  REMOVE = 'remove',
}

/** Describes a line to be rendered in a diff. */
export declare interface GrDiffLine {
  readonly type: GrDiffLineType;
  /** The line number on the left side of the diff - 0 means none.  */
  beforeNumber: LineNumber;
  /** The line number on the right side of the diff - 0 means none.  */
  afterNumber: LineNumber;
}

/**
 * Interface to implemented to define a new layer in the diff.
 *
 * Layers can affect how the text of the diff or its line numbers
 * are rendered.
 */
export declare interface DiffLayer {
  /**
   * Called during rendering and allows annotating the diff text or line number
   * by mutating those elements.
   *
   * @param textElement The rendered text of one side of the diff.
   * @param lineNumberElement The rendered line number of one side of the diff.
   * @param line Describes the line that should be annotated.
   */
  annotate(
    textElement: HTMLElement,
    lineNumberElement: HTMLElement,
    line: GrDiffLine
  ): void;
}
