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
import '../../elements/shared/gr-tooltip/gr-tooltip';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom';
import {getRootElement} from '../../scripts/rootElement';
import {property, observe} from '@polymer/decorators';
import {dedupingMixin} from '@polymer/polymer/lib/utils/mixin';
import {PolymerElementConstructor} from '@polymer/polymer/interfaces';
import {ElementMixinConstructor} from '@polymer/polymer/lib/mixins/element-mixin';
import {GrTooltip} from '../../elements/shared/gr-tooltip/gr-tooltip';

const BOTTOM_OFFSET = 7.2; // Height of the arrow in tooltip.

/**
 * @polymer
 * @mixinFunction
 */
export const TooltipMixin = dedupingMixin(
  (superClass: PolymerElementConstructor & ElementMixinConstructor) => {
    /**
     * @polymer
     * @mixinClass
     */
    class Mixin extends superClass {
      @property({type: Boolean})
      hasTooltip = false;

      @property({type: Boolean, reflectToAttribute: true})
      positionBelow = false;

      @property({type: Boolean})
      _isTouchDevice = 'ontouchstart' in document.documentElement;

      @property({type: Object})
      _tooltip?: GrTooltip | null;

      @property({type: String})
      _titleText = '';

      @property({type: Boolean})
      _hasSetupTooltipListeners = false;

      // Handler for mouseenter event
      _mouseenterHandler?: (e: MouseEvent) => void;

      // Hanlder for scrolling on window
      _windowScrollHandler: () => void;

      // Hanlder for showing the tooltip, will be attached to certain events
      _showHandler: () => void;

      // Hanlder for hiding the tooltip, will be attached to certain events
      _hideHandler: () => void;

      constructor() {
        super();
        this._windowScrollHandler = () => this._handleWindowScroll();
        this._showHandler = () => this._handleShowTooltip();
        this._hideHandler = () => this._handleHideTooltip();
      }

      /** @override */
      disconnectedCallback() {
        super.disconnectedCallback();
        // NOTE: if you define your own `detached` in your component
        // then this won't take affect (as its not a class yet)
        this._handleHideTooltip();
        if (this._mouseenterHandler) {
          this.removeEventListener('mouseenter', this._mouseenterHandler);
        }
        window.removeEventListener('scroll', this._windowScrollHandler);
      }

      @observe('hasTooltip')
      _setupTooltipListeners() {
        if (!this._mouseenterHandler) {
          this._mouseenterHandler = () => this._handleShowTooltip();
        }

        if (!this.hasTooltip) {
          // if attribute set to false, remove the listener
          this.removeEventListener('mouseenter', this._mouseenterHandler);
          this._hasSetupTooltipListeners = false;
          return;
        }

        if (this._hasSetupTooltipListeners) {
          return;
        }
        this._hasSetupTooltipListeners = true;

        this.addEventListener('mouseenter', this._mouseenterHandler);
      }

      _handleShowTooltip() {
        if (this._isTouchDevice) {
          return;
        }

        if (
          !this.hasAttribute('title') ||
          this.getAttribute('title') === '' ||
          this._tooltip
        ) {
          return;
        }

        // Store the title attribute text then set it to an empty string to
        // prevent it from showing natively.
        this._titleText = this.getAttribute('title') || '';
        this.setAttribute('title', '');

        const tooltip = document.createElement('gr-tooltip');
        tooltip.text = this._titleText;
        tooltip.maxWidth = this.getAttribute('max-width') || '';
        tooltip.positionBelow = this.hasAttribute('position-below');

        // Set visibility to hidden before appending to the DOM so that
        // calculations can be made based on the element’s size.
        tooltip.style.visibility = 'hidden';
        getRootElement().appendChild(tooltip);
        this._positionTooltip(tooltip);
        tooltip.style.visibility = 'initial';

        this._tooltip = tooltip;
        window.addEventListener('scroll', this._windowScrollHandler);
        this.addEventListener('mouseleave', this._showHandler);
        this.addEventListener('click', this._hideHandler);
      }

      _handleHideTooltip() {
        if (this._isTouchDevice) {
          return;
        }
        if (!this.hasAttribute('title') || !this._titleText) {
          return;
        }

        window.removeEventListener('scroll', this._windowScrollHandler);
        this.removeEventListener('mouseleave', this._hideHandler);
        this.removeEventListener('click', this._hideHandler);
        this.setAttribute('title', this._titleText);

        if (this._tooltip && this._tooltip.parentNode) {
          this._tooltip.parentNode.removeChild(this._tooltip);
        }
        this._tooltip = null;
      }

      _handleWindowScroll() {
        if (!this._tooltip) {
          return;
        }

        this._positionTooltip(this._tooltip);
      }

      _positionTooltip(tooltip: GrTooltip) {
        // This flush is needed for tooltips to be positioned correctly in Firefox
        // and Safari.
        flush();
        const rect = this.getBoundingClientRect();
        const boxRect = tooltip.getBoundingClientRect();
        if (tooltip.parentElement) {
          const parentRect = tooltip.parentElement.getBoundingClientRect();
          const top = rect.top - parentRect.top;
          const left =
            rect.left - parentRect.left + (rect.width - boxRect.width) / 2;
          const right = parentRect.width - left - boxRect.width;
          if (left < 0) {
            tooltip.updateStyles({
              '--gr-tooltip-arrow-center-offset': `${left}px`,
            });
          } else if (right < 0) {
            tooltip.updateStyles({
              '--gr-tooltip-arrow-center-offset': `${-0.5 * right}px`,
            });
          }
          tooltip.style.left = `${Math.max(0, left)}px`;

          if (!this.positionBelow) {
            tooltip.style.top = `${Math.max(0, top)}px`;
            tooltip.style.transform = `translateY(calc(-100% - ${BOTTOM_OFFSET}px))`;
          } else {
            tooltip.style.top = `${top + rect.height + BOTTOM_OFFSET}px`;
          }
        }
      }
    }

    return Mixin;
  }
);
