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

// Time in which pressing n key again after the toast navigates to next file
const NAVIGATE_TO_NEXT_FILE_TIMEOUT_MS = 5000;

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

  private _lastDisplayedNavigateToNextFileToast: number | null = null;

  @property({type: Array})
  stops: HTMLElement[] = [];

  /** @override */
  detached() {
    super.detached();
    this.unsetCursor();
  }

  /**
   * Move the cursor forward. Clipped to the ends of the stop list.
   *
   * @param condition Optional stop condition. If a condition
   *    is passed the cursor will continue to move in the specified direction
   *    until the condition is met.
   * @param getTargetHeight Optional function to calculate the
   *    height of the target's 'section'. The height of the target itself is
   *    sometimes different, used by the diff cursor.
   * @param clipToTop When none of the next indices match, move
   *     back to first instead of to last.
   * @param navigateToNextFile Navigate to next unreviewed file
   *     if user presses next on the last diff chunk
   * @private
   */

  next(
    condition?: Function,
    getTargetHeight?: (target: HTMLElement) => number,
    clipToTop?: boolean,
    navigateToNextFile?: boolean
  ) {
    this._moveCursor(
      1,
      condition,
      getTargetHeight,
      clipToTop,
      navigateToNextFile
    );
  }

  previous(condition?: Function) {
    this._moveCursor(-1, condition);
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
    const filteredStops = condition ? this.stops.filter(condition) : this.stops;
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
   * Set the cursor to an arbitrary element.
   *
   * @param noScroll prevent any potential scrolling in response
   * setting the cursor.
   */
  setCursor(element: HTMLElement, noScroll?: boolean) {
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

  isAtStart() {
    return this.index === 0;
  }

  isAtEnd() {
    return this.index === this.stops.length - 1;
  }

  moveToStart() {
    if (this.stops.length) {
      this.setCursor(this.stops[0]);
    }
  }

  moveToEnd() {
    if (this.stops.length) {
      this.setCursor(this.stops[this.stops.length - 1]);
    }
  }

  setCursorAtIndex(index: number, noScroll?: boolean) {
    this.setCursor(this.stops[index], noScroll);
  }

  /**
   * Move the cursor forward or backward by delta. Clipped to the beginning or
   * end of stop list.
   *
   * @param delta either -1 or 1.
   * @param condition Optional stop condition. If a condition
   * is passed the cursor will continue to move in the specified direction
   * until the condition is met.
   * @param getTargetHeight Optional function to calculate the
   * height of the target's 'section'. The height of the target itself is
   * sometimes different, used by the diff cursor.
   * @param clipToTop When none of the next indices match, move
   * back to first instead of to last.
   * @param navigateToNextFile Navigate to next unreviewed file
   * if user presses next on the last diff chunk
   * @private
   */
  _moveCursor(
    delta: number,
    condition?: Function,
    getTargetHeight?: (target: HTMLElement) => number,
    clipToTop?: boolean,
    navigateToNextFile?: boolean
  ) {
    if (!this.stops.length) {
      this.unsetCursor();
      return;
    }

    this._unDecorateTarget();

    const newIndex = this._getNextindex(delta, condition, clipToTop);

    let newTarget = null;
    if (newIndex !== -1) {
      newTarget = this.stops[newIndex];
    }

    /*
     * If user presses n on the last diff chunk, show a toast informing user
     * that pressing n again will navigate them to next unreviewed file.
     * If click happens within the time limit, then navigate to next file
     */
    if (navigateToNextFile && this.index === newIndex) {
      if (newIndex === this.stops.length - 1) {
        if (
          this._lastDisplayedNavigateToNextFileToast &&
          Date.now() - this._lastDisplayedNavigateToNextFileToast <=
            NAVIGATE_TO_NEXT_FILE_TIMEOUT_MS
        ) {
          // reset for next file
          this._lastDisplayedNavigateToNextFileToast = null;
          this.dispatchEvent(
            new CustomEvent('navigate-to-next-unreviewed-file', {
              composed: true,
              bubbles: true,
            })
          );
          return;
        }
        this._lastDisplayedNavigateToNextFileToast = Date.now();
        this.dispatchEvent(
          new CustomEvent('show-alert', {
            detail: {
              message: 'Press n again to navigate to next unreviewed file',
            },
            composed: true,
            bubbles: true,
          })
        );
        return;
      }
    }

    this.index = newIndex;
    this.target = newTarget as HTMLElement;

    if (!newTarget) {
      return;
    }

    if (getTargetHeight) {
      this._targetHeight = getTargetHeight(newTarget);
    } else {
      this._targetHeight = newTarget.scrollHeight;
    }

    if (this.focusOnMove) {
      newTarget.focus();
    }

    this._decorateTarget();
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

  /**
   * Get the next stop index indicated by the delta direction.
   *
   * @param delta either -1 or 1.
   * @param condition Optional stop condition.
   * @param clipToTop When none of the next indices match, move
   * back to first instead of to last.
   * @return the new index.
   * @private
   */
  _getNextindex(delta: number, condition?: Function, clipToTop?: boolean) {
    if (!this.stops.length) {
      return -1;
    }
    let newIndex = this.index;
    // If the cursor is not yet set and we are going backwards, start at the
    // back.
    if (this.index === -1 && delta < 0) {
      newIndex = this.stops.length;
    }
    do {
      newIndex = newIndex + delta;
    } while (
      (delta > 0 || newIndex > 0) &&
      (delta < 0 || newIndex < this.stops.length - 1) &&
      condition &&
      !condition(this.stops[newIndex])
    );

    newIndex = Math.max(0, Math.min(this.stops.length - 1, newIndex));

    // If we failed to satisfy the condition:
    if (condition && !condition(this.stops[newIndex])) {
      if (delta < 0 || clipToTop) {
        return 0;
      } else if (delta > 0) {
        return this.stops.length - 1;
      }
      return this.index;
    }

    return newIndex;
  }

  @observe('stops')
  _updateIndex() {
    if (!this.target) {
      this.index = -1;
      return;
    }

    const newIndex = Array.prototype.indexOf.call(this.stops, this.target);
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

  /**
   * @return
   */
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
