/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Subscription} from 'rxjs';
import {AbortStop, CursorMoveResult, Stop} from '../../../api/core';
import {
  DiffViewMode,
  GrDiffCursor as GrDiffCursorApi,
  GrDiffLineType,
  LineNumber,
  LineSelectedEventDetail,
} from '../../../api/diff';
import {ScrollMode, Side} from '../../../constants/constants';
import {toggleClass} from '../../../utils/dom-util';
import {
  GrCursorManager,
  isTargetable,
} from '../../../elements/shared/gr-cursor-manager/gr-cursor-manager';
<<<<<<< PATCH SET (2afdb4 Simplify `isTargetBlank` in gr-diff-cursor)
import {GrDiffLineType} from '../gr-diff/gr-diff-line';
=======
import {GrDiffGroupType} from '../gr-diff/gr-diff-group';
>>>>>>> BASE      (4c4e81 Simplify gr-diff-cursor lookups)
import {GrDiff} from '../gr-diff/gr-diff';
import {fire} from '../../../utils/event-util';
import {GrDiffRow} from '../gr-diff-builder/gr-diff-row';

const LEFT_SIDE_CLASS = 'target-side-left';
const RIGHT_SIDE_CLASS = 'target-side-right';

/**
 * From <tr> diff row go up to <tbody> diff chunk.
 *
 * In Lit based diff there is a <gr-diff-row> element in between the two.
 */
export function fromRowToChunk(
  rowEl: HTMLElement
): HTMLTableSectionElement | undefined {
  const parent = rowEl.parentElement;
  if (!parent) return undefined;
  if (parent.tagName === 'TBODY') {
    return parent as HTMLTableSectionElement;
  }

  const grandParent = parent.parentElement;
  if (!grandParent) return undefined;
  if (grandParent.tagName === 'TBODY') {
    return grandParent as HTMLTableSectionElement;
  }

  return undefined;
}

/** A subset of the GrDiff API that the cursor is using. */
export interface GrDiffCursorable extends HTMLElement {
  isRangeSelected(): boolean;
  createRangeComment(): void;
  getCursorStops(): Stop[];
  path?: string;
}

export class GrDiffCursor implements GrDiffCursorApi {
  private preventAutoScrollOnManualScroll = false;

  set side(side: Side) {
    if (this.sideInternal === side) {
      return;
    }
    if (this.sideInternal && this.diffRowTR) {
      this.fireCursorMoved('line-cursor-moved-out');
    }
    this.sideInternal = side;
    this.updateSideClass();
    if (this.diffRowTR) {
      this.fireCursorMoved('line-cursor-moved-in');
    }
  }

  get side(): Side {
    return this.sideInternal;
  }

  private sideInternal = Side.RIGHT;

  set diffRowTR(diffRow: HTMLTableRowElement | undefined) {
    if (this.diffRowTRInternal) {
      this.diffRowTRInternal.classList.remove(
        LEFT_SIDE_CLASS,
        RIGHT_SIDE_CLASS
      );
      this.fireCursorMoved('line-cursor-moved-out');
    }
    this.diffRowTRInternal = diffRow;

    this.updateSideClass();
    if (this.diffRowTR) {
      this.fireCursorMoved('line-cursor-moved-in');
    }
  }

  /**
   * This is the current target of the diff cursor.
   */
  get diffRowTR(): HTMLTableRowElement | undefined {
    return this.diffRowTRInternal;
  }

  private diffRowTRInternal?: HTMLTableRowElement;

  private diffs: GrDiffCursorable[] = [];

  /**
   * If set, the cursor will attempt to move to the line number (instead of
   * the first chunk) the next time the diff renders. It is set back to null
   * when used. It should be only used if you want the line to be focused
   * after initialization of the component and page should scroll
   * to that position. This parameter should be set at most for one gr-diff
   * element in the page.
   */
  initialLineNumber: number | null = null;

  // visible for testing
  cursorManager = new GrCursorManager();

  private targetSubscription?: Subscription;

  constructor() {
    this.cursorManager.cursorTargetClass = 'target-row';
    this.cursorManager.scrollMode = ScrollMode.KEEP_VISIBLE;
    this.cursorManager.focusOnMove = true;

    window.addEventListener('scroll', this._boundHandleWindowScroll);
    this.targetSubscription = this.cursorManager.target$.subscribe(target => {
      this.diffRowTR = (target ?? undefined) as HTMLTableRowElement | undefined;
    });
  }

  dispose() {
    this.cursorManager.unsetCursor();
    if (this.targetSubscription) this.targetSubscription.unsubscribe();
    window.removeEventListener('scroll', this._boundHandleWindowScroll);
  }

  // Don't remove - used by clients embedding gr-diff outside of Gerrit.
  isAtStart() {
    return this.cursorManager.isAtStart();
  }

  // Don't remove - used by clients embedding gr-diff outside of Gerrit.
  isAtEnd() {
    return this.cursorManager.isAtEnd();
  }

  moveLeft() {
    this.side = Side.LEFT;
    if (this.isTargetBlank()) {
      this.moveUp();
    }
  }

  moveRight() {
    this.side = Side.RIGHT;
    if (this.isTargetBlank()) {
      this.moveUp();
    }
  }

  moveDown() {
    if (this._getViewMode() === DiffViewMode.SIDE_BY_SIDE) {
      return this.cursorManager.next({
        filter: (row: Element) => this._rowHasSide(row),
      });
    } else {
      return this.cursorManager.next();
    }
  }

  moveUp() {
    if (this._getViewMode() === DiffViewMode.SIDE_BY_SIDE) {
      return this.cursorManager.previous({
        filter: (row: Element) => this._rowHasSide(row),
      });
    } else {
      return this.cursorManager.previous();
    }
  }

  moveToVisibleArea() {
    if (this._getViewMode() === DiffViewMode.SIDE_BY_SIDE) {
      this.cursorManager.moveToVisibleArea((row: Element) =>
        this._rowHasSide(row)
      );
    } else {
      this.cursorManager.moveToVisibleArea();
    }
  }

  moveToNextChunk(clipToTop?: boolean): CursorMoveResult {
    const result = this.cursorManager.next({
      filter: (row: HTMLElement) => this._isFirstRowOfChunk(row),
      getTargetHeight: target => fromRowToChunk(target)?.scrollHeight || 0,
      clipToTop,
    });
    this._fixSide();
    return result;
  }

  moveToPreviousChunk(): CursorMoveResult {
    const result = this.cursorManager.previous({
      filter: (row: HTMLElement) => this._isFirstRowOfChunk(row),
    });
    this._fixSide();
    return result;
  }

  moveToNextCommentThread(): CursorMoveResult {
    if (this.isAtEnd()) {
      return CursorMoveResult.CLIPPED;
    }
    const result = this.cursorManager.next({
      filter: (row: HTMLElement) => this._rowHasThread(row),
    });
    this._fixSide();
    return result;
  }

  moveToPreviousCommentThread(): CursorMoveResult {
    const result = this.cursorManager.previous({
      filter: (row: HTMLElement) => this._rowHasThread(row),
    });
    this._fixSide();
    return result;
  }

  moveToLineNumber(
    number: LineNumber,
    side: Side,
    path?: string,
    intentionalMove?: boolean
  ) {
    const row = this._findRowByNumberAndFile(number, side, path);
    if (row) {
      this.side = side;
      this.cursorManager.setCursor(row, undefined, intentionalMove);
    }
  }

  /**
   * The target of the diff cursor is always a <tr> element. That is the first
   * direct child of a <gr-diff-row> element. We typically want to retrieve
   * the `GrDiffRow`, because it supplies methods that we can use without
   * making further assumptions about the internal DOM structure.
   */
  getTargetDiffRow(): GrDiffRow | undefined {
<<<<<<< PATCH SET (2afdb4 Simplify `isTargetBlank` in gr-diff-cursor)
    if (this.diffRowTR?.parentElement?.tagName !== 'GR-DIFF-ROW') {
      return undefined;
    }
    return this.diffRowTR.parentElement as GrDiffRow;
=======
    let el: HTMLElement | undefined = this.diffRowTR;
    while (el) {
      if (el.tagName === 'GR-DIFF-ROW') return el as GrDiffRow;
      el = el.parentElement ?? undefined;
    }
    return undefined;
>>>>>>> BASE      (4c4e81 Simplify gr-diff-cursor lookups)
  }

  getTargetLineNumber(): LineNumber | undefined {
    const diffRow = this.getTargetDiffRow();
    return diffRow?.lineNumber(this.side);
  }

  getTargetDiffElement(): GrDiff | undefined {
    if (!this.diffRowTR) return undefined;

    const hostOwner = this.diffRowTR.getRootNode() as ShadowRoot;
    if (hostOwner?.host?.tagName === 'GR-DIFF') {
      return hostOwner.host as GrDiff;
    }
    return undefined;
  }

  moveToFirstChunk() {
    this.cursorManager.moveToStart();
    if (this.diffRowTR && !this._isFirstRowOfChunk(this.diffRowTR)) {
      this.moveToNextChunk(true);
    } else {
      this._fixSide();
    }
  }

  moveToLastChunk() {
    this.cursorManager.moveToEnd();
    if (this.diffRowTR && !this._isFirstRowOfChunk(this.diffRowTR)) {
      this.moveToPreviousChunk();
    } else {
      this._fixSide();
    }
  }

  /**
   * Move the cursor either to initialLineNumber or the first chunk and
   * reset scroll behavior.
   *
   * This may grab the focus from the app.
   *
   * If you do not want to move the cursor or grab focus, and just want to
   * reset the scroll behavior, use reInitAndUpdateStops() instead.
   */
  reInitCursor() {
    this._updateStops();
    if (!this.diffRowTR) {
      // does not scroll during init unless requested
      this.cursorManager.scrollMode = this.initialLineNumber
        ? ScrollMode.KEEP_VISIBLE
        : ScrollMode.NEVER;
      if (this.initialLineNumber) {
        this.moveToLineNumber(this.initialLineNumber, this.side);
        this.initialLineNumber = null;
      } else {
        this.moveToFirstChunk();
      }
    }
    this.resetScrollMode();
  }

  resetScrollMode() {
    this.cursorManager.scrollMode = ScrollMode.KEEP_VISIBLE;
  }

  private _boundHandleWindowScroll = () => {
    if (this.preventAutoScrollOnManualScroll) {
      this.cursorManager.scrollMode = ScrollMode.NEVER;
      this.cursorManager.focusOnMove = false;
      this.preventAutoScrollOnManualScroll = false;
    }
  };

  reInitAndUpdateStops() {
    this.resetScrollMode();
    this._updateStops();
  }

  private boundHandleDiffLoadingChanged = () => {
    this._updateStops();
  };

  private _boundHandleDiffRenderStart = () => {
    this.preventAutoScrollOnManualScroll = true;
  };

  private _boundHandleDiffRenderContent = () => {
    this._updateStops();
    // When done rendering, turn focus on move and automatic scrolling back on
    this.cursorManager.focusOnMove = true;
    this.preventAutoScrollOnManualScroll = false;
  };

  private _boundHandleDiffLineSelected = (
    e: CustomEvent<LineSelectedEventDetail>
  ) => {
    this.moveToLineNumber(e.detail.number, e.detail.side, e.detail.path);
  };

  createCommentInPlace() {
    const diffWithRangeSelected = this.diffs.find(diff =>
      diff.isRangeSelected()
    );
    if (diffWithRangeSelected) {
      diffWithRangeSelected.createRangeComment();
    } else {
      const diffRow = this.getTargetDiffRow();
      const lineNumber = diffRow?.lineNumber(this.side);
      const diff = this.getTargetDiffElement();
      if (diff && lineNumber) {
        diff.addDraftAtLine(lineNumber, this.side);
      }
    }
  }

  _getViewMode() {
    if (!this.diffRowTR) {
      return null;
    }

    if (this.diffRowTR.classList.contains('side-by-side')) {
      return DiffViewMode.SIDE_BY_SIDE;
    } else {
      return DiffViewMode.UNIFIED;
    }
  }

  _rowHasSide(row: Element) {
    const selector =
      (this.side === Side.LEFT ? '.left' : '.right') + ' + .content';
    return !!row.querySelector(selector);
  }

  _isFirstRowOfChunk(row: HTMLElement) {
    const chunk = fromRowToChunk(row);
    if (!chunk) return false;

    const isInDeltaChunk = chunk.classList.contains('delta');
    if (!isInDeltaChunk) return false;

    const firstRow = chunk.querySelector('tr:not(.moveControls)');
    return firstRow === row;
  }

  _rowHasThread(row: HTMLElement): boolean {
    const slots = [
      ...row.querySelectorAll<HTMLSlotElement>('.thread-group > slot'),
    ];
    return slots.some(slot => slot.assignedElements().length > 0);
  }

  /**
   * If we jumped to a row where there is no content on the current side then
   * switch to the alternate side.
   */
  _fixSide() {
    if (
      this._getViewMode() === DiffViewMode.SIDE_BY_SIDE &&
      this.isTargetBlank()
    ) {
      this.side = this.side === Side.LEFT ? Side.RIGHT : Side.LEFT;
    }
  }

  private isTargetBlank() {
    const line = this.getTargetDiffRow()?.line(this.side);
    return line?.type === GrDiffLineType.BLANK;
  }

  private fireCursorMoved(
    event: 'line-cursor-moved-out' | 'line-cursor-moved-in'
  ) {
    const lineNum = this.getTargetLineNumber();
    const side = this.side;
    if (!lineNum) return;
    fire(this.diffRowTR, event, {lineNum, side});
  }

  private updateSideClass() {
    if (!this.diffRowTR) {
      return;
    }
    toggleClass(this.diffRowTR, LEFT_SIDE_CLASS, this.side === Side.LEFT);
    toggleClass(this.diffRowTR, RIGHT_SIDE_CLASS, this.side === Side.RIGHT);
  }

  _updateStops() {
    this.cursorManager.stops = this.diffs.reduce(
      (stops: Stop[], diff) => stops.concat(diff.getCursorStops()),
      []
    );
  }

  replaceDiffs(diffs: GrDiffCursorable[]) {
    for (const diff of this.diffs) {
      this.removeEventListeners(diff);
    }
    this.diffs = [];
    for (const diff of diffs) {
      this.addEventListeners(diff);
    }
    this.diffs.push(...diffs);
    this._updateStops();
  }

  unregisterDiff(diff: GrDiffCursorable) {
    // This can happen during destruction - just don't unregister then.
    if (!this.diffs) return;
    const i = this.diffs.indexOf(diff);
    if (i !== -1) {
      this.diffs.splice(i, 1);
    }
  }

  private removeEventListeners(diff: GrDiffCursorable) {
    diff.removeEventListener(
      'loading-changed',
      this.boundHandleDiffLoadingChanged
    );
    diff.removeEventListener('render-start', this._boundHandleDiffRenderStart);
    diff.removeEventListener(
      'render-content',
      this._boundHandleDiffRenderContent
    );
    diff.removeEventListener(
      'line-selected',
      this._boundHandleDiffLineSelected
    );
  }

  private addEventListeners(diff: GrDiffCursorable) {
    diff.addEventListener(
      'loading-changed',
      this.boundHandleDiffLoadingChanged
    );
    diff.addEventListener('render-start', this._boundHandleDiffRenderStart);
    diff.addEventListener('render-content', this._boundHandleDiffRenderContent);
    diff.addEventListener('line-selected', this._boundHandleDiffLineSelected);
  }

  _findRowByNumberAndFile(
    targetNumber: LineNumber,
    side: Side,
    path?: string
  ): HTMLElement | undefined {
    let stops: Array<HTMLElement | AbortStop>;
    if (path) {
      const diff = this.diffs.filter(diff => diff.path === path)[0];
      stops = diff.getCursorStops();
    } else {
      stops = this.cursorManager.stops;
    }
    // Sadly needed for type narrowing to understand that the result is always
    // targetable.
    const targetableStops: HTMLElement[] = stops.filter(isTargetable);
    const selector = `.lineNum.${side}[data-value="${targetNumber}"]`;
    return targetableStops.find(stop => stop.querySelector(selector));
  }
}
