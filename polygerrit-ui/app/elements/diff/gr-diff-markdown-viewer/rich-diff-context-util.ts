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
import {DiffInfo} from '../../../types/diff';
import {normalizeSkipInfo} from '../../../utils/diff-util';
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

export interface SkipLineRange {
  start: number;
  end: number;
}

export interface DiffSkipRanges {
  left: SkipLineRange[];
  right: SkipLineRange[];
}

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
  /** Server-skipped line ranges from `diff.content` (`{skip: N}` chunks). */
  skipRanges?: DiffSkipRanges;
}

export interface CollapsedRichDiffSection<T extends RichDiffCollapsibleRow> {
  /** Stable key preserved across incremental partial expansions. */
  gapId: string;
  startIndex: number;
  endIndex: number;
  rows: T[];
  startLine?: number;
  endLine?: number;
  lineCount: number;
  hasSkippedLines: boolean;
  hasVisibleAbove: boolean;
  hasVisibleBelow: boolean;
  canExpandFromStart: boolean;
  canExpandFromEnd: boolean;
  partialStartRange?: {startLine: number; endLine: number};
  partialEndRange?: {startLine: number; endLine: number};
}

export type RichDiffDisplayItem<T extends RichDiffCollapsibleRow> =
  | {type: 'row'; index: number; row: T}
  | {type: 'collapsed'; section: CollapsedRichDiffSection<T>};

/** Value-based `hasChanged` comparator for `DisplayLine` Lit properties. */
export function lineOfInterestHasChanged(
  newVal?: DisplayLine,
  oldVal?: DisplayLine
): boolean {
  return newVal?.side !== oldVal?.side || newVal?.lineNum !== oldVal?.lineNum;
}

/**
 * Extracts 1-based inclusive line ranges for server-omitted `{skip: N}` chunks
 * so collapsed context bars do not over-count unfetched lines.
 */
export function extractSkipRanges(diff?: DiffInfo): DiffSkipRanges {
  const left: SkipLineRange[] = [];
  const right: SkipLineRange[] = [];
  if (!diff?.content) return {left, right};

  let leftLine = 1;
  let rightLine = 1;
  for (const chunk of diff.content) {
    if (chunk.skip) {
      const skip = normalizeSkipInfo(chunk.skip);
      if (skip.left > 0) {
        left.push({start: leftLine, end: leftLine + skip.left - 1});
        leftLine += skip.left;
      }
      if (skip.right > 0) {
        right.push({start: rightLine, end: rightLine + skip.right - 1});
        rightLine += skip.right;
      }
    } else if (chunk.ab) {
      leftLine += chunk.ab.length;
      rightLine += chunk.ab.length;
    } else {
      if (chunk.a) leftLine += chunk.a.length;
      if (chunk.b) rightLine += chunk.b.length;
    }
  }
  return {left, right};
}

function countSkippedLinesInRange(
  start: number,
  end: number,
  ranges?: readonly SkipLineRange[]
): number {
  if (!ranges || ranges.length === 0 || end < start) return 0;
  let skipped = 0;
  for (const range of ranges) {
    if (range.end < start) continue;
    if (range.start > end) break;
    const overlapStart = Math.max(start, range.start);
    const overlapEnd = Math.min(end, range.end);
    if (overlapEnd >= overlapStart) {
      skipped += overlapEnd - overlapStart + 1;
    }
  }
  return skipped;
}

function getSameSideSpan(
  first: RichDiffCollapsibleRow,
  last: RichDiffCollapsibleRow
): {side: Side; startLine: number; endLine: number} | undefined {
  if (
    first.rightStartLine !== undefined &&
    last.rightEndLine !== undefined &&
    last.rightEndLine >= first.rightStartLine
  ) {
    return {
      side: Side.RIGHT,
      startLine: first.rightStartLine,
      endLine: last.rightEndLine,
    };
  }
  if (
    first.leftStartLine !== undefined &&
    last.leftEndLine !== undefined &&
    last.leftEndLine >= first.leftStartLine
  ) {
    return {
      side: Side.LEFT,
      startLine: first.leftStartLine,
      endLine: last.leftEndLine,
    };
  }
  return undefined;
}

function getRowLineCount(row: RichDiffCollapsibleRow): number {
  const span = getSameSideSpan(row, row);
  if (span) {
    return span.endLine - span.startLine + 1;
  }
  return 1;
}

function computeSectionLineMetrics(
  rows: readonly RichDiffCollapsibleRow[],
  skipRanges?: DiffSkipRanges
): {
  startLine?: number;
  endLine?: number;
  lineCount: number;
  hasSkippedLines: boolean;
} {
  if (rows.length === 0) return {lineCount: 0, hasSkippedLines: false};
  const first = rows[0];
  const last = rows[rows.length - 1];
  const span = getSameSideSpan(first, last);
  if (span) {
    const sideSkips =
      span.side === Side.LEFT ? skipRanges?.left : skipRanges?.right;
    const skippedCount = countSkippedLinesInRange(
      span.startLine,
      span.endLine,
      sideSkips
    );
    const rawSpan = span.endLine - span.startLine + 1;
    return {
      startLine: span.startLine,
      endLine: span.endLine,
      lineCount: Math.max(1, rawSpan - skippedCount),
      hasSkippedLines: skippedCount > 0,
    };
  }
  const lineCount = rows.reduce((sum, row) => sum + getRowLineCount(row), 0);
  return {
    startLine: first.rightStartLine ?? first.leftStartLine,
    endLine: last.rightEndLine ?? last.leftEndLine,
    lineCount,
    hasSkippedLines: false,
  };
}

function mergeExpandedIntervals(
  ranges: ReadonlyArray<{start: number; end: number}>,
  pad: number
): Array<{start: number; end: number}> {
  if (ranges.length === 0) return [];
  const expanded = ranges.map(r => {
    return {
      start: r.start - pad,
      end: r.end + pad,
    };
  });
  expanded.sort((a, b) => a.start - b.start || a.end - b.end);

  const merged: Array<{start: number; end: number}> = [expanded[0]];
  for (let i = 1; i < expanded.length; i++) {
    const curr = expanded[i];
    const prev = merged[merged.length - 1];
    if (curr.start <= prev.end + 1) {
      if (curr.end > prev.end) prev.end = curr.end;
    } else {
      merged.push(curr);
    }
  }
  return merged;
}

function overlapsMergedIntervals(
  start: number,
  end: number,
  merged: ReadonlyArray<{start: number; end: number}>
): boolean {
  if (merged.length === 0) return false;
  let lo = 0;
  let hi = merged.length - 1;
  while (lo <= hi) {
    const mid = (lo + hi) >>> 1;
    const range = merged[mid];
    if (end < range.start) {
      hi = mid - 1;
    } else if (start > range.end) {
      lo = mid + 1;
    } else {
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

export interface PartialExpandOptions {
  /** Target number of lines to reveal (defaults to `PARTIAL_EXPAND_LINES`). */
  linesToExpand?: number;
  /** Server-skipped line ranges from `diff.content` (`{skip: N}` chunks). */
  skipRanges?: DiffSkipRanges;
}

/**
 * Computes which row indices to expand when clicking a partial expand control
 * (`+10`) on either edge of a collapsed section.
 */
export function getPartialExpandIndices<T extends RichDiffCollapsibleRow>(
  section: Pick<CollapsedRichDiffSection<T>, 'startIndex' | 'rows'>,
  direction: 'from-start' | 'from-end',
  options: PartialExpandOptions = {}
): number[] {
  const {rows, startIndex} = section;
  if (rows.length === 0) return [];
  const {linesToExpand = PARTIAL_EXPAND_LINES, skipRanges} = options;
  const fromStart = direction === 'from-start';
  const anchorRow = fromStart ? rows[0] : rows[rows.length - 1];
  const indices: number[] = [];
  let fallbackAccumulated = 0;

  for (let step = 0; step < rows.length; step++) {
    const i = fromStart ? step : rows.length - 1 - step;
    const row = rows[i];
    indices.push(startIndex + i);
    fallbackAccumulated += getRowLineCount(row);
    const span = fromStart
      ? getSameSideSpan(anchorRow, row)
      : getSameSideSpan(row, anchorRow);
    let accumulated = fallbackAccumulated;
    if (span) {
      const sideSkips =
        span.side === Side.LEFT ? skipRanges?.left : skipRanges?.right;
      const skipped = countSkippedLinesInRange(
        span.startLine,
        span.endLine,
        sideSkips
      );
      accumulated = Math.max(
        fallbackAccumulated,
        span.endLine - span.startLine + 1 - skipped
      );
    }
    if (accumulated >= linesToExpand) break;
  }
  return indices;
}

function getPartialExpandMetadata<T extends RichDiffCollapsibleRow>(
  rows: T[],
  startIndex: number,
  direction: 'from-start' | 'from-end',
  skipRanges?: DiffSkipRanges,
  linesToExpand = PARTIAL_EXPAND_LINES
): {
  canExpand: boolean;
  range?: {startLine: number; endLine: number};
} {
  if (rows.length <= 1) return {canExpand: false};
  const expanded = getPartialExpandIndices({startIndex, rows}, direction, {
    linesToExpand,
    skipRanges,
  });
  if (expanded.length === 0 || expanded.length >= rows.length) {
    return {canExpand: false};
  }
  const remaining =
    direction === 'from-start'
      ? rows.slice(expanded.length)
      : rows.slice(0, rows.length - expanded.length);
  if (
    computeSectionLineMetrics(remaining, skipRanges).lineCount <
    MIN_COLLAPSED_LINES
  ) {
    return {canExpand: false};
  }
  const slice =
    direction === 'from-start'
      ? rows.slice(0, expanded.length)
      : rows.slice(rows.length - expanded.length);
  const sliceSpan = getSameSideSpan(slice[0], slice[slice.length - 1]);
  return {
    canExpand: true,
    range: sliceSpan
      ? {startLine: sliceSpan.startLine, endLine: sliceSpan.endLine}
      : undefined,
  };
}

/**
 * Groups aligned rich diff rows into visible rows and collapsible unchanged
 * sections according to the user's diff context preference, attached comment
 * threads (including multi-line comment ranges), `lineOfInterest`, and manually
 * expanded row indices.
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
  const baseVisible = new Array<boolean>(rows.length).fill(false);
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
      baseVisible[i] = true;
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
      for (const thread of row.leftThreads) {
        const rangeStart = thread.range?.start_line;
        if (
          typeof rangeStart === 'number' &&
          Number.isFinite(rangeStart) &&
          rangeStart > 0
        ) {
          const rangeEnd = thread.range?.end_line ?? rangeStart;
          leftAnchorRanges.push({
            start: Math.min(rangeStart, rangeEnd),
            end: Math.max(rangeStart, rangeEnd),
          });
        }
      }
      for (const thread of row.rightThreads) {
        const rangeStart = thread.range?.start_line;
        if (
          typeof rangeStart === 'number' &&
          Number.isFinite(rangeStart) &&
          rangeStart > 0
        ) {
          const rangeEnd = thread.range?.end_line ?? rangeStart;
          rightAnchorRanges.push({
            start: Math.min(rangeStart, rangeEnd),
            end: Math.max(rangeStart, rangeEnd),
          });
        }
      }
    }
  }

  const loi = options.lineOfInterest;
  if (
    loi &&
    typeof loi.lineNum === 'number' &&
    Number.isFinite(loi.lineNum) &&
    loi.lineNum > 0
  ) {
    const lineNum = loi.lineNum;
    hasAnyAnchor = true;
    const targetRange = {start: lineNum, end: lineNum};
    if (loi.side === Side.LEFT) {
      leftAnchorRanges.push(targetRange);
    } else {
      rightAnchorRanges.push(targetRange);
    }
    const nearestIdx = findNearestRowIndexForLine(rows, loi.side, lineNum);
    if (nearestIdx !== undefined) {
      baseVisible[nearestIdx] = true;
    }
  }

  if (!hasAnyAnchor) {
    const totalLines = computeSectionLineMetrics(
      rows,
      options.skipRanges
    ).lineCount;
    if (contextLines > 0 && totalLines <= contextLines) {
      return rows.map((row, index) => {
        return {type: 'row', index, row};
      });
    }
  } else {
    const mergedLeft = mergeExpandedIntervals(leftAnchorRanges, contextLines);
    const mergedRight = mergeExpandedIntervals(rightAnchorRanges, contextLines);
    for (let i = 0; i < rows.length; i++) {
      if (baseVisible[i]) continue;
      const row = rows[i];
      const matchesLeft =
        row.leftStartLine !== undefined &&
        row.leftEndLine !== undefined &&
        overlapsMergedIntervals(row.leftStartLine, row.leftEndLine, mergedLeft);
      const matchesRight =
        row.rightStartLine !== undefined &&
        row.rightEndLine !== undefined &&
        overlapsMergedIntervals(
          row.rightStartLine,
          row.rightEndLine,
          mergedRight
        );
      if (matchesLeft || matchesRight) {
        baseVisible[i] = true;
      }
    }
  }

  // Assign each contiguous unexpanded gap a stable ID so incremental partial
  // expansion from either edge keeps the same key in Lit `repeat()`.
  const baseGapIds = new Array<string>(rows.length).fill('');
  let gapCursor = 0;
  while (gapCursor < rows.length) {
    if (baseVisible[gapCursor]) {
      gapCursor++;
      continue;
    }
    const gapStart = gapCursor;
    while (gapCursor < rows.length && !baseVisible[gapCursor]) {
      gapCursor++;
    }
    const gapId = `gap-${gapStart}-${gapCursor - 1}`;
    for (let j = gapStart; j < gapCursor; j++) {
      baseGapIds[j] = gapId;
    }
  }

  const visible = baseVisible.map(
    (isBaseVis, idx) =>
      isBaseVis || Boolean(options.expandedRowIndices?.has(idx))
  );

  const usedGapIds = new Set<string>();
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
    const metrics = computeSectionLineMetrics(hiddenRows, options.skipRanges);

    if (metrics.lineCount < MIN_COLLAPSED_LINES) {
      for (let j = runStart; j <= runEnd; j++) {
        items.push({type: 'row', index: j, row: rows[j]});
      }
      continue;
    }

    const hasVisibleAbove = runStart > 0;
    const hasVisibleBelow = runEnd < rows.length - 1;
    const isWholeFileCollapsed = !hasVisibleAbove && !hasVisibleBelow;

    const startMeta =
      hasVisibleAbove || isWholeFileCollapsed
        ? getPartialExpandMetadata(
            hiddenRows,
            runStart,
            'from-start',
            options.skipRanges
          )
        : {canExpand: false};
    const endMeta = hasVisibleBelow
      ? getPartialExpandMetadata(
          hiddenRows,
          runStart,
          'from-end',
          options.skipRanges
        )
      : {canExpand: false};

    const candidateGapId = baseGapIds[runStart] || `gap-${runStart}-${runEnd}`;
    const gapId = usedGapIds.has(candidateGapId)
      ? `${candidateGapId}-${runStart}`
      : candidateGapId;
    usedGapIds.add(gapId);

    items.push({
      type: 'collapsed',
      section: {
        gapId,
        startIndex: runStart,
        endIndex: runEnd,
        rows: hiddenRows,
        startLine: metrics.startLine,
        endLine: metrics.endLine,
        lineCount: metrics.lineCount,
        hasSkippedLines: metrics.hasSkippedLines,
        hasVisibleAbove,
        hasVisibleBelow,
        canExpandFromStart: startMeta.canExpand,
        canExpandFromEnd: endMeta.canExpand,
        partialStartRange: startMeta.range,
        partialEndRange: endMeta.range,
      },
    });
  }

  return items;
}

export const richDiffContextBarStyles = css`
  .rich-context-bar {
    grid-column: 1 / -1;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: center;
    gap: var(--spacing-m, 8px);
    padding: var(--spacing-s, 4px) var(--spacing-l, 12px);
    margin: var(--spacing-s, 4px) 0;
    background-color: var(--background-color-secondary, #f8f9fa);
    border: 1px solid var(--border-color, #dadce0);
    border-radius: var(--border-radius, 4px);
    user-select: none;
  }
  .unified-container .rich-context-bar {
    margin-bottom: var(--spacing-m, 8px);
  }
  .rich-context-btn {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 4px 12px;
    border: 1px solid var(--border-color, #dadce0);
    border-radius: var(--border-radius, 4px);
    background-color: var(--background-color-primary, #ffffff);
    color: var(--default-button-text-color, var(--primary-text-color, #202124));
    font-family: inherit;
    font-size: var(--font-size-small, 12px);
    font-weight: var(--font-weight-medium, 500);
    line-height: 18px;
    white-space: nowrap;
    cursor: pointer;
  }
  .rich-context-btn:hover {
    background: linear-gradient(
        var(--hover-background-color, rgba(161, 194, 250, 0.2)),
        var(--hover-background-color, rgba(161, 194, 250, 0.2))
      ),
      var(--background-color-primary, #ffffff);
  }
  .rich-context-btn:active {
    background: linear-gradient(rgba(0, 0, 0, 0.12), rgba(0, 0, 0, 0.12)),
      var(--background-color-primary, #ffffff);
  }
  .rich-context-btn:focus-visible {
    outline: 2px solid var(--primary-button-background-color, #1a73e8);
    outline-offset: 1px;
  }
  .rich-context-btn gr-icon {
    --gr-icon-size: 16px;
    color: inherit;
  }
  .diff-cell[tabindex='-1']:focus:not(:focus-visible),
  .unified-block[tabindex='-1']:focus:not(:focus-visible) {
    outline: none;
  }
`;

function formatPartialLabel(
  direction: 'from-start' | 'from-end',
  section: CollapsedRichDiffSection<RichDiffCollapsibleRow>
): string {
  const range =
    direction === 'from-start'
      ? section.partialStartRange
      : section.partialEndRange;
  const rangeText = range
    ? `lines ${range.startLine}–${range.endLine}`
    : `${PARTIAL_EXPAND_LINES} more lines`;
  if (direction === 'from-start') {
    return section.hasVisibleAbove
      ? `Show ${rangeText} below previous section`
      : `Show ${rangeText} from top`;
  }
  return `Show ${rangeText} above next section`;
}

/**
 * Renders the expandable context control bar for a collapsed run of unchanged
 * rich diff rows. Keeps `+N common lines` consistently on the left with
 * directional `+10` expand buttons on the right.
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
  const partialSuffix = section.hasSkippedLines ? ' (partial diff)' : '';
  const rangeSuffix =
    section.startLine !== undefined && section.endLine !== undefined
      ? ` (lines ${section.startLine}–${section.endLine})`
      : '';
  const fromStartLabel = formatPartialLabel('from-start', section);
  const fromEndLabel = formatPartialLabel('from-end', section);

  return html`
    <div
      class="rich-context-bar"
      role="group"
      aria-label="Collapsed unchanged context (${commonLinesLabel}${partialSuffix})"
      data-gap-id=${section.gapId}
      data-start-index=${section.startIndex}
      data-end-index=${section.endIndex}
    >
      <button
        type="button"
        class="rich-context-btn expand-all"
        title="Show ${commonLinesLabel}${rangeSuffix}${partialSuffix}"
        aria-label="Show ${commonLinesLabel}${rangeSuffix}${partialSuffix}"
        @click=${() => onExpandAll(section)}
      >
        <gr-icon icon="unfold_more" aria-hidden="true"></gr-icon>
        <span>+${commonLinesLabel}${partialSuffix}</span>
      </button>
      ${when(
        section.canExpandFromStart,
        () => html`
          <button
            type="button"
            class="rich-context-btn expand-from-start"
            title=${fromStartLabel}
            aria-label=${fromStartLabel}
            @click=${() => onExpandPartial(section, 'from-start')}
          >
            <gr-icon icon="arrow_downward" aria-hidden="true"></gr-icon>
            <span>+${PARTIAL_EXPAND_LINES}</span>
          </button>
        `
      )}
      ${when(
        section.canExpandFromEnd,
        () => html`
          <button
            type="button"
            class="rich-context-btn expand-from-end"
            title=${fromEndLabel}
            aria-label=${fromEndLabel}
            @click=${() => onExpandPartial(section, 'from-end')}
          >
            <gr-icon icon="arrow_upward" aria-hidden="true"></gr-icon>
            <span>+${PARTIAL_EXPAND_LINES}</span>
          </button>
        `
      )}
    </div>
  `;
}
