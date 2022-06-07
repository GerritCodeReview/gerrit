/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {isSafari, findActiveElement} from './dom-util';

const SUPPORTS_SHADOW_SELECTION =
  typeof window.ShadowRoot.prototype.getSelection === 'function';
const SUPPORTS_BEFORE_INPUT =
  typeof (window.InputEvent.prototype as InputEventExtended).getTargetRanges ===
  'function';

const TARGET_ID = 'diffTable';

let processing = false;
let contentEditableRange: Range | null = null;

interface InputEventExtended extends InputEvent {
  getTargetRanges(): StaticRange[];
}

if (isSafari() && !SUPPORTS_SHADOW_SELECTION && SUPPORTS_BEFORE_INPUT) {
  /**
   * This library aims at extracting the selection range in a content editable
   * area. It is a hacky solution to work around the fact that Safari does not
   * allow to get the selection from shadow dom anymore.
   *
   * The main idea behind this approach is the following:
   * - Listen to 'selectionChange' events of 'contentEditable' areas.
   * - Trigger a 'beforeInput' event by running and immediately terminating
   *   an arbitrary `execCommand`.
   * - use the getTargetRanges() method to get a list of static ranges
   *
   * This typescript snippet is the porting of that idea (as explained by its
   * original author [1]).
   *
   * [1] https://github.com/GoogleChromeLabs/shadow-selection-polyfill/issues/11
   */

  window.addEventListener(
    'selectionchange',
    () => {
      if (!processing) {
        processing = true;
        const active = findActiveElement(document, true);
        if (active && active.id === TARGET_ID) {
          // Safari does not allow to select inside a shadowRoot, so we use an
          // `execCommand` to trigger a `beforeInput` event in order to
          // get at the target range from the event.
          document.execCommand('indent');
        }
        processing = false;
      }
    },
    true
  );

  window.addEventListener(
    'beforeinput',
    event => {
      if (processing) {
        // selecting
        const inputEvent = event as InputEventExtended;
        if (typeof inputEvent.getTargetRanges !== 'function') return;
        const range = inputEvent.getTargetRanges()[0];

        const newRange = new Range();

        newRange.setStart(range.startContainer, range.startOffset);
        newRange.setEnd(range.endContainer, range.endOffset);

        contentEditableRange = newRange;

        event.preventDefault();
        event.stopImmediatePropagation();
      } else {
        // typing
        const active = findActiveElement(document, true);
        if (active && active.id === TARGET_ID) {
          // Prevent diff content from actually being edited: Making the diff
          // table content editable is just a mechanism to allow processing
          // 'beforeInput' events, but the content itself should not be editable
          event.preventDefault();
          event.stopImmediatePropagation();
        }
      }
    },
    true
  );

  window.addEventListener(
    'selectstart',
    _ => {
      contentEditableRange = null;
    },
    true
  );
}

export function getContentEditableRange() {
  return contentEditableRange;
}
