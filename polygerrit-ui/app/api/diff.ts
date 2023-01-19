/**
 * @fileoverview The API of Gerrit's diff viewer, gr-diff.
 *
 * This includes some types which are also defined as part of Gerrit's JSON API
 * which are used as inputs to gr-diff.
 *
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {CommentRange, CursorMoveResult} from './core';

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
  /**
   * Meta information about the file on side A as a DiffFileMetaInfo entity.
   * Not set when change_type is ADDED.
   */
  meta_a?: DiffFileMetaInfo;
  /**
   * Meta information about the file on side B as a DiffFileMetaInfo entity.
   * Not set when change_type is DELETED.
   */
  meta_b?: DiffFileMetaInfo;
  /** The type of change (ADDED, MODIFIED, DELETED, RENAMED COPIED, REWRITE). */
  change_type: ChangeType;
  /** Intraline status (OK, ERROR, TIMEOUT). */
  intraline_status: 'OK' | 'Error' | 'Timeout';
  /** The content differences in the file as a list of DiffContent entities. */
  content: DiffContent[];
  /** Whether the file is binary. */
  binary?: boolean;
  /** A list of strings representing the patch set diff header. */
  diff_header?: string[];
}

/**
 * Represents a "generic" text range in the code (e.g. text selection)
 */
export declare interface TextRange {
  /** first line of the range (1-based inclusive). */
  start_line: number;
  /** first column of the range (in the first line) (1-based inclusive). */
  start_column: number;
  /** last line of the range (1-based inclusive). */
  end_line: number;
  /** last column of the range (in the end line) (1-based inclusive). */
  end_column: number;
}

/**
 * Represents a syntax block in a code (e.g. method, function, class, if-else).
 */
export declare interface SyntaxBlock {
  /** Name of the block (e.g. name of the method/class)*/
  name: string;
  /**
   * Where does this block syntatically starts and ends (line number and
   * column).
   */
  range: TextRange;
  /** Sub-blocks of the current syntax block (e.g. methods of a class) */
  children: SyntaxBlock[];
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
  /**
   * The first level of syntax blocks tree (outline) within the current file.
   * It contains an hierarchical structure where each block contains its
   * sub-blocks (children).
   */
  syntax_tree?: SyntaxBlock[];
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
  range?: {
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
  line_length: number;
  show_line_endings?: boolean;
  show_tabs?: boolean;
  show_whitespace_errors?: boolean;
  syntax_highlighting?: boolean;
  tab_size: number;
  font_size: number;
  // Hides the FILE and LOST diff rows. Default is TRUE.
  show_file_comment_button?: boolean;
  line_wrapping?: boolean;
}

/**
 * Event details when a token is highlighted.
 */
export declare interface TokenHighlightEventDetails {
  token: string;
  element: Element;
  side: Side;
  range: TextRange;
}

/**
 * Listens to changes in token highlighting - when a new token starts or stopped
 * being highlighted. undefined is sent if the event is about a clear in
 * highlighting.
 */
export type TokenHighlightListener = (
  tokenHighlightEvent?: TokenHighlightEventDetails
) => void;

export declare interface ImageDiffPreferences {
  automatic_blink?: boolean;
}

export declare type DiffResponsiveMode =
  | 'FULL_RESPONSIVE'
  | 'SHRINK_ONLY'
  | 'NONE';
export declare interface RenderPreferences {
  hide_left_side?: boolean;
  disable_context_control_buttons?: boolean;
  show_file_comment_button?: boolean;
  hide_line_length_indicator?: boolean;
  use_block_expansion?: boolean;
  image_diff_prefs?: ImageDiffPreferences;
  responsive_mode?: DiffResponsiveMode;
  num_lines_rendered_at_once?: number;
  /**
   * If enabled, then a new (experimental) diff rendering is used that is
   * based on Lit components and multiple rendering passes. This is planned to
   * be a temporary setting until the experiment is concluded.
   */
  use_lit_components?: boolean;
  show_sign_col?: boolean;
  /**
   * The default view mode is SIDE_BY_SIDE.
   *
   * Note that gr-diff also still supports setting viewMode as a dedicated
   * property on <gr-diff>. TODO: Migrate usages to RenderPreferences.
   */
  view_mode?: DiffViewMode;
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
  /** 1-based, inclusive. */
  start_line: number;
  /** 1-based, inclusive. */
  end_line: number;
}

export declare interface CoverageRange {
  type: CoverageType;
  side: Side;
  code_range: LineRange;
}

/** LOST LineNumber is for ported comments without a range, they have their own
 *  line number and are added on top of the FILE row in gr-diff
 */
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

export declare interface LineNumberEventDetail {
  side: Side;
  lineNum: LineNumber;
}

// TODO: Currently unused and not fired.
export declare interface RenderProgressEventDetail {
  linesRendered: number;
}

export declare interface DisplayLine {
  side: Side;
  lineNum: LineNumber;
}

/** All types of button for expanding diff sections */
export enum ContextButtonType {
  ABOVE = 'above',
  BELOW = 'below',
  BLOCK_ABOVE = 'block-above',
  BLOCK_BELOW = 'block-below',
  ALL = 'all',
}

/** Details to be externally accessed when expanding diffs */
export declare interface DiffContextExpandedExternalDetail {
  expandedLines: number;
  buttonType: ContextButtonType;
}

/**
 * Details to be externally accessed when hovering context
 * expansion buttons
 */
export declare interface DiffContextButtonHoveredDetail {
  linesToExpand: number;
  buttonType: ContextButtonType;
}

export declare type ImageDiffAction =
  | {type: 'overview-image-clicked'}
  | {type: 'overview-frame-dragged'}
  | {type: 'magnifier-clicked'}
  | {type: 'magnifier-dragged'}
  | {type: 'version-switcher-clicked'; button: 'base' | 'revision' | 'switch'}
  | {
      type: 'highlight-changes-changed';
      value: boolean;
      source: 'controls' | 'magnifier';
    }
  | {type: 'zoom-level-changed'; scale: number | 'fit'}
  | {type: 'follow-mouse-changed'; value: boolean}
  | {type: 'background-color-changed'; value: string}
  | {type: 'automatic-blink-changed'; value: boolean};

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
   * @param side Which side of the diff is being annotated.
   */
  annotate(
    textElement: HTMLElement,
    lineNumberElement: HTMLElement,
    line: GrDiffLine,
    side: Side
  ): void;
}

/** Data used by GrAnnotation to generate elements. */
export declare interface ElementSpec {
  tagName: string;
  attributes?: {[key: string]: unknown};
}

/** Used to annotate segments of an HTMLElement with a class string. */
export declare interface GrAnnotation {
  /**
   * Annotates the [offset, offset+length) text segment in the parent with the
   * element definition provided as arguments.
   *
   * @param parent the node whose contents will be annotated.
   * If parent is Text then parent.parentNode must not be null
   * @param offset the 0-based offset from which the annotation will
   * start.
   * @param length of the annotated text.
   * @param elementSpec the spec to create the
   * annotating element.
   */
  annotateWithElement(
    el: HTMLElement,
    start: number,
    length: number,
    elementSpec: ElementSpec
  ): void;

  /**
   * Surrounds the element's text at specified range in an ANNOTATION_TAG
   * element. If the element has child elements, the range is split and
   * applied as deeply as possible.
   */
  annotateElement(
    el: HTMLElement,
    start: number,
    length: number,
    className: string
  ): void;
}

/** An instance of the GrDiff Webcomponent */
export declare interface GrDiff extends HTMLElement {
  /**
   * A line that should not be collapsed, e.g. because it contains a
   * search result, or is pointed to from the URL.
   * This is considered during rendering, but changing this does not
   * automatically trigger a re-render.
   */
  lineOfInterest?: DisplayLine;

  /**
   * Return line number element for reading only,
   *
   * This is useful e.g. to determine where on screen certain lines are,
   * whether they are covered up etc.
   */
  getLineNumEls(side: Side): readonly HTMLElement[];
}

/** A service to interact with the line cursor in gr-diff instances. */
export declare interface GrDiffCursor {
  // The current setup requires API users to register GrDiff instances with the
  // cursor, but we do not at this point want to expose the API that GrDiffCursor
  // uses to the public as it is likely to change. So for now, we allow any type
  // and cast. This works fine so long as API users do provide whatever the
  // gr-diff tag creates.
  replaceDiffs(diffs: unknown[]): void;
  unregisterDiff(diff: unknown): void;

  isAtStart(): boolean;
  isAtEnd(): boolean;

  moveLeft(): void;
  moveRight(): void;

  moveDown(): CursorMoveResult;
  moveUp(): CursorMoveResult;

  moveToFirstChunk(): void;
  moveToLastChunk(): void;

  moveToNextChunk(): CursorMoveResult;
  moveToPreviousChunk(): CursorMoveResult;

  moveToNextCommentThread(): CursorMoveResult;
  moveToPreviousCommentThread(): CursorMoveResult;

  createCommentInPlace(): void;
  resetScrollMode(): void;

  /**
   * Moves to a specific line number in the diff
   *
   * @param lineNum which line number should be selected
   * @param side which side should be selected
   * @param path file path for the file that should be selected
   * @param intentionalMove Defines if move-related controls should be applied
   * (e.g. GrCursorManager.focusOnMove)
   **/
  moveToLineNumber(
    lineNum: number,
    side: Side,
    path?: string,
    intentionalMove?: boolean
  ): void;
}
