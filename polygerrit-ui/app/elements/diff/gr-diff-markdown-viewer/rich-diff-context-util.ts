/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, TemplateResult} from 'lit';
import {when} from 'lit/directives/when.js';
import '../../shared/gr-icon/gr-icon';
import {DisplayLine, Side} from '../../../api/diff';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {
  computeContext,
  FULL_CONTEXT,
  FullContext,
} from '../../../embed/diff/gr-diff/gr-diff-utils';
import {CommentThread} from '../../../types/common';
import {pluralize} from '../../../utils/string-util';
import {DiffBlockStatus} from './markdown-diff-util';

/**
 * Minimum number of hidden lines required to collapse a contiguous stretch of
 * unchanged rich diff blocks. Matches `hideInContextControl` in
 * `gr-diff-group.ts` (> 3 lines) so a collapse bar never takes more vertical
 * space than the collapsed content itself.
 */
export const MIN_COLLAPSED_LINES = 4;

/** Default number of lines revealed when clicking an incremental expand button. */
export const PARTIAL_EXPAND_LINES = 10;

export interface RichDiffCollapsibleRow {
  status: DiffBlockStatus;
  leftStartLine?: number;
  leftEndLine?: number;
  rightStartLine?: number;
  rightEndLine?: number;
  leftThreads: CommentThread[];
  rightThreads: CommentThread[];
}

export interface RichDiffContextOptions {
  /** Number of context lines from user diff prefs, or `FULL_CONTEXT` (-1). */
  context?: number;
  /** Override toggled via "Show Entire File" / `Shift+X`. */
  showFullContext?: FullContext;
  /** Row indices explicitly expanded by clicking context controls. */
  expandedRowIndices?: ReadonlySet<number>;
  /** Target line from URL / comment anchor that must remain visible. */
  lineOfInterest?: DisplayLine;
}

export interface CollapsedRichDiffSection<T extends RichDiffCollapsibleRow> {
  startIndex: number;
  endIndex: number;
  rows: T[];
  startLine?: number;
  endLine?: number;
  lineCount: number;
  hasVisibleAbove: boolean;
  hasVisibleBelow: boolean;
  canExpandFromStart: boolean;
  canExpandFromEnd: boolean;
}

export type RichDiffDisplayItem<T extends RichDiffCollapsibleRow> =
  | {type: 'row'; index: number; row: T}
  | {type: 'collapsed'; section: CollapsedRichDiffSection<T>};

function getRowLineCount(row: RichDiffCollapsibleRow): number {
  const start = row.rightStartLine ?? row.leftStartLine;
  const end = row.rightEndLine ?? row.leftEndLine;
  if (start !== undefined && end !== undefined && end >= start) {
    return end - start + 1;
  }
  return 1;
}

function computeSectionLineMetrics(rows: readonly RichDiffCollapsibleRow[]): {
  startLine?: number;
  endLine?: number;
  lineCount: number;
} {
  if (rows.length === 0) return {lineCount: 0};
  const first = rows[0];
  const last = rows[rows.length - 1];
  const startLine = first.rightStartLine ?? first.leftStartLine;
  const endLine = last.rightEndLine ?? last.leftEndLine;
  if (
    startLine !== undefined &&
    endLine !== undefined &&
    endLine >= startLine
  ) {
    return {
      startLine,
      endLine,
      lineCount: endLine - startLine + 1,
    };
  }
  const lineCount = rows.reduce((sum, row) => sum + getRowLineCount(row), 0);
  return {startLine, endLine, lineCount};
}

function overlapsRange(
  start: number,
  end: number,
  ranges: ReadonlyArray<{start: number; end: number}>,
  context: number
): boolean {
  for (const range of ranges) {
    if (end >= range.start - context && start <= range.end + context) {
      return true;
    }
  }
  return false;
}

function findNearestRowIndexForLine(
  rows: readonly RichDiffCollapsibleRow[],
  side: Side,
  lineNum: number
): number | undefined {
  const isLeft = side === Side.LEFT;
  let bestIndex: number | undefined;
  let bestDistance = Infinity;
  for (let i = 0; i < rows.length; i++) {
    const start = isLeft ? rows[i].leftStartLine : rows[i].rightStartLine;
    const end = isLeft ? rows[i].leftEndLine : rows[i].rightEndLine;
    if (start === undefined || end === undefined) continue;
    if (lineNum >= start && lineNum <= end) {
      return i;
    }
    const dist = lineNum < start ? start - lineNum : lineNum - end;
    if (dist < bestDistance) {
      bestDistance = dist;
      bestIndex = i;
    }
  }
  return bestIndex;
}

/**
 * Computes which row indices to expand when clicking a partial expand control
 * (`+10`) on either edge of a collapsed section.
 */
export function getPartialExpandIndices<T extends RichDiffCollapsibleRow>(
  section: Pick<CollapsedRichDiffSection<T>, 'startIndex' | 'rows'>,
  direction: 'from-start' | 'from-end',
  linesToExpand = PARTIAL_EXPAND_LINES
): number[] {
  const {rows, startIndex} = section;
  if (rows.length === 0) return [];
  const indices: number[] = [];
  if (direction === 'from-start') {
    const firstLine = rows[0].rightStartLine ?? rows[0].leftStartLine;
    let accumulated = 0;
    for (let i = 0; i < rows.length; i++) {
      indices.push(startIndex + i);
      const rowEnd = rows[i].rightEndLine ?? rows[i].leftEndLine;
      if (firstLine !== undefined && rowEnd !== undefined) {
        accumulated = rowEnd - firstLine + 1;
      } else {
        accumulated += getRowLineCount(rows[i]);
      }
      if (accumulated >= linesToExpand) break;
    }
  } else {
    const lastRow = rows[rows.length - 1];
    const lastLine = lastRow.rightEndLine ?? lastRow.leftEndLine;
    let accumulated = 0;
    for (let i = rows.length - 1; i >= 0; i--) {
      indices.push(startIndex + i);
      const rowStart = rows[i].rightStartLine ?? rows[i].leftStartLine;
      if (lastLine !== undefined && rowStart !== undefined) {
        accumulated = lastLine - rowStart + 1;
      } else {
        accumulated += getRowLineCount(rows[i]);
      }
      if (accumulated >= linesToExpand) break;
    }
  }
  return indices;
}

function canPartiallyExpand<T extends RichDiffCollapsibleRow>(
  rows: T[],
  startIndex: number,
  direction: 'from-start' | 'from-end',
  linesToExpand = PARTIAL_EXPAND_LINES
): boolean {
  if (rows.length <= 1) return false;
  const expanded = getPartialExpandIndices(
    {startIndex, rows},
    direction,
    linesToExpand
  );
  if (expanded.length >= rows.length) return false;
  const remaining =
    direction === 'from-start'
      ? rows.slice(expanded.length)
      : rows.slice(0, rows.length - expanded.length);
  return computeSectionLineMetrics(remaining).lineCount >= MIN_COLLAPSED_LINES;
}

/**
 * Groups aligned rich diff rows into visible rows and collapsible unchanged
 * sections according to the user's diff context preference, attached comment
 * threads, `lineOfInterest`, and manually expanded row indices.
 */
export function groupRichDiffRows<T extends RichDiffCollapsibleRow>(
  rows: T[],
  options: RichDiffContextOptions = {}
): RichDiffDisplayItem<T>[] {
  if (rows.length === 0) return [];

  const effectiveContext = computeContext(
    options.context,
    options.showFullContext ?? FullContext.UNDECIDED,
    createDefaultDiffPrefs().context
  );

  if (effectiveContext === FULL_CONTEXT) {
    return rows.map((row, index) => {
      return {type: 'row', index, row};
    });
  }

  const contextLines = Math.max(0, effectiveContext);
  const visible = new Array<boolean>(rows.length).fill(false);
  const leftAnchorRanges: Array<{start: number; end: number}> = [];
  const rightAnchorRanges: Array<{start: number; end: number}> = [];
  let hasAnyAnchor = false;

  for (let i = 0; i < rows.length; i++) {
    const row = rows[i];
    const isAnchor =
      row.status !== 'unchanged' ||
      row.leftThreads.length > 0 ||
      row.rightThreads.length > 0;
    if (isAnchor) {
      hasAnyAnchor = true;
      visible[i] = true;
      if (row.leftStartLine !== undefined && row.leftEndLine !== undefined) {
        leftAnchorRanges.push({
          start: row.leftStartLine,
          end: row.leftEndLine,
        });
      }
      if (row.rightStartLine !== undefined && row.rightEndLine !== undefined) {
        rightAnchorRanges.push({
          start: row.rightStartLine,
          end: row.rightEndLine,
        });
      }
    }
    if (options.expandedRowIndices?.has(i)) {
      visible[i] = true;
    }
  }

  if (
    options.lineOfInterest &&
    typeof options.lineOfInterest.lineNum === 'number'
  ) {
    const lineNum = options.lineOfInterest.lineNum;
    hasAnyAnchor = true;
    const targetRange = {start: lineNum, end: lineNum};
    if (options.lineOfInterest.side === Side.LEFT) {
      leftAnchorRanges.push(targetRange);
    } else {
      rightAnchorRanges.push(targetRange);
    }
    const nearestIdx = findNearestRowIndexForLine(
      rows,
      options.lineOfInterest.side,
      lineNum
    );
    if (nearestIdx !== undefined) {
      visible[nearestIdx] = true;
    }
  }

  if (!hasAnyAnchor) {
    const totalLines = computeSectionLineMetrics(rows).lineCount;
    if (contextLines > 0 && totalLines <= contextLines) {
      return rows.map((row, index) => {
        return {type: 'row', index, row};
      });
    }
  } else {
    for (let i = 0; i < rows.length; i++) {
      if (visible[i]) continue;
      const row = rows[i];
      const matchesLeft =
        row.leftStartLine !== undefined &&
        row.leftEndLine !== undefined &&
        overlapsRange(
          row.leftStartLine,
          row.leftEndLine,
          leftAnchorRanges,
          contextLines
        );
      const matchesRight =
        row.rightStartLine !== undefined &&
        row.rightEndLine !== undefined &&
        overlapsRange(
          row.rightStartLine,
          row.rightEndLine,
          rightAnchorRanges,
          contextLines
        );
      if (matchesLeft || matchesRight) {
        visible[i] = true;
      }
    }
  }

  const items: RichDiffDisplayItem<T>[] = [];
  let i = 0;
  while (i < rows.length) {
    if (visible[i]) {
      items.push({type: 'row', index: i, row: rows[i]});
      i++;
      continue;
    }

    const runStart = i;
    while (i < rows.length && !visible[i]) {
      i++;
    }
    const runEnd = i - 1;
    const hiddenRows = rows.slice(runStart, runEnd + 1);
    const metrics = computeSectionLineMetrics(hiddenRows);

    if (metrics.lineCount < MIN_COLLAPSED_LINES) {
      for (let j = runStart; j <= runEnd; j++) {
        items.push({type: 'row', index: j, row: rows[j]});
      }
      continue;
    }

    const hasVisibleAbove = runStart > 0;
    const hasVisibleBelow = runEnd < rows.length - 1;
    items.push({
      type: 'collapsed',
      section: {
        startIndex: runStart,
        endIndex: runEnd,
        rows: hiddenRows,
        startLine: metrics.startLine,
        endLine: metrics.endLine,
        lineCount: metrics.lineCount,
        hasVisibleAbove,
        hasVisibleBelow,
        canExpandFromStart:
          hasVisibleAbove &&
          canPartiallyExpand(hiddenRows, runStart, 'from-start'),
        canExpandFromEnd:
          hasVisibleBelow &&
          canPartiallyExpand(hiddenRows, runStart, 'from-end'),
      },
    });
  }

  return items;
}

export const richDiffContextBarStyles = css`
  .rich-context-bar {
    grid-column: 1 / -1;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: var(--spacing-s, 8px);
    padding: var(--spacing-xs, 4px) var(--spacing-m, 12px);
    margin: var(--spacing-xs, 4px) 0;
    background-color: var(--background-color-secondary, #f8f9fa);
    border-top: 1px solid var(--border-color, #e0e0e0);
    border-bottom: 1px solid var(--border-color, #e0e0e0);
    border-radius: var(--border-radius, 4px);
    user-select: none;
  }
  .rich-context-btn {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 4px 12px;
    border: 1px solid var(--border-color, #dadce0);
    border-radius: var(--border-radius, 4px);
    background-color: var(--background-color-primary, #ffffff);
    color: var(
      --diff-context-control-color,
      var(--primary-text-color, #202124)
    );
    font-family: inherit;
    font-size: var(--font-size-small, 12px);
    font-weight: var(--font-weight-medium, 500);
    line-height: 18px;
    cursor: pointer;
  }
  .rich-context-btn:hover {
    background-color: var(--hover-background-color, rgba(161, 194, 250, 0.2));
  }
  .rich-context-btn:focus-visible {
    outline: 2px solid var(--primary-button-background-color, #1a73e8);
    outline-offset: 1px;
  }
  .rich-context-btn gr-icon {
    --gr-icon-size: 16px;
    color: inherit;
  }
`;

/**
 * Renders the expandable context control bar for a collapsed run of unchanged
 * rich diff rows.
 */
export function renderRichDiffContextBar<T extends RichDiffCollapsibleRow>(
  section: CollapsedRichDiffSection<T>,
  onExpandAll: (section: CollapsedRichDiffSection<T>) => void,
  onExpandPartial: (
    section: CollapsedRichDiffSection<T>,
    direction: 'from-start' | 'from-end'
  ) => void
): TemplateResult {
  const commonLinesLabel = pluralize(section.lineCount, 'common line');
  const rangeSuffix =
    section.startLine !== undefined && section.endLine !== undefined
      ? ` (lines ${section.startLine}–${section.endLine})`
      : '';

  return html`
    <div
      class="rich-context-bar"
      role="group"
      aria-label="Collapsed unchanged context (${commonLinesLabel})"
      data-start-index=${section.startIndex}
      data-end-index=${section.endIndex}
    >
      ${when(
        section.canExpandFromStart,
        () => html`
          <button
            type="button"
            class="rich-context-btn expand-from-start"
            title="Show ${PARTIAL_EXPAND_LINES} more lines below"
            aria-label="Show ${PARTIAL_EXPAND_LINES} more lines below"
            @click=${() => onExpandPartial(section, 'from-start')}
          >
            <gr-icon icon="expand_more"></gr-icon>
            <span>+${PARTIAL_EXPAND_LINES}</span>
          </button>
        `
      )}
      <button
        type="button"
        class="rich-context-btn expand-all"
        title="Show ${commonLinesLabel}${rangeSuffix}"
        aria-label="Show ${commonLinesLabel}${rangeSuffix}"
        @click=${() => onExpandAll(section)}
      >
        <gr-icon icon="unfold_more"></gr-icon>
        <span>+${commonLinesLabel}</span>
      </button>
      ${when(
        section.canExpandFromEnd,
        () => html`
          <button
            type="button"
            class="rich-context-btn expand-from-end"
            title="Show ${PARTIAL_EXPAND_LINES} more lines above"
            aria-label="Show ${PARTIAL_EXPAND_LINES} more lines above"
            @click=${() => onExpandPartial(section, 'from-end')}
          >
            <gr-icon icon="expand_less"></gr-icon>
            <span>+${PARTIAL_EXPAND_LINES}</span>
          </button>
        `
      )}
    </div>
  `;
}
