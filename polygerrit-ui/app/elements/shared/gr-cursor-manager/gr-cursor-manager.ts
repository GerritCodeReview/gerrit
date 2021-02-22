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
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-cursor-manager_html';
import {ScrollMode} from '../../../constants/constants';
import {customElement, property, observe} from '@polymer/decorators';

export interface GrCursorManager {
  $: {};
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-cursor-manager': GrCursorManager;
  }
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

/**
 * Type guard and checker to check if a stop can be targeted.
 * Abort stops cannot be targeted.
 */
export function isTargetable(stop: Stop): stop is HTMLElement {
  return !(stop instanceof AbortStop);
}

@customElement('gr-cursor-manager')
export class GrCursorManager extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object, notify: true})
  target: HTMLElement | null = null;

  /**
   * The height of content intended to be included with the target.
   */
  @property({type: Number})
  _targetHeight: number | null = null;

  /**
   * The index of the current target (if any). -1 otherwise.
   */
  @property({type: Number, notify: true})
  index = -1;

  /**
   * The class to apply to the current target. Use null for no class.
   */
  @property({type: String})
  cursorTargetClass: string | null = null;

  /**
   * The scroll behavior for the cursor. Values are 'never' and
   * 'keep-visible'. 'keep-visible' will only scroll if the cursor is beyond
   * the viewport.
   * TODO (beckysiegel) figure out why it can be undefined
   *
   * @type {string|undefined}
   */
  @property({type: String})
  scrollMode: string = ScrollMode.NEVER;

  /**
   * When true, will call element.focus() during scrolling.
   */
  @property({type: Boolean})
  focusOnMove = false;

  @property({type: Array})
  stops: Stop[] = [];

  /** Only non-AbortStop stops. */
  get targetableStops(): HTMLElement[] {
    return this.stops.filter(isTargetable);
  }

  /** @override */
  detached() {
    super.detached();
    this.unsetCursor();
  }

  /**
   * Move the cursor forward. Clipped to the ends of the stop list.
   *
   * @param options.filter Will keep going and skip any stops for which this
   *    condition is not met.
   * @param options.getTargetHeight Optional function to calculate the
   *    height of the target's 'section'. The height of the target itself is
   *    sometimes different, used by the diff cursor.
   * @param options.clipToTop When none of the next indices match, move
   *     back to first instead of to last.
   * @return If a move was performed or why not.
   * @private
   */
  next(
    options: {
      filter?: (stop: HTMLElement) => boolean;
      getTargetHeight?: (target: HTMLElement) => number;
      clipToTop?: boolean;
    } = {}
  ): CursorMoveResult {
    return this._moveCursor(1, options);
  }

  previous(
    options: {
      filter?: (stop: HTMLElement) => boolean;
    } = {}
  ): CursorMoveResult {
    return this._moveCursor(-1, options);
  }

  /**
   * Move the cursor to the row which is the closest to the viewport center
   * in vertical direction.
   * The method uses IntersectionObservers API. If browser
   * doesn't support this API the method does nothing
   *
   * @param condition Optional condition. If a condition
   * is passed only stops which meet conditions are taken into account.
   */
  moveToVisibleArea(condition?: (el: Element) => boolean) {
    if (!this.stops || !this._isIntersectionObserverSupported()) {
      return;
    }
    const filteredStops = condition
      ? this.targetableStops.filter(condition)
      : this.targetableStops;
    const dims = this._getWindowDims();
    const windowCenter = Math.round(dims.innerHeight / 2);

    let closestToTheCenter: HTMLElement | null = null;
    let minDistanceToCenter: number | null = null;
    let unobservedCount = filteredStops.length;

    const observer = new IntersectionObserver(entries => {
      // This callback is called for the first time immediately.
      // Typically it gets all observed stops at once, but
      // sometimes can get them in several chunks.
      entries.forEach(entry => {
        observer.unobserve(entry.target);

        // In Edge it is recommended to use intersectionRatio instead of
        // isIntersecting.
        const isInsideViewport =
          entry.isIntersecting || entry.intersectionRatio > 0;
        if (!isInsideViewport) {
          return;
        }
        const center =
          entry.boundingClientRect.top +
          Math.round(entry.boundingClientRect.height / 2);
        const distanceToWindowCenter = Math.abs(center - windowCenter);
        if (
          minDistanceToCenter === null ||
          distanceToWindowCenter < minDistanceToCenter
        ) {
          // entry.target comes from the filteredStops array,
          // hence it is an HTMLElement
          closestToTheCenter = entry.target as HTMLElement;
          minDistanceToCenter = distanceToWindowCenter;
        }
      });
      unobservedCount -= entries.length;
      if (unobservedCount === 0 && closestToTheCenter) {
        // set cursor when all stops were observed.
        // In most cases the target is visible, so scroll is not
        // needed. But in rare cases the target can become invisible
        // at this point (due to some scrolling in window).
        // To avoid jumps set noScroll options.
        this.setCursor(closestToTheCenter, true);
      }
    });
    filteredStops.forEach(stop => {
      observer.observe(stop);
    });
  }

  _isIntersectionObserverSupported() {
    // The copy of this method exists in gr-app-element.js under the
    // name _isCursorManagerSupportMoveToVisibleLine
    // If you update this method, you must update gr-app-element.js
    // as well.
    return 'IntersectionObserver' in window;
  }

  /**
   * Set the cursor to an arbitrary stop - if the given element is not one of
   * the stops, unset the cursor.
   *
   * @param noScroll prevent any potential scrolling in response
   * setting the cursor.
   */
  setCursor(element: HTMLElement, noScroll?: boolean) {
    if (!this.targetableStops.includes(element)) {
      this.unsetCursor();
      return;
    }
    let behavior;
    if (noScroll) {
      behavior = this.scrollMode;
      this.scrollMode = ScrollMode.NEVER;
    }

    this.unsetCursor();
    this.target = element;
    this._updateIndex();
    this._decorateTarget();

    if (noScroll && behavior) {
      this.scrollMode = behavior;
    }
  }

  unsetCursor() {
    this._unDecorateTarget();
    this.index = -1;
    this.target = null;
    this._targetHeight = null;
  }

  /** Returns true if there are no stops, or we are on the first stop. */
  isAtStart(): boolean {
    return this.stops.length === 0 || this.index === 0;
  }

  /** Returns true if there are no stops, or we are on the last stop. */
  isAtEnd(): boolean {
    return this.stops.length === 0 || this.index === this.stops.length - 1;
  }

  moveToStart() {
    if (this.stops.length) {
      this.setCursorAtIndex(0);
    }
  }

  moveToEnd() {
    if (this.stops.length) {
      this.setCursorAtIndex(this.stops.length - 1);
    }
  }

  setCursorAtIndex(index: number, noScroll?: boolean) {
    const stop = this.stops[index];
    if (isTargetable(stop)) {
      this.setCursor(stop, noScroll);
    }
  }

  /**
   * Move the cursor forward or backward by delta. Clipped to the beginning or
   * end of stop list.
   *
   * @param delta either -1 or 1.
   * @param options.abort Will abort moving the cursor when encountering a
   *    stop for which this condition is met. Will abort even if the stop
   *    would have been filtered
   * @param options.filter Will keep going and skip any stops for which this
   *    condition is not met.
   * @param options.getTargetHeight Optional function to calculate the
   * height of the target's 'section'. The height of the target itself is
   * sometimes different, used by the diff cursor.
   * @param options.clipToTop When none of the next indices match, move
   * back to first instead of to last.
   * @return  If a move was performed or why not.
   * @private
   */
  _moveCursor(
    delta: number,
    {
      filter,
      getTargetHeight,
      clipToTop,
    }: {
      filter?: (stop: HTMLElement) => boolean;
      getTargetHeight?: (target: HTMLElement) => number;
      clipToTop?: boolean;
    } = {}
  ): CursorMoveResult {
    if (!this.stops.length) {
      this.unsetCursor();
      return CursorMoveResult.NO_STOPS;
    }

    let newIndex = this.index;
    // If the cursor is not yet set and we are going backwards, start at the
    // back.
    if (this.index === -1 && delta < 0) {
      newIndex = this.stops.length;
    }

    let clipped = false;
    let newStop: Stop;
    do {
      newIndex += delta;
      if (
        (delta > 0 && newIndex >= this.stops.length) ||
        (delta < 0 && newIndex < 0)
      ) {
        newIndex = delta < 0 || clipToTop ? 0 : this.stops.length - 1;
        newStop = this.stops[newIndex];
        clipped = true;
        break;
      }
      // Sadly needed so that type narrowing understands that this.stops[newIndex] is
      // targetable after I have checked that.
      newStop = this.stops[newIndex];
    } while (isTargetable(newStop) && filter && !filter(newStop));

    if (!isTargetable(newStop)) {
      return CursorMoveResult.ABORTED;
    }

    this._unDecorateTarget();

    this.index = newIndex;
    this.target = newStop;

    if (getTargetHeight) {
      this._targetHeight = getTargetHeight(this.target);
    } else {
      this._targetHeight = this.target.scrollHeight;
    }

    if (this.focusOnMove) {
      this.target.focus();
    }

    this._decorateTarget();

    return clipped ? CursorMoveResult.CLIPPED : CursorMoveResult.MOVED;
  }

  _decorateTarget() {
    if (this.target && this.cursorTargetClass) {
      this.target.classList.add(this.cursorTargetClass);
    }
  }

  _unDecorateTarget() {
    if (this.target && this.cursorTargetClass) {
      this.target.classList.remove(this.cursorTargetClass);
    }
  }

  @observe('stops')
  _updateIndex() {
    if (!this.target) {
      this.index = -1;
      return;
    }

    const newIndex = this.stops.indexOf(this.target);
    if (newIndex === -1) {
      this.unsetCursor();
    } else {
      this.index = newIndex;
    }
  }

  /**
   * Calculate where the element is relative to the window.
   *
   * @param target Target to scroll to.
   * @return Distance to top of the target.
   */
  _getTop(target: HTMLElement) {
    let top: number = target.offsetTop;
    for (
      let offsetParent = target.offsetParent;
      offsetParent;
      offsetParent = (offsetParent as HTMLElement).offsetParent
    ) {
      top += (offsetParent as HTMLElement).offsetTop;
    }
    return top;
  }

  _targetIsVisible(top: number) {
    const dims = this._getWindowDims();
    return (
      this.scrollMode === ScrollMode.KEEP_VISIBLE &&
      top > dims.pageYOffset &&
      top < dims.pageYOffset + dims.innerHeight
    );
  }

  _calculateScrollToValue(top: number, target: HTMLElement) {
    const dims = this._getWindowDims();
    return top + -dims.innerHeight / 3 + target.offsetHeight / 2;
  }

  @observe('target')
  _scrollToTarget() {
    if (!this.target || this.scrollMode === ScrollMode.NEVER) {
      return;
    }

    const dims = this._getWindowDims();
    const top = this._getTop(this.target);
    const bottomIsVisible = this._targetHeight
      ? this._targetIsVisible(top + this._targetHeight)
      : true;
    const scrollToValue = this._calculateScrollToValue(top, this.target);

    if (this._targetIsVisible(top)) {
      // Don't scroll if either the bottom is visible or if the position that
      // would get scrolled to is higher up than the current position. This
      // would cause less of the target content to be displayed than is
      // already.
      if (bottomIsVisible || scrollToValue < dims.scrollY) {
        return;
      }
    }

    // Scroll the element to the middle of the window. Dividing by a third
    // instead of half the inner height feels a bit better otherwise the
    // element appears to be below the center of the window even when it
    // isn't.
    window.scrollTo(dims.scrollX, scrollToValue);
  }

  _getWindowDims() {
    return {
      scrollX: window.scrollX,
      scrollY: window.scrollY,
      innerHeight: window.innerHeight,
      pageYOffset: window.pageYOffset,
    };
  }
}
