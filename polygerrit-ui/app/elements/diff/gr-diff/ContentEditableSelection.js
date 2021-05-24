/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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
import {isSafari, findActiveElement} from '../../../utils/dom-util.js';

const SUPPORTS_SHADOW_SELECTION =
    typeof window.ShadowRoot.prototype.getSelection === 'function';
const SUPPORTS_BEFORE_INPUT =
    typeof window.InputEvent.prototype.getTargetRanges === 'function';

class ContentEditableSelection {
  constructor() {
    this._range = null;
  }

  getRange() {
    return this._range;
  }

  setRange(range) {
    this._range = range;
  }

  removeRange() {
    this._range = null;
  }
}

let processing = false;
const selection = new ContentEditableSelection();

if (isSafari() && !SUPPORTS_SHADOW_SELECTION && SUPPORTS_BEFORE_INPUT) {
  window.addEventListener('selectionchange', () => {
    if (!processing) {
      processing = true;
      const active = findActiveElement(document, true);
      if (active && (active.id === 'diffTable')) {
        // Safari does allow to select inside a shadowRoot, so we use an
        // `execCommand` to trigger a `beforeInput` event in order to
        // get at the target range from the event.
        document.execCommand('indent');
      } else {
        selection.removeRange();
      }
      processing = false;
    }
  }, true);

  window.addEventListener('beforeinput', event => {
    if (processing) {
      // selecting
      // console.log("selecting: " + event.inputType);
      const ranges = event.getTargetRanges();
      const range = ranges[0];

      const newRange = new Range();

      newRange.setStart(range.startContainer, range.startOffset);
      newRange.setEnd(range.endContainer, range.endOffset);

      selection.setRange(newRange);

      event.preventDefault();
      event.stopImmediatePropagation();
    } else {
      // typing
      const active = findActiveElement(document, true);
      // console.log("Typing text:" + event.inputType + " : " + active.id);
      if (active.id === 'diffTable') {
        event.preventDefault();
        event.stopImmediatePropagation();
      }
    }
  }, true);

  window.addEventListener('selectstart', event => {
    selection.removeRange();
  }, true);
}

export function getContentEditableRange() {
  return selection.getRange();
}