const SUPPORTS_SHADOW_SELECTION = typeof window.ShadowRoot.prototype.getSelection === 'function';
const SUPPORTS_BEFORE_INPUT = typeof window.InputEvent.prototype.getTargetRanges === 'function';
const IS_FIREFOX = window.navigator.userAgent.toLowerCase().indexOf('firefox') > -1;

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

  while (true) {
    if (active && active.shadowRoot && active.shadowRoot.activeElement) {
      active = active.shadowRoot.activeElement;
    } else {
      break;
    }
  }

  return active;
}

let processing = false;
let selection = new ContentEditableSelection();

if (!IS_FIREFOX && !SUPPORTS_SHADOW_SELECTION && SUPPORTS_BEFORE_INPUT) {

  window.addEventListener('selectionchange', () => {
    console.log("'selectionchange'. Processing [" + processing + "]. Range: [" + selection.getRangeAt(0) + "]");
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

  window.addEventListener('beforeinput', (event) => {
    console.log("'beforeinput'. Processing [" + processing + "]. Range: [" + selection.getRangeAt(0) + "]");
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

  window.addEventListener('selectstart', (event) => {
    console.log("event handler 'selectstart' triggered, removing all ranges");
    selection.removeAllRanges();
  }, true);
}

export function getRange() {
    let r = selection.getRangeAt(0);
    console.log("getRange. Processing [" + processing + "]. Range: [" + r + "]");
    return r;
}