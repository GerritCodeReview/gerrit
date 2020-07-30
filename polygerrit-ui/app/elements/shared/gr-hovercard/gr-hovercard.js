/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
(function() {
  'use strict';
  const HOVER_CLASS = 'hovered';

  /**
   * When the hovercard is positioned diagonally (bottom-left, bottom-right,
   * top-left, or top-right), we add additional (invisible) padding so that the
   * area that a user can hover over to access the hovercard is larger.
   */
  const DIAGONAL_OVERFLOW = 15;

  Polymer({
    is: 'gr-hovercard',

    properties: {
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
        value: 'bottom',
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
    },

    listeners: {
      mouseleave: 'hide',
    },

    attached() {
      if (!this._target) { this._target = this.target; }
      this.listen(this._target, 'mouseenter', 'show');
      this.listen(this._target, 'focus', 'show');
      this.listen(this._target, 'mouseleave', 'hide');
      this.listen(this._target, 'blur', 'hide');
      this.listen(this._target, 'click', 'hide');
    },

    ready() {
      // First, check to see if the container has already been created.
      this.container = Gerrit.getRootElement()
          .querySelector('#' + this.containerId);

      if (this.container) { return; }

      // If it does not exist, create and initialize the hovercard container.
      this.container = document.createElement('div');
      this.container.setAttribute('id', this.containerId);
      Gerrit.getRootElement().appendChild(this.container);
    },

    removeListeners() {
      this.unlisten(this._target, 'mouseenter', 'show');
      this.unlisten(this._target, 'focus', 'show');
      this.unlisten(this._target, 'mouseleave', 'hide');
      this.unlisten(this._target, 'blur', 'hide');
      this.unlisten(this._target, 'click', 'hide');
    },

    /**
     * Returns the target element that the hovercard is anchored to (the `id` of
     * the `for` property).
     *
     * @type {HTMLElement}
     */
    get target() {
      const parentNode = Polymer.dom(this).parentNode;
      // If the parentNode is a document fragment, then we need to use the host.
      const ownerRoot = Polymer.dom(this).getOwnerRoot();
      let target;
      if (this.for) {
        target = Polymer.dom(ownerRoot).querySelector('#' + this.for);
      } else {
        target = parentNode.nodeType == Node.DOCUMENT_FRAGMENT_NODE ?
          ownerRoot.host :
          parentNode;
      }
      return target;
    },

    /**
     * Hides/closes the hovercard. This occurs when the user triggers the
     * `mouseleave` event on the hovercard's `target` element (as long as the
     * user is not hovering over the hovercard).
     *
     * @param {Event} e DOM Event (e.g. `mouseleave` event)
     */
    hide(e) {
      const targetRect = this._target.getBoundingClientRect();
      const x = e.clientX;
      const y = e.clientY;
      if (x > targetRect.left && x < targetRect.right && y > targetRect.top &&
          y < targetRect.bottom) {
        // Sometimes the hovercard itself obscures the mouse pointer, and
        // that generates a mouseleave event. We don't want to hide the hovercard
        // in that situation.
        return;
      }

      // If the hovercard is already hidden or the user is now hovering over the
      //  hovercard or the user is returning from the hovercard but now hovering
      //  over the target (to stop an annoying flicker effect), just return.
      if (!this._isShowing || e.relatedTarget === this ||
          (e.target === this && e.relatedTarget === this._target)) {
        return;
      }

      // Mark that the hovercard is not visible and do not allow focusing
      this._isShowing = false;

      // Clear styles in preparation for the next time we need to show the card
      this.classList.remove(HOVER_CLASS);

      // Reset and remove the hovercard from the DOM
      this.style.cssText = '';
      this.$.hovercard.setAttribute('tabindex', -1);

      // Remove the hovercard from the container, given that it is still a child
      // of the container.
      if (this.container.contains(this)) {
        this.container.removeChild(this);
      }
    },

    /**
     * Shows/opens the hovercard. This occurs when the user triggers the
     * `mousenter` event on the hovercard's `target` element.
     *
     * @param {Event} e DOM Event (e.g., `mouseenter` event)
     */
    show(e) {
      if (this._isShowing) {
        return;
      }

      // Mark that the hovercard is now visible
      this._isShowing = true;
      this.setAttribute('tabindex', 0);

      // Add it to the DOM and calculate its position
      this.container.appendChild(this);
      this.updatePosition();

      // Trigger the transition
      this.classList.add(HOVER_CLASS);
    },

    /**
     * Updates the hovercard's position based on the `position` attribute
     * and the current position of the `target` element.
     *
     * The hovercard is supposed to stay open if the user hovers over it.
     * To keep it open when the user moves away from the target, the bounding
     * rects of the target and hovercard must touch or overlap.
     *
     * NOTE: You do not need to directly call this method unless you need to
     * update the position of the tooltip while it is already visible (the
     * target element has moved and the tooltip is still open).
     */
    updatePosition() {
      if (!this._target) { return; }

      // Calculate the necessary measurements and positions
      const parentRect = document.documentElement.getBoundingClientRect();
      const targetRect = this._target.getBoundingClientRect();
      const thisRect = this.getBoundingClientRect();

      const targetLeft = targetRect.left - parentRect.left;
      const targetTop = targetRect.top - parentRect.top;

      let hovercardLeft;
      let hovercardTop;
      const diagonalPadding = this.offset + DIAGONAL_OVERFLOW;
      let cssText = '';

      // Find the top and left position values based on the position attribute
      // of the hovercard.
      switch (this.position) {
        case 'top':
          hovercardLeft = targetLeft + (targetRect.width - thisRect.width) / 2;
          hovercardTop = targetTop - thisRect.height - this.offset;
          cssText += `padding-bottom:${this.offset
          }px; margin-bottom:-${this.offset}px;`;
          break;
        case 'bottom':
          hovercardLeft = targetLeft + (targetRect.width - thisRect.width) / 2;
          hovercardTop = targetTop + targetRect.height + this.offset;
          cssText +=
              `padding-top:${this.offset}px; margin-top:-${this.offset}px;`;
          break;
        case 'left':
          hovercardLeft = targetLeft - thisRect.width - this.offset;
          hovercardTop = targetTop + (targetRect.height - thisRect.height) / 2;
          cssText +=
              `padding-right:${this.offset}px; margin-right:-${this.offset}px;`;
          break;
        case 'right':
          hovercardLeft = targetRect.right + this.offset;
          hovercardTop = targetTop + (targetRect.height - thisRect.height) / 2;
          cssText +=
              `padding-left:${this.offset}px; margin-left:-${this.offset}px;`;
          break;
        case 'bottom-right':
          hovercardLeft = targetRect.left + targetRect.width + this.offset;
          hovercardTop = targetRect.top + targetRect.height + this.offset;
          cssText += `padding-top:${diagonalPadding}px;`;
          cssText += `padding-left:${diagonalPadding}px;`;
          cssText += `margin-left:-${diagonalPadding}px;`;
          cssText += `margin-top:-${diagonalPadding}px;`;
          break;
        case 'bottom-left':
          hovercardLeft = targetRect.left - thisRect.width - this.offset;
          hovercardTop = targetRect.top + targetRect.height + this.offset;
          cssText += `padding-top:${diagonalPadding}px;`;
          cssText += `padding-right:${diagonalPadding}px;`;
          cssText += `margin-right:-${diagonalPadding}px;`;
          cssText += `margin-top:-${diagonalPadding}px;`;
          break;
        case 'top-left':
          hovercardLeft = targetRect.left - thisRect.width - this.offset;
          hovercardTop = targetRect.top - thisRect.height - this.offset;
          cssText += `padding-bottom:${diagonalPadding}px;`;
          cssText += `padding-right:${diagonalPadding}px;`;
          cssText += `margin-bottom:-${diagonalPadding}px;`;
          cssText += `margin-right:-${diagonalPadding}px;`;
          break;
        case 'top-right':
          hovercardLeft = targetRect.left + targetRect.width + this.offset;
          hovercardTop = targetRect.top - thisRect.height - this.offset;
          cssText += `padding-bottom:${diagonalPadding}px;`;
          cssText += `padding-left:${diagonalPadding}px;`;
          cssText += `margin-bottom:-${diagonalPadding}px;`;
          cssText += `margin-left:-${diagonalPadding}px;`;
          break;
      }

      // Prevent hovercard from appearing outside the viewport.
      // TODO(kaspern): fix hovercard appearing outside viewport on bottom and
      // right.
      if (hovercardLeft < 0) { hovercardLeft = 0; }
      if (hovercardTop < 0) { hovercardTop = 0; }
      // Set the hovercard's position
      cssText += `left:${hovercardLeft}px; top:${hovercardTop}px;`;
      this.style.cssText = cssText;
    },

    /**
     * Responds to a change in the `for` value and gets the updated `target`
     * element for the hovercard.
     *
     * @private
     */
    _forChanged() {
      this._target = this.target;
    },
  });
})();
