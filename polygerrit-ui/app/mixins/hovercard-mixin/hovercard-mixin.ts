/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {getRootElement} from '../../scripts/rootElement';
import {Constructor} from '../../utils/common-util';
import {LitElement, PropertyValues} from 'lit';
import {property, query} from 'lit/decorators';
import {ShowAlertEventDetail} from '../../types/events';
import {debounce, DelayedTask} from '../../utils/async-util';
import {hovercardStyles} from '../../styles/gr-hovercard-styles';
import {sharedStyles} from '../../styles/shared-styles';
import {DependencyRequestEvent} from '../../models/dependency';
import {
  addShortcut,
  findActiveElement,
  isElementTarget,
  Key,
  Modifier,
} from '../../utils/dom-util';
import {ShortcutController} from '../../elements/lit/shortcut-controller';
import {
  getFocusableElements,
  getFocusableElementsReverse,
} from '../../utils/focusable';
import {getAppContext} from '../../services/app-context';
import {
  ReportingService,
  Timer,
} from '../../services/gr-reporting/gr-reporting';

interface ReloadEventDetail {
  clearPatchset?: boolean;
}

const HOVER_CLASS = 'hovered';
const HIDE_CLASS = 'hide';

/**
 * ID for the container element.
 */
const containerId = 'gr-hovercard-container';

export interface MouseKeyboardOrFocusEvent {
  keyboardEvent?: KeyboardEvent;
  mouseEvent?: MouseEvent;
  focusEvent?: FocusEvent;
}

export function getHovercardContainer(
  options: {createIfNotExists: boolean} = {createIfNotExists: false}
): HTMLElement | null {
  let container = getRootElement().querySelector<HTMLElement>(
    `#${containerId}`
  );
  if (!container && options.createIfNotExists) {
    // If it does not exist, create and initialize the hovercard container.
    container = document.createElement('div');
    container.setAttribute('id', containerId);
    getRootElement().appendChild(container);
  }
  return container;
}

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
 * The mixin for hovercard behavior.
 *
 * @example
 *
 * class YourComponent extends hovercardBehaviorMixin(
 *  LitElement)
 *
 * @see gr-hovercard.ts
 *
 * // following annotations are required for polylint
 * @lit
 * @mixinFunction
 */
export const HovercardMixin = <T extends Constructor<LitElement>>(
  superClass: T
) => {
  /**
   * @lit
   * @mixinClass
   */
  class Mixin extends superClass {
    @query('#container')
    topElement?: HTMLElement;

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

    // Private but used in tests.
    hideTask?: DelayedTask;

    showTask?: DelayedTask;

    isScheduledToShow?: boolean;

    isScheduledToHide?: boolean;

    openedByKeyboard = false;

    reporting: ReportingService = getAppContext().reportingService;

    reportingTimer?: Timer;

    private targetCleanups: Array<() => void> = [];

    /** Called in disconnectedCallback. */
    private cleanups: (() => void)[] = [];

    static get styles() {
      return [sharedStyles, hovercardStyles];
    }

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    constructor(...args: any[]) {
      super(...args);
      // show the hovercard if mouse moves to hovercard
      // this will cancel pending hide as well
      this.addEventListener('mouseenter', this.mouseShow);
      // when leave hovercard, hide it immediately
      this.addEventListener('mouseleave', this.mouseHide);
      const keyboardController = new ShortcutController(this);
      keyboardController.addGlobal({key: Key.ESC}, (e: KeyboardEvent) =>
        this.hide({keyboardEvent: e})
      );
    }

    override connectedCallback() {
      super.connectedCallback();
      // We have to cache the target because when we this.container.appendChild
      // in show we can not pick the container as target when we reconnect.
      if (!this._target) {
        this._target = this.target;
        this.addTargetEventListeners();
      }

      this.container = getHovercardContainer({createIfNotExists: true});
      this.cleanups.push(
        addShortcut(
          this,
          {key: Key.TAB},
          (e: KeyboardEvent) => {
            this.pressTab(e);
          },
          {
            doNotPrevent: true,
          }
        )
      );
      this.cleanups.push(
        addShortcut(
          this,
          {key: Key.TAB, modifiers: [Modifier.SHIFT_KEY]},
          (e: KeyboardEvent) => {
            this.pressShiftTab(e);
          },
          {
            doNotPrevent: true,
          }
        )
      );
    }

    override disconnectedCallback() {
      this.cancelShowTask();
      this.cancelHideTask();
      for (const cleanup of this.cleanups) cleanup();
      this.cleanups = [];
      super.disconnectedCallback();
    }

    private addTargetEventListeners() {
      // We intentionally listen on 'mousemove' instead of 'mouseenter', because
      // otherwise the target appearing under the mouse cursor would also
      // trigger the hovercard, which can annoying for the user, for example
      // when added reviewer chips appear in the reply dialog via keyboard
      // interaction.
      this._target?.addEventListener('mousemove', this.mouseDebounceShow);
      this._target?.addEventListener('mouseleave', this.mouseDebounceHide);
      this._target?.addEventListener('blur', this.focusDebounceHide);
      this._target?.addEventListener('click', this.mouseHide);
      if (this._target) {
        this.targetCleanups.push(
          addShortcut(this._target, {key: Key.ENTER}, (e: KeyboardEvent) => {
            this.show({keyboardEvent: e});
          })
        );
        this.targetCleanups.push(
          addShortcut(this._target, {key: Key.SPACE}, (e: KeyboardEvent) => {
            this.show({keyboardEvent: e});
          })
        );
      }
      this.addEventListener('request-dependency', this.resolveDep);
    }

    private removeTargetEventListeners() {
      this._target?.removeEventListener('mousemove', this.mouseDebounceShow);
      this._target?.removeEventListener('mouseleave', this.mouseDebounceHide);
      this._target?.removeEventListener('blur', this.focusDebounceHide);
      this._target?.removeEventListener('click', this.mouseHide);
      for (const cleanup of this.targetCleanups) {
        cleanup();
      }
      this.targetCleanups = [];
      this.removeEventListener('request-dependency', this.resolveDep);
    }

    /**
     * Responds to a change in the `for` value and gets the updated `target`
     * element for the hovercard.
     */
    override updated(changedProperties: PropertyValues) {
      super.updated(changedProperties);
      if (changedProperties.has('for')) {
        this.removeTargetEventListeners();
        this._target = this.target;
        this.addTargetEventListeners();
      }
    }

    readonly mouseDebounceHide = (e: MouseEvent) => {
      this.debounceHide({mouseEvent: e});
    };

    readonly mouseDebounceShow = (e: MouseEvent) => {
      this.debounceShow({mouseEvent: e});
    };

    readonly mouseHide = (e: MouseEvent) => {
      this.hide({mouseEvent: e});
    };

    readonly mouseShow = (e: MouseEvent) => {
      this.show({mouseEvent: e});
    };

    readonly focusDebounceHide = (e: FocusEvent) => {
      this.debounceHide({focusEvent: e});
    };

    readonly debounceHide = (props: MouseKeyboardOrFocusEvent) => {
      this.cancelShowTask();
      if (!this._isShowing || this.isScheduledToHide) return;
      this.isScheduledToHide = true;
      this.hideTask = debounce(
        this.hideTask,
        () => {
          // This happens when hide immediately through click or mouse leave
          // on the hovercard
          if (!this.isScheduledToHide) return;
          this.hide(props);
        },
        HIDE_DELAY_MS
      );
    };

    cancelHideTask() {
      if (!this.hideTask) return;
      this.hideTask.cancel();
      this.isScheduledToHide = false;
      this.hideTask = undefined;
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

    private readonly documentClickListener = (e: MouseEvent) => {
      if (!e.target || !isElementTarget(e.target)) return;
      if (this.contains(e.target)) return;
      this.forceHide();
    };

    /**
     * Hovercards aren't children of <gr-app>. Dependencies must be resolved via
     * their targets, so re-route 'request-dependency' events.
     */
    readonly resolveDep = (e: DependencyRequestEvent<unknown>) => {
      this._target?.dispatchEvent(
        new DependencyRequestEvent<unknown>(e.dependency, e.callback)
      );
    };

    readonly forceHide = () => {
      this.hide({keyboardEvent: new KeyboardEvent('enter')});
    };

    /**
     * Hides/closes the hovercard. This occurs when the user triggers the
     * `mouseleave` event on the hovercard's `target` element (as long as the
     * user is not hovering over the hovercard). If event is not specified
     * in props, code assumes mouseEvent
     */
    readonly hide = (props: MouseKeyboardOrFocusEvent) => {
      this.cancelHideTask();
      this.cancelShowTask();
      if (!this._isShowing) {
        return;
      }
      if (!props?.keyboardEvent && this.openedByKeyboard) return;
      // If the user is clicking on a link and still hovering over the hovercard
      // or the user is returning from the hovercard but now hovering over the
      // target (to stop an annoying flicker effect), just return.
      if (props?.mouseEvent) {
        const e = props.mouseEvent;
        if (
          e.relatedTarget === this ||
          (e.target === this && e.relatedTarget === this._target)
        ) {
          return;
        }
      }
      if (this.openedByKeyboard) {
        if (this._target) {
          this._target.focus();
        }
      }
      // Make sure to reset the keyboard variable so new shows will not
      // assume keyboard is the reason for opening the hovercard.
      this.openedByKeyboard = false;

      // Mark that the hovercard is not visible and do not allow focusing
      this._isShowing = false;

      // Clear styles in preparation for the next time we need to show the card
      this.classList.remove(HOVER_CLASS);

      // Reset and remove the hovercard from the DOM
      this.style.cssText = '';
      this.topElement?.setAttribute('tabindex', '-1');

      // Remove the hovercard from the container, given that it is still a child
      // of the container.
      if (this.container?.contains(this)) {
        this.container.removeChild(this);
      }
      document.removeEventListener('click', this.documentClickListener);
      this.reportingTimer?.end({
        targetId: this._target?.id,
        tagName: this.tagName,
      });
    };

    /**
     * Shows/opens the hovercard with a fixed delay.
     */
    readonly debounceShow = (props: MouseKeyboardOrFocusEvent) => {
      this.debounceShowBy(SHOW_DELAY_MS, props);
    };

    /**
     * Shows/opens the hovercard with the given delay.
     */
    debounceShowBy(delayMs: number, props: MouseKeyboardOrFocusEvent) {
      this.cancelHideTask();
      if (this._isShowing || this.isScheduledToShow) return;
      this.isScheduledToShow = true;
      this.showTask = debounce(
        this.showTask,
        () => {
          // This happens when the mouse leaves the target before the delay is over.
          if (!this.isScheduledToShow) return;
          this.show(props);
        },
        delayMs
      );
    }

    override focus(options?: FocusOptions): void {
      const a = getFocusableElements(this).next();
      if (!a.done) a.value.focus(options);
    }

    pressTab(e: KeyboardEvent) {
      const activeElement = findActiveElement(document);
      const lastFocusable = getFocusableElementsReverse(this).next();
      if (!lastFocusable.done && activeElement === lastFocusable.value) {
        e.preventDefault();
        this.forceHide();
      }
    }

    pressShiftTab(e: KeyboardEvent) {
      const activeElement = findActiveElement(document);
      const firstFocusable = getFocusableElements(this).next();
      if (!firstFocusable.done && activeElement === firstFocusable.value) {
        e.preventDefault();
        this.forceHide();
      }
    }

    cancelShowTask() {
      if (!this.showTask) return;
      this.showTask.cancel();
      this.isScheduledToShow = false;
      this.showTask = undefined;
    }

    /**
     * Shows/opens the hovercard. This occurs when the user triggers the
     * `mousenter` event on the hovercard's `target` element or when a user
     * presses enter/space on the hovercard's `target` element. If event is not
     * specified in props, code assumes mouseEvent
     */
    readonly show = async (props: MouseKeyboardOrFocusEvent) => {
      this.cancelHideTask();
      this.cancelShowTask();
      // If we are calling show again because of a mouse reason, then keep
      // the keyboard valuable set.
      this.openedByKeyboard = this.openedByKeyboard || !!props?.keyboardEvent;
      if (this._isShowing || !this.container) {
        return;
      }

      // Mark that the hovercard is now visible
      this._isShowing = true;

      // Add it to the DOM and calculate its position
      this.container.appendChild(this);
      // We temporarily hide the hovercard until we have found the correct
      // position for it.
      this.classList.add(HIDE_CLASS);
      this.classList.add(HOVER_CLASS);
      // Make sure that the hovercard actually rendered and all dom-if
      // statements processed, so that we can measure the (invisible)
      // hovercard properly in updatePosition().
      await new Promise<void>(r => {
        setTimeout(r, 0);
      });
      this.updatePosition();
      this.classList.remove(HIDE_CLASS);
      if (props?.keyboardEvent) {
        this.focus();
      }
      document.addEventListener('click', this.documentClickListener);
      this.reportingTimer = this.reporting.getTimer('Show Hovercard');
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

      this.style.left = `${hovercardLeft}px`;
      this.style.top = `${hovercardTop}px`;
    }
  }

  return Mixin as T & Constructor<HovercardMixinInterface>;
};

export interface HovercardMixinInterface {
  for?: string;
  offset: number;
  _target: HTMLElement | null;
  _isShowing: boolean;
  dispatchEventThroughTarget(eventName: string, detail?: unknown): void;
  show(props: MouseKeyboardOrFocusEvent): Promise<void>;
  forceHide(): void;

  // Used for tests
  mouseHide(e: MouseEvent): void;
  hide(props: MouseKeyboardOrFocusEvent): void;
  container: HTMLElement | null;
  hideTask?: DelayedTask;
  showTask?: DelayedTask;
  position: string;
  debounceShowBy(delayMs: number, props: MouseKeyboardOrFocusEvent): void;
  updatePosition(): void;
  isScheduledToShow?: boolean;
  isScheduledToHide?: boolean;
}
