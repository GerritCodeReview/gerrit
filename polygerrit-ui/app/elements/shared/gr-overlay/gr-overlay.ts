/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../styles/shared-styles';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-overlay_html';
import {IronOverlayMixin} from '../../../mixins/iron-overlay-mixin/iron-overlay-mixin';
import {customElement} from '@polymer/decorators';
import {IronOverlayBehavior} from '@polymer/iron-overlay-behavior/iron-overlay-behavior';
import {findActiveElement} from '../../../utils/dom-util';
import {fireEvent} from '../../../utils/event-util';
import {getFocusableElements} from '../../../utils/focusable';

const AWAIT_MAX_ITERS = 10;
const AWAIT_STEP = 5;
const BREAKPOINT_FULLSCREEN_OVERLAY = '50em';

declare global {
  interface HTMLElementTagNameMap {
    'gr-overlay': GrOverlay;
  }
}

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = IronOverlayMixin(
  PolymerElement,
  IronOverlayBehavior as IronOverlayBehavior
);

/**
 * @attr {Boolean} with-backdrop - inherited from IronOverlay
 * @attr {Boolean} always-on-top - inherited from IronOverlay
 * @attr {Boolean} no-cancel-on-esc-key - inherited from IronOverlay
 * @attr {Boolean} no-cancel-on-outside-click - inherited from IronOverlay
 * @attr {String} scroll-action - inherited from IronOverlay
 */
@customElement('gr-overlay')
export class GrOverlay extends base {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when a fullscreen overlay is closed
   *
   * @event fullscreen-overlay-closed
   */

  /**
   * Fired when an overlay is opened in full screen mode
   *
   * @event fullscreen-overlay-opened
   */

  // private but used in test
  fullScreenOpen = false;

  // private but used in test
  _boundHandleClose: () => void = () => super.close();

  private returnFocusTo?: HTMLElement;

  override get _focusableNodes() {
    return Array.from(getFocusableElements(this));
  }

  constructor() {
    super();
    this.addEventListener('iron-overlay-closed', () => this._overlayClosed());
    this.addEventListener('iron-overlay-cancelled', () =>
      this._overlayClosed()
    );
  }

  override open() {
    this.returnFocusTo = findActiveElement(document, true) ?? undefined;
    window.addEventListener('popstate', this._boundHandleClose);
    return new Promise<void>((resolve, reject) => {
      super.open.apply(this);
      if (this._isMobile()) {
        fireEvent(this, 'fullscreen-overlay-opened');
        this.fullScreenOpen = true;
      }
      this._awaitOpen(resolve, reject);
    });
  }

  _isMobile() {
    return window.matchMedia(`(max-width: ${BREAKPOINT_FULLSCREEN_OVERLAY})`);
  }

  // called after iron-overlay is closed. Does not actually close the overlay
  _overlayClosed() {
    window.removeEventListener('popstate', this._boundHandleClose);
    if (this.fullScreenOpen) {
      fireEvent(this, 'fullscreen-overlay-closed');
      this.fullScreenOpen = false;
    }
    if (this.returnFocusTo) {
      this.returnFocusTo.focus();
      this.returnFocusTo = undefined;
    }
  }

  /**
   * NOTE: (wyatta) Slightly hacky way to listen to the overlay actually
   * opening. Eventually replace with a direct way to listen to the overlay.
   */
  _awaitOpen(fn: (this: GrOverlay) => void, reject: (error: Error) => void) {
    let iters = 0;
    const step = () => {
      setTimeout(() => {
        if (this.style.display !== 'none') {
          fn.call(this);
        } else if (iters++ < AWAIT_MAX_ITERS) {
          step.call(this);
        } else {
          reject(new Error('gr-overlay _awaitOpen failed to resolve'));
        }
      }, AWAIT_STEP);
    };
    step.call(this);
  }

  _id() {
    return this.getAttribute('id') || 'global';
  }
}

export interface GrOverlayStops {
  start: Node;
  end: Node;
}
