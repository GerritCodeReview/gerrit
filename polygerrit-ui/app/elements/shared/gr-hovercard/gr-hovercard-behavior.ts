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
import '../../../styles/shared-styles';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom';
import {getRootElement} from '../../../scripts/rootElement';
import {Constructor} from '../../../utils/common-util';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {dedupingMixin} from '@polymer/polymer/lib/utils/mixin';
import {property, observe} from '@polymer/decorators';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {
  pushScrollLock,
  removeScrollLock,
} from '@polymer/iron-overlay-behavior/iron-scroll-manager';
import {ShowAlertEventDetail} from '../../../types/events';
import {debounce, DelayedTask} from '../../../utils/async-util';
interface ReloadEventDetail {
  clearPatchset?: boolean;
}

const HOVER_CLASS = 'hovered';
const HIDE_CLASS = 'hide';

/**
 * How long should we wait before showing the hovercard when the user hovers
 * over the element?
 */
const SHOW_DELAY_MS = 550;

/**
 * How long should we wait before hiding the hovercard when the user moves from
 * target to the hovercard.
 *
 * Note: this should be lower than SHOW_DELAY_MS to avoid flickering.
 */
const HIDE_DELAY_MS = 500;

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
 *
 * @see gr-hovercard.ts
 *
 * // following annotations are required for polylint
 * @polymer
 * @mixinFunction
 */
export const hovercardBehaviorMixin = dedupingMixin(
  <T extends Constructor<PolymerElement & LegacyElementMixin>>(
    superClass: T
  ): T & Constructor<GrHovercardBehaviorInterface> => {
    /**
     * @polymer
     * @mixinClass
     */
    class Mixin extends superClass {
      @property({type: Object})
      _target: HTMLElement | null = null;

      // Determines whether or not the hovercard is visible.
      @property({type: Boolean})
      _isShowing = false;

      // The `id` of the element that the hovercard is anchored to.
      @property({type: String})
      for?: string;

      /**
       * The spacing between the top of the hovercard and the element it is
       * anchored to.
       */
      @property({type: Number})
      offset = 14;

      /**
       * Positions the hovercard to the top, right, bottom, left, bottom-left,
       * bottom-right, top-left, or top-right of its content.
       */
      @property({type: String})
      position = 'right';

      @property({type: Object})
      container: HTMLElement | null = null;

      /**
       * ID for the container element.
       */
      @property({type: String})
      containerId = 'gr-hovercard-container';

      private hideTask?: DelayedTask;

      private showTask?: DelayedTask;

      private isScheduledToShow?: boolean;

      private isScheduledToHide?: boolean;

      /** @override */
      connectedCallback() {
        super.connectedCallback();
        if (!this._target) {
          this._target = this.target;
        }
        this._target.addEventListener('mouseenter', this.debounceShow);
        this._target.addEventListener('focus', this.debounceShow);
        this._target.addEventListener('mouseleave', this.debounceHide);
        this._target.addEventListener('blur', this.debounceHide);

        // when click, dismiss immediately
        this._target.addEventListener('click', this.hide);

        // show the hovercard if mouse moves to hovercard
        // this will cancel pending hide as well
        this.addEventListener('mouseenter', this.show);
        this.addEventListener('mouseenter', this.lock);
        // when leave hovercard, hide it immediately
        this.addEventListener('mouseleave', this.hide);
        this.addEventListener('mouseleave', this.unlock);
      }

      disconnectedCallback() {
        this.cancelShowTask();
        this.cancelHideTask();
        this.unlock();
        super.disconnectedCallback();
      }

      /** @override */
      ready() {
        super.ready();
        // First, check to see if the container has already been created.
        this.container = getRootElement().querySelector('#' + this.containerId);

        if (this.container) {
          return;
        }

        // If it does not exist, create and initialize the hovercard container.
        this.container = document.createElement('div');
        this.container.setAttribute('id', this.containerId);
        getRootElement().appendChild(this.container);
      }

      removeListeners() {
        this._target?.removeEventListener('mouseenter', this.debounceShow);
        this._target?.removeEventListener('focus', this.debounceShow);
        this._target?.removeEventListener('mouseleave', this.debounceHide);
        this._target?.removeEventListener('blur', this.debounceHide);
        this._target?.removeEventListener('click', this.hide);
      }

      readonly debounceHide = () => {
        this.cancelShowTask();
        if (!this._isShowing || this.isScheduledToHide) return;
        this.isScheduledToHide = true;
        this.hideTask = debounce(
          this.hideTask,
          () => {
            // This happens when hide immediately through click or mouse leave
            // on the hovercard
            if (!this.isScheduledToHide) return;
            this.hide();
          },
          HIDE_DELAY_MS
        );
      };

      cancelHideTask() {
        if (this.hideTask) {
          this.hideTask.cancel();
          this.isScheduledToHide = false;
        }
      }

      /**
       * Hovercard elements are created outside of <gr-app>, so if you want to fire
       * events, then you probably want to do that through the target element.
       */

      dispatchEventThroughTarget(eventName: string): void;

      dispatchEventThroughTarget(
        eventName: 'show-alert',
        detail: ShowAlertEventDetail
      ): void;

      dispatchEventThroughTarget(
        eventName: 'reload',
        detail: ReloadEventDetail
      ): void;

      dispatchEventThroughTarget(eventName: string, detail?: unknown) {
        if (!detail) detail = {};
        if (this._target)
          this._target.dispatchEvent(
            new CustomEvent(eventName, {
              detail,
              bubbles: true,
              composed: true,
            })
          );
      }

      /**
       * Returns the target element that the hovercard is anchored to (the `id` of
       * the `for` property).
       */
      get target(): HTMLElement {
        const parentNode = this.parentNode;
        // If the parentNode is a document fragment, then we need to use the host.
        const ownerRoot = this.getRootNode() as ShadowRoot;
        let target;
        if (this.for) {
          target = ownerRoot.querySelector('#' + this.for);
        } else {
          target =
            !parentNode || parentNode.nodeType === Node.DOCUMENT_FRAGMENT_NODE
              ? ownerRoot.host
              : parentNode;
        }
        return target as HTMLElement;
      }

      /**
       * unlock scroll, this will resume the scroll outside of the hovercard.
       */
      readonly unlock = () => {
        removeScrollLock(this);
      };

      /**
       * Hides/closes the hovercard. This occurs when the user triggers the
       * `mouseleave` event on the hovercard's `target` element (as long as the
       * user is not hovering over the hovercard).
       *
       */
      readonly hide = (e?: MouseEvent) => {
        this.cancelHideTask();
        this.cancelShowTask();
        if (!this._isShowing) {
          return;
        }

        // If the user is now hovering over the hovercard or the user is returning
        // from the hovercard but now hovering over the target (to stop an annoying
        // flicker effect), just return.
        if (e) {
          if (
            e.relatedTarget === this ||
            (e.target === this && e.relatedTarget === this._target)
          ) {
            return;
          }
        }

        // Mark that the hovercard is not visible and do not allow focusing
        this._isShowing = false;

        // Clear styles in preparation for the next time we need to show the card
        this.classList.remove(HOVER_CLASS);

        // Reset and remove the hovercard from the DOM
        this.style.cssText = '';
        this.$['container'].setAttribute('tabindex', '-1');

        // Remove the hovercard from the container, given that it is still a child
        // of the container.
        if (this.container?.contains(this)) {
          this.container.removeChild(this);
        }
      };

      /**
       * Shows/opens the hovercard with a fixed delay.
       */
      readonly debounceShow = () => {
        this.debounceShowBy(SHOW_DELAY_MS);
      };

      /**
       * Shows/opens the hovercard with the given delay.
       */
      debounceShowBy(delayMs: number) {
        this.cancelHideTask();
        if (this._isShowing || this.isScheduledToShow) return;
        this.isScheduledToShow = true;
        this.showTask = debounce(
          this.showTask,
          () => {
            // This happens when the mouse leaves the target before the delay is over.
            if (!this.isScheduledToShow) return;
            this.show();
          },
          delayMs
        );
      }

      cancelShowTask() {
        if (this.showTask) {
          this.showTask.cancel();
          this.isScheduledToShow = false;
        }
      }

      /**
       * Lock background scroll but enable scroll inside of current hovercard.
       */
      readonly lock = () => {
        pushScrollLock(this);
      };

      /**
       * Shows/opens the hovercard. This occurs when the user triggers the
       * `mousenter` event on the hovercard's `target` element.
       */
      readonly show = () => {
        this.cancelHideTask();
        this.cancelShowTask();
        if (this._isShowing || !this.container) {
          return;
        }

        // Mark that the hovercard is now visible
        this._isShowing = true;
        this.setAttribute('tabindex', '0');

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
      };

      updatePosition() {
        const positionsToTry = new Set([
          this.position,
          'right',
          'bottom-right',
          'top-right',
          'bottom',
          'top',
          'bottom-left',
          'top-left',
          'left',
        ]);
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
      updatePositionTo(position: string) {
        if (!this._target) {
          return;
        }

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

        switch (position) {
          case 'top':
            hovercardLeft =
              targetLeft + (targetRect.width - thisRect.width) / 2;
            hovercardTop = targetTop - thisRect.height - this.offset;
            break;
          case 'bottom':
            hovercardLeft =
              targetLeft + (targetRect.width - thisRect.width) / 2;
            hovercardTop = targetTop + targetRect.height + this.offset;
            break;
          case 'left':
            hovercardLeft = targetLeft - thisRect.width - this.offset;
            hovercardTop =
              targetTop + (targetRect.height - thisRect.height) / 2;
            break;
          case 'right':
            hovercardLeft = targetLeft + targetRect.width + this.offset;
            hovercardTop =
              targetTop + (targetRect.height - thisRect.height) / 2;
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

        this.style.left = `${hovercardLeft}px`;
        this.style.top = `${hovercardTop}px`;
      }

      /**
       * Responds to a change in the `for` value and gets the updated `target`
       * element for the hovercard.
       */
      @observe('for')
      _forChanged() {
        this._target = this.target;
      }
    }

    return Mixin;
  }
);

export interface GrHovercardBehaviorInterface {
  attached(): void;
  ready(): void;
  removeListeners(): void;
  debounceHide(): void;
  cancelHideTask(): void;
  dispatchEventThroughTarget(eventName: string, detail?: unknown): void;
  hide(e?: MouseEvent): void;
  debounceShow(): void;
  debounceShowBy(delayMs: number): void;
  cancelShowTask(): void;
  show(): void;
  updatePosition(): void;
  updatePositionTo(position: string): void;
}
