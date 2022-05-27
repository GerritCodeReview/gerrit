/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {BehaviorSubject} from 'rxjs';
import {AbortStop, CursorMoveResult, Stop} from '../../../api/core';
import {ScrollMode} from '../../../constants/constants';

/**
 * Type guard and checker to check if a stop can be targeted.
 * Abort stops cannot be targeted.
 */
export function isTargetable(stop: Stop): stop is HTMLElement {
  return !(stop instanceof AbortStop);
}

export class GrCursorManager {
  get target(): HTMLElement | null {
    return this.targetSubject.getValue();
  }

  set target(target: HTMLElement | null) {
    this.targetSubject.next(target);
    this._scrollToTarget();
  }

  private targetSubject = new BehaviorSubject<HTMLElement | null>(null);

  target$ = this.targetSubject.asObservable();

  /**
   * The height of content intended to be included with the target.
   */
  _targetHeight: number | null = null;

  /**
   * The index of the current target (if any). -1 otherwise.
   */
  index = -1;

  /**
   * The class to apply to the current target. Use null for no class.
   */
  cursorTargetClass: string | null = null;

  /**
   * The scroll behavior for the cursor. Values are 'never' and
   * 'keep-visible'. 'keep-visible' will only scroll if the cursor is beyond
   * the viewport.
   * TODO (beckysiegel) figure out why it can be undefined
   *
   * @type {string|undefined}
   */
  scrollMode: string = ScrollMode.NEVER;

  /**
   * When true, will call element.focus() during scrolling.
   */
  focusOnMove = false;

  set stops(stops: Stop[]) {
    this.stopsInternal = stops;
    this._updateIndex();
  }

  get stops(): Stop[] {
    return this.stopsInternal;
  }

  private stopsInternal: Stop[] = [];

  /** Only non-AbortStop stops. */
  get targetableStops(): HTMLElement[] {
    return this.stops.filter(isTargetable);
  }

  /**
   * Move the cursor forward. Clipped to the end of the stop list.
   *
   * @param options.filter Skips any stops for which filter returns false.
   * @param options.getTargetHeight Optional function to calculate the
   *    height of the target's 'section'. The height of the target itself is
   *    sometimes different, used by the diff cursor.
   * @param options.clipToTop When none of the next indices match, move
   *     back to first instead of to last.
   * @param options.circular When on last element, you get to first element.
   * @return If a move was performed or why not.
   */
  next(
    options: {
      filter?: (stop: HTMLElement) => boolean;
      getTargetHeight?: (target: HTMLElement) => number;
      clipToTop?: boolean;
      circular?: boolean;
    } = {}
  ): CursorMoveResult {
    return this._moveCursor(1, options);
  }

  /**
   * Move the cursor backward. Clipped to the beginning of stop list.
   *
   * @param options.filter Skips any stops for which filter returns false.
   * @param options.getTargetHeight Optional function to calculate the
   *    height of the target's 'section'. The height of the target itself is
   *    sometimes different, used by the diff cursor.
   * @param options.clipToTop When none of the next indices match, move
   * back to first instead of to last.
   * @param options.circular When on first element, you get to last element.
   * @return  If a move was performed or why not.
   */
  previous(
    options: {
      filter?: (stop: HTMLElement) => boolean;
      circular?: boolean;
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
   * @param filter Skips any stops for which filter returns false.
   */
  async moveToVisibleArea(filter?: (el: Element) => boolean) {
    const centerMostStop = await this.getCenterMostStop(filter);
    // In most cases the target is visible, so scroll is not
    // needed. But in rare cases the target can become invisible
    // at this point (due to some scrolling in window).
    // To avoid jumps set noScroll options.
    if (centerMostStop) {
      this.setCursor(centerMostStop, true);
    }
  }

  private async getCenterMostStop(
    filter?: (el: Element) => boolean
  ): Promise<HTMLElement | undefined> {
    const visibleEntries = await this.getVisibleEntries(filter);
    const windowCenter = Math.round(window.innerHeight / 2);

    let centerMostStop: HTMLElement | undefined = undefined;
    let minDistanceToCenter = Number.MAX_VALUE;

    for (const entry of visibleEntries) {
      // We are just using the entries here, because entry.boundingClientRect
      // is already computed, but entry.target.getBoundingClientRect() should
      // actually yield the same result.
      const center =
        entry.boundingClientRect.top +
        Math.round(entry.boundingClientRect.height / 2);
      const distanceToWindowCenter = Math.abs(center - windowCenter);
      if (distanceToWindowCenter < minDistanceToCenter) {
        // entry.target comes from the filteredStops array,
        // hence it is an HTMLElement
        centerMostStop = entry.target as HTMLElement;
        minDistanceToCenter = distanceToWindowCenter;
      }
    }
    return centerMostStop;
  }

  private async getVisibleEntries(
    filter?: (el: Element) => boolean
  ): Promise<IntersectionObserverEntry[]> {
    if (!this.stops) {
      return [];
    }
    const filteredStops = filter
      ? this.targetableStops.filter(filter)
      : this.targetableStops;
    return new Promise(resolve => {
      let unobservedCount = filteredStops.length;
      const visibleEntries: IntersectionObserverEntry[] = [];
      const observer = new IntersectionObserver(entries => {
        visibleEntries.push(
          ...entries
            // In Edge it is recommended to use intersectionRatio instead of
            // isIntersecting.
            .filter(
              entry => entry.isIntersecting || entry.intersectionRatio > 0
            )
        );

        // This callback is called for the first time immediately.
        // Typically it gets all observed stops at once, but
        // sometimes can get them in several chunks.
        for (const entry of entries) {
          observer.unobserve(entry.target);
        }
        unobservedCount -= entries.length;
        if (unobservedCount === 0) {
          resolve(visibleEntries);
        }
      });
      for (const stop of filteredStops) {
        observer.observe(stop);
      }
    });
  }

  /**
   * Set the cursor to an arbitrary stop - if the given element is not one of
   * the stops, unset the cursor.
   *
   * @param noScroll prevent any potential scrolling in response
   * setting the cursor.
   * @param applyFocus indicates if it should try to focus after move operation
   * (e.g. focusOnMove).
   */
  setCursor(element: HTMLElement, noScroll?: boolean, applyFocus?: boolean) {
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

    if (applyFocus) {
      this._focusAfterMove();
    }
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

  _moveCursor(
    delta: number,
    {
      filter,
      getTargetHeight,
      clipToTop,
      circular,
    }: {
      filter?: (stop: HTMLElement) => boolean;
      getTargetHeight?: (target: HTMLElement) => number;
      clipToTop?: boolean;
      circular?: boolean;
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
        newIndex =
          (delta < 0 && !circular) || (delta > 0 && circular) || clipToTop
            ? 0
            : this.stops.length - 1;
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

    this._decorateTarget();
    this._focusAfterMove();
    return clipped ? CursorMoveResult.CLIPPED : CursorMoveResult.MOVED;
  }

  _focusAfterMove() {
    if (this.focusOnMove) {
      this.target?.focus();
    }
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
    return (
      this.scrollMode === ScrollMode.KEEP_VISIBLE &&
      top > window.pageYOffset &&
      top < window.pageYOffset + window.innerHeight
    );
  }

  _calculateScrollToValue(top: number, target: HTMLElement) {
    return top + -window.innerHeight / 3 + target.offsetHeight / 2;
  }

  _scrollToTarget() {
    if (!this.target || this.scrollMode === ScrollMode.NEVER) {
      return;
    }

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
      if (bottomIsVisible || scrollToValue < window.scrollY) {
        return;
      }
    }

    // Scroll the element to the middle of the window. Dividing by a third
    // instead of half the inner height feels a bit better otherwise the
    // element appears to be below the center of the window even when it
    // isn't.
    window.scrollTo(window.scrollX, scrollToValue);
  }
}
