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

import {Subscription} from 'rxjs';
import '../../shared/gr-cursor-manager/gr-cursor-manager';
import {
  AbortStop,
  CursorMoveResult,
  GrCursorManager,
  Stop,
  isTargetable,
} from '../../shared/gr-cursor-manager/gr-cursor-manager';
import {afterNextRender} from '@polymer/polymer/lib/utils/render-status';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PolymerElement} from '@polymer/polymer';
import {html} from '@polymer/polymer/lib/utils/html-tag';
import {DiffViewMode, LineNumberEventDetail} from '../../../api/diff';
import {ScrollMode, Side} from '../../../constants/constants';
import {customElement, observe, property} from '@polymer/decorators';
import {GrDiffLineType} from '../gr-diff/gr-diff-line';
import {PolymerDomWrapper} from '../../../types/types';
import {GrDiffGroupType} from '../gr-diff/gr-diff-group';
import {GrDiff} from '../gr-diff/gr-diff';
import {toggleClass} from '../../../utils/dom-util';

type GrDiffRowType = GrDiffLineType | GrDiffGroupType;

const LEFT_SIDE_CLASS = 'target-side-left';
const RIGHT_SIDE_CLASS = 'target-side-right';

/** A subset of the GrDiff API that the cursor is using. */
export interface GrDiffCursorable extends HTMLElement {
  isRangeSelected(): boolean;
  createRangeComment(): void;
  getCursorStops(): Stop[];
  path?: string;
}

export class GrDiffCursor {
  private preventAutoScrollOnManualScroll = false;

  set side(side: Side) {
    this.sideInternal = side;
    this.updateSideClass();
  }

  get side(): Side {
    return this.sideInternal;
  }

  private sideInternal = Side.RIGHT;

  set diffRow(diffRow: HTMLElement | undefined) {
    if (this.diffRowInternal) {
      this.diffRowInternal.classList.remove(LEFT_SIDE_CLASS, RIGHT_SIDE_CLASS);
      this.fireCursorMoved(
        'line-cursor-moved-out',
        this.diffRowInternal,
        this.side
      );
    }
    this.diffRowInternal = diffRow;

    this.updateSideClass();
    if (this.diffRow) {
      this.fireCursorMoved('line-cursor-moved-in', this.diffRow, this.side);
    }
  }

  get diffRow(): HTMLElement | undefined {
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

  moveToNextChunk(clipToTop?: boolean): CursorMoveResult {
    const result = this.cursorManager.next({
      filter: (row: HTMLElement) => this._isFirstRowOfChunk(row),
      getTargetHeight: target =>
        (target?.parentNode as HTMLElement)?.scrollHeight || 0,
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
   */
  getAddress() {
    if (!this.diffRow) {
      return null;
    }
    // Get the line-number cell targeted by the cursor. If the mode is unified
    // then prefer the revision cell if available.
    return this.getAddressFor(this.diffRow, this.side);
  }

  private getAddressFor(diffRow: HTMLElement, side: Side) {
    let cell;
    if (this._getViewMode() === DiffViewMode.UNIFIED) {
      cell = diffRow.querySelector('.lineNum.right');
      if (!cell) {
        cell = diffRow.querySelector('.lineNum.left');
      }
    } else {
      cell = diffRow.querySelector('.lineNum.' + side);
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

  private fireCursorMoved(
    event: 'line-cursor-moved-out' | 'line-cursor-moved-in',
    row: HTMLElement,
    side: Side
  ) {
    const address = this.getAddressFor(row, side);
    if (address) {
      const {leftSide, number} = address;
      row.dispatchEvent(
        new CustomEvent<LineNumberEventDetail>(event, {
          detail: {
            lineNum: number,
            side: leftSide ? Side.LEFT : Side.RIGHT,
          },
          composed: true,
          bubbles: true,
        })
      );
    }
  }

  private updateSideClass() {
    if (!this.diffRow) {
      return;
    }
    toggleClass(this.diffRow, LEFT_SIDE_CLASS, this.side === Side.LEFT);
    toggleClass(this.diffRow, RIGHT_SIDE_CLASS, this.side === Side.RIGHT);
  }

  _updateSide(_: Side, oldSide: Side) {
    if (!this.diffRow) {
      return;
    }
    if (oldSide) {
      this.fireCursorMoved('line-cursor-moved-out', this.diffRow, oldSide);
    }
    this.updateSideClass();
    if (this.diffRow) {
      this.fireCursorMoved('line-cursor-moved-in', this.diffRow, this.side);
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

// TODO(oler): Remove this once clients have migrated to using GrDiffCursor service.
@customElement('gr-diff-cursor')
export class GrDiffCursorElement extends PolymerElement {
  static get template() {
    return html``;
  }

  @property({type: String, observer: '_sideChanged'})
  side: Side = Side.RIGHT;

  _sideChanged(side: Side) {
    this.cursor.side = side;
  }

  @property({type: Object}) diffs: GrDiff[] = [];

  @observe('diffs.splices')
  _diffsChanged() {
    if (this.diffs) {
      this.cursor.replaceDiffs(this.diffs);
    }
  }

  private cursor = new GrDiffCursor();

  /** @override */
  ready() {
    super.ready();
    afterNextRender(this, () => {
      /*
      This represents the diff cursor is ready for interaction coming from
      client components. It is more then Polymer "ready" lifecycle, as no
      "ready" events are automatically fired by Polymer, it means
      the cursor is completely interactable - in this case attached and
      painted on the page. We name it "ready" instead of "rendered" as the
      long-term goal is to make gr-diff-cursor a javascript class - not a DOM
      element with an actual lifecycle. This will be triggered only once
      per element.
      */
      this.dispatchEvent(
        new CustomEvent('ready', {
          composed: true,
          bubbles: false,
        })
      );
    });
  }

  /** @override */
  disconnectedCallback() {
    this.cursor.dispose();
    super.disconnectedCallback();
  }

  isAtStart = this.cursor.isAtStart.bind(this.cursor);

  isAtEnd = this.cursor.isAtEnd.bind(this.cursor);

  moveLeft = this.cursor.moveLeft.bind(this.cursor);

  moveRight = this.cursor.moveRight.bind(this.cursor);

  moveDown = this.cursor.moveDown.bind(this.cursor);

  moveUp = this.cursor.moveUp.bind(this.cursor);

  moveToNextChunk = this.cursor.moveToNextChunk.bind(this.cursor);

  moveToPreviousChunk = this.cursor.moveToPreviousChunk.bind(this.cursor);

  moveToNextCommentThread = this.cursor.moveToNextCommentThread.bind(
    this.cursor
  );

  moveToPreviousCommentThread = this.cursor.moveToPreviousCommentThread.bind(
    this.cursor
  );

  moveToLineNumber = this.cursor.moveToLineNumber.bind(this.cursor);

  moveToFirstChunk = this.cursor.moveToFirstChunk.bind(this.cursor);

  moveToLastChunk = this.cursor.moveToLastChunk.bind(this.cursor);

  reInit = this.cursor.resetScrollMode.bind(this.cursor);

  createCommentInPlace = this.cursor.createCommentInPlace.bind(this.cursor);
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-cursor': GrDiffCursorElement;
  }
}
