/**
 * @fileoverview The Gerrit diff API.
 *
 * This API is used by other apps embedding gr-diff and any breaking changes
 * should be discussed with the Gerrit core team and properly versioned.
 *
 * Should only contain types, no values, so that other apps using gr-diff can
 * use this solely to type check and generate externs for their separate ts
 * bundles.
 *
 * Should declare all types, to avoid renaming breaking multi-bundle setups.
 *
 * Enums should be converted to union types to avoid values in this file.
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
  /** A list of strings representing the patch set diff header. */
  diff_header: string[];
  /** The content differences in the file as a list of DiffContent entities. */
  content: DiffContent[];
  /**
   * Links to the file diff in external sites as a list of DiffWebLinkInfo
   * entries.
   */
  web_links?: DiffWebLinkInfo[];
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
  /** Links to the file in external sites as a list of WebLinkInfo entries. */
  web_links: WebLinkInfo[];
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
  /** @deprecated Use move_details instead. */
  due_to_move?: boolean;

  /**
   * Provides info about a move operation the chunk.
   * It's presence indicates the current chunk exists due to a move.
   */
  move_details?: {
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
  };
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
  // TODO: Undocumented, but used in code.
  keyLocation?: boolean;
}

/**
 * The DiffWebLinkInfo entity describes a link on a diff screen to an external
 * site.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#diff-web-link-info
 */
export declare interface DiffWebLinkInfo {
  /** The link name. */
  name: string;
  /** The link URL. */
  url: string;
  /** URL to the icon of the link. */
  image_url: string;
  // TODO: Are these really of type string? Not able to trigger them, but the
  // docs sound more like boolean.
  show_on_side_by_side_diff_view: string;
  show_on_unified_diff_view: string;
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
 * The WebLinkInfo entity describes a link to an external site.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#web-link-info
 */
export declare interface WebLinkInfo {
  /** The link name. */
  name: string;
  /** The link URL. */
  url: string;
  /** URL to the icon of the link. */
  image_url: string;
}

/**
 * The DiffPreferencesInfo entity contains information about the diff
 * preferences of a user.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-accounts.html#diff-preferences-info
 */
export declare interface DiffPreferencesInfo {
  context: number;
  expand_all_comments?: boolean;
  ignore_whitespace: IgnoreWhitespaceType;
  intraline_difference?: boolean;
  line_length: number;
  cursor_blink_rate: number;
  manual_review?: boolean;
  retain_header?: boolean;
  show_line_endings?: boolean;
  show_tabs?: boolean;
  show_whitespace_errors?: boolean;
  skip_deleted?: boolean;
  skip_uncommented?: boolean;
  syntax_highlighting?: boolean;
  hide_top_menu?: boolean;
  auto_hide_diff_table_header?: boolean;
  hide_line_numbers?: boolean;
  tab_size: number;
  font_size: number;
  hide_empty_pane?: boolean;
  match_brackets?: boolean;
  line_wrapping?: boolean;
  // TODO(TS): show_file_comment_button exists in JS code, but doesn't exist in
  // the doc. Either remove or update doc
  show_file_comment_button?: boolean;
  // TODO(TS): theme exists in JS code, but doesn't exist in the doc.
  // Either remove or update doc
  theme?: string;
}

export declare type DiffPreferencesInfoKey = keyof DiffPreferencesInfo;

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
