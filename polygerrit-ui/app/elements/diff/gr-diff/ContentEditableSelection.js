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
const SUPPORTS_SHADOW_SELECTION =
    typeof window.ShadowRoot.prototype.getSelection === 'function';
const SUPPORTS_BEFORE_INPUT =
    typeof window.InputEvent.prototype.getTargetRanges === 'function';
const IS_FIREFOX =
    window.navigator.userAgent.toLowerCase().indexOf('firefox') > -1;

class ContentEditableSelection {
  constructor() {
    this._ranges = [];
  }

  getRangeAt(index) {
    return this._ranges[index];
  }

  addRange(range) {
    this._ranges.push(range);
  }

  removeAllRanges() {
    this._ranges = [];
  }
}

function getActiveElement() {
  let active = document.activeElement;
  let searchActive = true;
  while (searchActive) {
    if (active && active.shadowRoot && active.shadowRoot.activeElement) {
      active = active.shadowRoot.activeElement;
    } else {
      searchActive = false;
    }
  }

  return active;
}

let processing = false;
const selection = new ContentEditableSelection();

if (!IS_FIREFOX && !SUPPORTS_SHADOW_SELECTION && SUPPORTS_BEFORE_INPUT) {
  window.addEventListener('selectionchange', () => {
    if (!processing) {
      processing = true;
      const active = getActiveElement();
      if (active && (active.getAttribute('contenteditable') === 'true')) {
        document.execCommand('indent');
      } else {
        selection.removeAllRanges();
      }
      processing = false;
    }
  }, true);

  window.addEventListener('beforeinput', event => {
    if (processing) {
      const ranges = event.getTargetRanges();
      const range = ranges[0];

      const newRange = new Range();

      newRange.setStart(range.startContainer, range.startOffset);
      newRange.setEnd(range.endContainer, range.endOffset);

      selection.removeAllRanges();
      selection.addRange(newRange);

      event.preventDefault();
      event.stopImmediatePropagation();
    }
  }, true);

  window.addEventListener('selectstart', event => {
    selection.removeAllRanges();
  }, true);
}

export function getRange() {
  const r = selection.getRangeAt(0);
  return r;
}