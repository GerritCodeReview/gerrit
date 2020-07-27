/**
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
import '../../../styles/shared-styles.js';
import {flush, dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {Debouncer} from '@polymer/polymer/lib/utils/debounce.js';
import {timeOut} from '@polymer/polymer/lib/utils/async.js';
import {getRootElement} from '../../../scripts/rootElement.js';

const HOVER_CLASS = 'hovered';
const HIDE_CLASS = 'hide';

/**
 * How long should be wait before showing the hovercard when the user hovers
 * over the element?
 */
const SHOW_DELAY_MS = 500;

/**
 * How long should be wait before hiding the hovercard when the user moves from
 * target to the hovercard.
 *
 * Note: this should be lower than SHOW_DELAY_MS to avoid flickering.
 */
const HIDE_DELAY_MS = 300;

/**
 * The mixin for gr-hovercard-behavior.
 *
 * @example
 *
 * // LegacyElementMixin is still needed to support the old lifecycles
 * // TODO: Replace old life cycles with new ones.
 *
 * class YourComponent extends hovercardBehaviorMixin(
 *  LegacyElementMixin(PolymerElement)
 * ) {
 *   static get is() { return ''; }
 *   static get template() { return html``; }
 * }
 *
 * customElements.define(GrHovercard.is, GrHovercard);
 *
 * @see gr-hovercard.js
 *
 * // following annotations are required for polylint
 * @polymer
 * @mixinFunction
 */
export const hovercardBehaviorMixin = superClass => class extends superClass {
  static get properties() {
    return {
      /**
       * @type {?}
       */
      _target: Object,

      /**
       * Determines whether or not the hovercard is visible.
       *
       * @type {boolean}
       */
      _isShowing: {
        type: Boolean,
        value: false,
      },
      /**
       * The `id` of the element that the hovercard is anchored to.
       *
       * @type {string}
       */
      for: {
        type: String,
        observer: '_forChanged',
      },

      /**
       * The spacing between the top of the hovercard and the element it is
       * anchored to.
       *
       * @type {number}
       */
      offset: {
        type: Number,
        value: 14,
      },

      /**
       * Positions the hovercard to the top, right, bottom, left, bottom-left,
       * bottom-right, top-left, or top-right of its content.
       *
       * @type {string}
       */
      position: {
        type: String,
        value: 'right',
      },

      container: Object,
      /**
       * ID for the container element.
       *
       * @type {string}
       */
      containerId: {
        type: String,
        value: 'gr-hovercard-container',
      },
    };
  }

  /** @override */
  attached() {
    super.attached();
    if (!this._target) { this._target = this.target; }
    this.listen(this._target, 'mouseenter', 'debounceShow');
    this.listen(this._target, 'focus', 'debounceShow');
    this.listen(this._target, 'mouseleave', 'debounceHide');
    this.listen(this._target, 'blur', 'debounceHide');

    // when click, dismiss immediately
    this.listen(this._target, 'click', 'hide');

    // cancel the hide if mouse now hover on the hovercard
    this.addEventListener('mouseenter', () => {
      this.cancelHideDebouncer();
    });
    // when leave hovercard, hide it immediately
    this.addEventListener('mouseleave',
        e => {
          this.cancelHideDebouncer();
          this.hide();
        });
  }

  /** @override */
  ready() {
    super.ready();
    // First, check to see if the container has already been created.
    this.container = getRootElement()
        .querySelector('#' + this.containerId);

    if (this.container) { return; }

    // If it does not exist, create and initialize the hovercard container.
    this.container = document.createElement('div');
    this.container.setAttribute('id', this.containerId);
    getRootElement().appendChild(this.container);
  }

  removeListeners() {
    this.unlisten(this._target, 'mouseenter', 'debounceShow');
    this.unlisten(this._target, 'focus', 'debounceShow');
    this.unlisten(this._target, 'mouseleave', 'debounceHide');
    this.unlisten(this._target, 'blur', 'debounceHide');
    this.unlisten(this._target, 'click', 'hide');
  }

  debounceHide() {
    this._isScheduledToHide = true;
    this._hideDebouncer = Debouncer.debounce(
        this._hideDebouncer,
        timeOut.after(HIDE_DELAY_MS),
        () => {
          // This happens when hide immediately through click or mouse leave
          // on the hovercard
          if (!this._isScheduledToHide) return;
          this.hide();
          this._isScheduledToHide = false;
        });
  }

  cancelHideDebouncer() {
    if (this._hideDebouncer) {
      this._hideDebouncer.cancel();
    }
  }

  /**
   * Hovercard elements are created outside of <gr-app>, so if you want to fire
   * events, then you probably want to do that through the target element.
   */
  dispatchEventThroughTarget(eventName) {
    this._target.dispatchEvent(new CustomEvent(eventName, {
      bubbles: true,
      composed: true,
    }));
  }

  /**
   * Returns the target element that the hovercard is anchored to (the `id` of
   * the `for` property).
   *
   * @type {HTMLElement}
   */
  get target() {
    const parentNode = dom(this).parentNode;
    // If the parentNode is a document fragment, then we need to use the host.
    const ownerRoot = dom(this).getOwnerRoot();
    let target;
    if (this.for) {
      target = dom(ownerRoot).querySelector('#' + this.for);
    } else {
      target = parentNode.nodeType == Node.DOCUMENT_FRAGMENT_NODE ?
        ownerRoot.host :
        parentNode;
    }
    return target;
  }

  /**
   * Hides/closes the hovercard. This occurs when the user triggers the
   * `mouseleave` event on the hovercard's `target` element (as long as the
   * user is not hovering over the hovercard).
   *
   * @param {Event} opt_e DOM Event (e.g. `mouseleave` event)
   */
  hide(opt_e) {
    this._isScheduledToShow = false;
    this._isScheduledToHide = false;
    if (!this._isShowing) {
      return;
    }

    // If the user is now hovering over the hovercard or the user is returning
    // from the hovercard but now hovering over the target (to stop an annoying
    // flicker effect), just return.
    if (opt_e) {
      if (opt_e.relatedTarget === this ||
          (opt_e.target === this && opt_e.relatedTarget === this._target)) {
        return;
      }
    }

    // Mark that the hovercard is not visible and do not allow focusing
    this._isShowing = false;

    // Clear styles in preparation for the next time we need to show the card
    this.classList.remove(HOVER_CLASS);

    // Reset and remove the hovercard from the DOM
    this.style.cssText = '';
    this.$.container.setAttribute('tabindex', -1);

    // Remove the hovercard from the container, given that it is still a child
    // of the container.
    if (this.container.contains(this)) {
      this.container.removeChild(this);
    }
  }

  /**
   * Shows/opens the hovercard with a fixed delay.
   */
  debounceShow() {
    this.debounceShowBy(SHOW_DELAY_MS);
  }

  /**
   * Shows/opens the hovercard with the given delay.
   */
  debounceShowBy(delayMs) {
    this.cancelHideDebouncer();
    if (this._isShowing || this._isScheduledToShow) return;
    this._isScheduledToShow = true;
    this._showDebouncer = Debouncer.debounce(
        this._showDebouncer,
        timeOut.after(delayMs),
        () => {
          // This happens when the mouse leaves the target before the delay is over.
          if (!this._isScheduledToShow) return;
          this._isScheduledToShow = false;
          this.show();
        });
  }

  /**
   * Shows/opens the hovercard. This occurs when the user triggers the
   * `mousenter` event on the hovercard's `target` element.
   */
  show() {
    if (this._isShowing) {
      return;
    }

    // Mark that the hovercard is now visible
    this._isShowing = true;
    this.setAttribute('tabindex', 0);

    // Add it to the DOM and calculate its position
    this.container.appendChild(this);
    // We temporarily hide the hovercard until we have found the correct
    // position for it.
    this.classList.add(HIDE_CLASS);
    this.classList.add(HOVER_CLASS);
    // Make sure that the hovercard actually rendered and all dom-if
    // statements processed, so that we can measure the (invisible)
    // hovercard properly in updatePosition().
    flush();
    this.updatePosition();
    this.classList.remove(HIDE_CLASS);
  }

  updatePosition() {
    const positionsToTry = new Set(
        [this.position, 'right', 'bottom-right', 'top-right',
          'bottom', 'top', 'bottom-left', 'top-left', 'left']);
    for (const position of positionsToTry) {
      this.updatePositionTo(position);
      if (this._isInsideViewport()) return;
    }
    console.warn('Could not find a visible position for the hovercard.');
  }

  _isInsideViewport() {
    const thisRect = this.getBoundingClientRect();
    if (thisRect.top < 0) return false;
    if (thisRect.left < 0) return false;
    const docuRect = document.documentElement.getBoundingClientRect();
    if (thisRect.bottom > docuRect.height) return false;
    if (thisRect.right > docuRect.width) return false;
    return true;
  }

  /**
   * Updates the hovercard's position based the current position of the `target`
   * element.
   *
   * The hovercard is supposed to stay open if the user hovers over it.
   * To keep it open when the user moves away from the target, the bounding
   * rects of the target and hovercard must touch or overlap.
   *
   * NOTE: You do not need to directly call this method unless you need to
   * update the position of the tooltip while it is already visible (the
   * target element has moved and the tooltip is still open).
   */
  updatePositionTo(position) {
    if (!this._target) { return; }

    // Make sure that thisRect will not get any paddings and such included
    // in the width and height of the bounding client rect.
    this.style.cssText = '';

    const docuRect = document.documentElement.getBoundingClientRect();
    const targetRect = this._target.getBoundingClientRect();
    const thisRect = this.getBoundingClientRect();

    const targetLeft = targetRect.left - docuRect.left;
    const targetTop = targetRect.top - docuRect.top;

    let hovercardLeft;
    let hovercardTop;
    let cssText = '';

    switch (position) {
      case 'top':
        hovercardLeft = targetLeft + (targetRect.width - thisRect.width) / 2;
        hovercardTop = targetTop - thisRect.height - this.offset;
        break;
      case 'bottom':
        hovercardLeft = targetLeft + (targetRect.width - thisRect.width) / 2;
        hovercardTop = targetTop + targetRect.height + this.offset;
        break;
      case 'left':
        hovercardLeft = targetLeft - thisRect.width - this.offset;
        hovercardTop = targetTop + (targetRect.height - thisRect.height) / 2;
        break;
      case 'right':
        hovercardLeft = targetLeft + targetRect.width + this.offset;
        hovercardTop = targetTop + (targetRect.height - thisRect.height) / 2;
        break;
      case 'bottom-right':
        hovercardLeft = targetLeft + targetRect.width + this.offset;
        hovercardTop = targetTop;
        break;
      case 'bottom-left':
        hovercardLeft = targetLeft - thisRect.width - this.offset;
        hovercardTop = targetTop;
        break;
      case 'top-left':
        hovercardLeft = targetLeft - thisRect.width - this.offset;
        hovercardTop = targetTop + targetRect.height - thisRect.height;
        break;
      case 'top-right':
        hovercardLeft = targetLeft + targetRect.width + this.offset;
        hovercardTop = targetTop + targetRect.height - thisRect.height;
        break;
    }

    cssText += `left:${hovercardLeft}px; top:${hovercardTop}px;`;
    this.style.cssText = cssText;
  }

  /**
   * Responds to a change in the `for` value and gets the updated `target`
   * element for the hovercard.
   *
   * @private
   */
  _forChanged() {
    this._target = this.target;
  }
};
