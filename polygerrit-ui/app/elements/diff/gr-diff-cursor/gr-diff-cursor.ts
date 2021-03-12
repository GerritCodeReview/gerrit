/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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

import '../../shared/gr-cursor-manager/gr-cursor-manager';
import {
  AbortStop,
  CursorMoveResult,
  GrCursorManager,
  Stop,
  isTargetable,
} from '../../shared/gr-cursor-manager/gr-cursor-manager';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom';
import {DiffViewMode} from '../../../api/diff';
import {ScrollMode, Side} from '../../../constants/constants';
import {GrDiffLineType} from '../gr-diff/gr-diff-line';
import {PolymerDomWrapper} from '../../../types/types';
import {GrDiffGroupType} from '../gr-diff/gr-diff-group';
import {GrDiff} from '../gr-diff/gr-diff';
import {fireAlert} from '../../../utils/event-util';
import {Subject, Subscription} from 'rxjs';

type GrDiffRowType = GrDiffLineType | GrDiffGroupType;

const LEFT_SIDE_CLASS = 'target-side-left';
const RIGHT_SIDE_CLASS = 'target-side-right';

// Time in which pressing n key again after the toast navigates to next file
const NAVIGATE_TO_NEXT_FILE_TIMEOUT_MS = 5000;

/** A subset of the GrDiff API that the cursor is using. */
export interface GrDiffCursorable extends HTMLElement {
  isRangeSelected(): boolean;
  createRangeComment(): void;
  getCursorStops(): Stop[];
  path?: string;
}

export class GrDiffCursor {
  private preventAutoScrollOnManualScroll = false;

  private lastDisplayedNavigateToNextFileToast: number | null = null;

  set side(side: Side) {
    this.sideInternal = side;
    this._updateSideClass();
  }

  get side(): Side {
    return this.sideInternal;
  }

  private sideInternal = Side.RIGHT;

  set diffRow(diffRow: HTMLElement|undefined) {
    if (this.diffRowInternal) {
      this.diffRowInternal.classList.remove(LEFT_SIDE_CLASS, RIGHT_SIDE_CLASS);
    }
    this.diffRowInternal = diffRow;
    this._updateSideClass();
  }

  get diffRow(): HTMLElement|undefined {
    return this.diffRowInternal;
  }

  private diffRowInternal?: HTMLElement;

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

  private cursorManager = new GrCursorManager();

  private targetSubscription?: Subscription;

  constructor() {
    this.cursorManager.cursorTargetClass = 'target-row';
    this.cursorManager.scrollMode = ScrollMode.KEEP_VISIBLE;
    this.cursorManager.focusOnMove = true;

    window.addEventListener('scroll', this._boundHandleWindowScroll);
    this.targetSubscription = this.cursorManager.target$.subscribe(target => {
      this.diffRow = target || undefined;
    });
  }

  // DO NOT SUBMIT: Call this from the parents
  dispose() {
    if (this.targetSubscription) this.targetSubscription.unsubscribe();
    window.removeEventListener('scroll', this._boundHandleWindowScroll);
    this.cursorManager.unsetCursor();
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
    if (this._isTargetBlank()) {
      this.moveUp();
    }
  }

  moveRight() {
    this.side = Side.RIGHT;
    if (this._isTargetBlank()) {
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

  private nextUnreviewedFileSubject = new Subject<void>();

  nextUnreviewedFile$ = this.nextUnreviewedFileSubject.asObservable();

  private nextFileWithCommentsSubject = new Subject<void>();

  nextFileWithComments$ = this.nextFileWithCommentsSubject.asObservable();

  moveToNextChunk(
    clipToTop?: boolean,
    navigateToNextFile?: boolean
  ): CursorMoveResult {
    const result = this.cursorManager.next({
      filter: (row: HTMLElement) => this._isFirstRowOfChunk(row),
      getTargetHeight: target =>
        (target?.parentNode as HTMLElement)?.scrollHeight || 0,
      clipToTop,
    });
    /*
     * If user presses n on the last diff chunk, show a toast informing user
     * that pressing n again will navigate them to next unreviewed file.
     * If click happens within the time limit, then navigate to next file
     */
    if (
      navigateToNextFile &&
      result === CursorMoveResult.CLIPPED &&
      this.isAtEnd()
    ) {
      if (
        this.lastDisplayedNavigateToNextFileToast &&
        Date.now() - this.lastDisplayedNavigateToNextFileToast <=
          NAVIGATE_TO_NEXT_FILE_TIMEOUT_MS
      ) {
        // reset for next file
        this.lastDisplayedNavigateToNextFileToast = null;
        this.nextUnreviewedFileSubject.next();
      } else {
        this.lastDisplayedNavigateToNextFileToast = Date.now();
        fireAlert(
          document,
          'Press n again to navigate to next unreviewed file'
        );
      }
    }

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

  moveToNextCommentThread(): CursorMoveResult | undefined {
    if (this.isAtEnd()) {
      this.nextFileWithCommentsSubject.next();
      return;
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

  moveToLineNumber(number: number, side: Side, path?: string) {
    const row = this._findRowByNumberAndFile(number, side, path);
    if (row) {
      this.side = side;
      this.cursorManager.setCursor(row);
    }
  }

  /**
   * Get the line number element targeted by the cursor row and side.
   */
  getTargetLineElement(): HTMLElement | null {
    let lineElSelector = '.lineNum';

    if (!this.diffRow) {
      return null;
    }

    if (this._getViewMode() === DiffViewMode.SIDE_BY_SIDE) {
      lineElSelector += this.side === Side.LEFT ? '.left' : '.right';
    }

    return this.diffRow.querySelector(lineElSelector);
  }

  getTargetDiffElement(): GrDiff | null {
    if (!this.diffRow) return null;

    const hostOwner = (dom(this.diffRow) as PolymerDomWrapper).getOwnerRoot();
    if (hostOwner?.host?.tagName === 'GR-DIFF') {
      return hostOwner.host as GrDiff;
    }
    return null;
  }

  moveToFirstChunk() {
    this.cursorManager.moveToStart();
    if (this.diffRow && !this._isFirstRowOfChunk(this.diffRow)) {
      this.moveToNextChunk(true);
    } else {
      this._fixSide();
    }
  }

  moveToLastChunk() {
    this.cursorManager.moveToEnd();
    if (this.diffRow && !this._isFirstRowOfChunk(this.diffRow)) {
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
   * reset the scroll behavior, use reInit() instead.
   */
  reInitCursor() {
    if (!this.diffRow) {
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
    this.reInit();
  }

  reInit() {
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
    this.reInit();
    this._updateStops();
  }

  handleDiffUpdate() {
    this._updateStops();
    this.reInitCursor();
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

  private _boundHandleDiffLineSelected = (event: Event) => {
    const customEvent = event as CustomEvent;
    this.moveToLineNumber(
      customEvent.detail.number,
      customEvent.detail.side,
      customEvent.detail.path
    );
  };

  createCommentInPlace() {
    const diffWithRangeSelected = this.diffs.find(diff =>
      diff.isRangeSelected()
    );
    if (diffWithRangeSelected) {
      diffWithRangeSelected.createRangeComment();
    } else {
      const line = this.getTargetLineElement();
      const diff = this.getTargetDiffElement();
      if (diff && line) {
        diff.addDraftAtLine(line);
      }
    }
  }

  /**
   * Get an object describing the location of the cursor. Such as
   * {leftSide: false, number: 123} for line 123 of the revision, or
   * {leftSide: true, number: 321} for line 321 of the base patch.
   * Returns null if an address is not available.
   *
   */
  getAddress() {
    if (!this.diffRow) {
      return null;
    }

    // Get the line-number cell targeted by the cursor. If the mode is unified
    // then prefer the revision cell if available.
    let cell;
    if (this._getViewMode() === DiffViewMode.UNIFIED) {
      cell = this.diffRow.querySelector('.lineNum.right');
      if (!cell) {
        cell = this.diffRow.querySelector('.lineNum.left');
      }
    } else {
      cell = this.diffRow.querySelector('.lineNum.' + this.side);
    }
    if (!cell) {
      return null;
    }

    const number = cell.getAttribute('data-value');
    if (!number || number === 'FILE') {
      return null;
    }

    return {
      leftSide: cell.matches('.left'),
      number: Number(number),
    };
  }

  _getViewMode() {
    if (!this.diffRow) {
      return null;
    }

    if (this.diffRow.classList.contains('side-by-side')) {
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
    const parentClassList = (row.parentNode as HTMLElement).classList;
    const isInChunk =
      parentClassList.contains('section') && parentClassList.contains('delta');
    const previousRow = row.previousSibling as HTMLElement;
    const firstContentRow =
      !previousRow || previousRow.classList.contains('moveControls');
    return isInChunk && firstContentRow;
  }

  _rowHasThread(row: HTMLElement): boolean {
    return !!row.querySelector('.thread-group');
  }

  /**
   * If we jumped to a row where there is no content on the current side then
   * switch to the alternate side.
   */
  _fixSide() {
    if (
      this._getViewMode() === DiffViewMode.SIDE_BY_SIDE &&
      this._isTargetBlank()
    ) {
      this.side = this.side === Side.LEFT ? Side.RIGHT : Side.LEFT;
    }
  }

  _isTargetBlank() {
    if (!this.diffRow) {
      return false;
    }

    const actions = this._getActionsForRow();
    return (
      (this.side === Side.LEFT && !actions.left) ||
      (this.side === Side.RIGHT && !actions.right)
    );
  }

  _updateSideClass() {
    if (!this.diffRow) {
      return;
    }

    this.toggleClass(LEFT_SIDE_CLASS, this.side === Side.LEFT, this.diffRow);
    this.toggleClass(RIGHT_SIDE_CLASS, this.side === Side.RIGHT, this.diffRow);
  }

  private toggleClass(className: string, condition: boolean, el: HTMLElement) {
    if (condition) {
      el.classList.add(className);
    } else {
      el.classList.remove(className);
    }
  }

  _isActionType(type: GrDiffRowType) {
    return (
      type !== GrDiffLineType.BLANK && type !== GrDiffGroupType.CONTEXT_CONTROL
    );
  }

  _getActionsForRow() {
    const actions = {left: false, right: false};
    if (this.diffRow) {
      actions.left = this._isActionType(
        this.diffRow.getAttribute('left-type') as GrDiffRowType
      );
      actions.right = this._isActionType(
        this.diffRow.getAttribute('right-type') as GrDiffRowType
      );
    }
    return actions;
  }

  _updateStops() {
    this.cursorManager.stops = this.diffs.reduce(
      (stops: Stop[], diff) => stops.concat(diff.getCursorStops()),
      []
    );
  }

  clearDiffs() {
    for (const diff of this.diffs) {
      this.removeEventListeners(diff);
    }
    this.diffs = [];
    this._updateStops();
  }

  registerDiffs(diffs: GrDiffCursorable[]) {
    for (const diff of diffs) {
      this.addEventListeners(diff);
    }
    this.diffs.push(...diffs);
    this._updateStops();
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
    targetNumber: number,
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
