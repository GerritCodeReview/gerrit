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
import '../../../styles/shared-styles';
import {addListener} from '@polymer/polymer/lib/utils/gestures';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-diff-selection_html';
import {
  normalize,
  NormalizedRange,
} from '../gr-diff-highlight/gr-range-normalizer';
import {descendedFromClass, querySelectorAll} from '../../../utils/dom-util';
import {customElement, property, observe} from '@polymer/decorators';
import {DiffInfo} from '../../../types/diff';
import {Side} from '../../../constants/constants';
import {GrDiffBuilderElement} from '../gr-diff-builder/gr-diff-builder-element';
import {
  getLineElByChild,
  getSide,
  getSideByLineEl,
  isThreadEl,
} from '../gr-diff/gr-diff-utils';

/**
 * Possible CSS classes indicating the state of selection. Dynamically added/
 * removed based on where the user clicks within the diff.
 */
const SelectionClass = {
  COMMENT: 'selected-comment',
  LEFT: 'selected-left',
  RIGHT: 'selected-right',
  BLAME: 'selected-blame',
};

interface LinesCache {
  left: string[] | null;
  right: string[] | null;
}

function getNewCache(): LinesCache {
  return {left: null, right: null};
}

@customElement('gr-diff-selection')
export class GrDiffSelection extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object})
  diff?: DiffInfo;

  @property({type: Object})
  _cachedDiffBuilder?: GrDiffBuilderElement;

  @property({type: Object})
  _linesCache: LinesCache = {left: null, right: null};

  constructor() {
    super();
    this.addEventListener('copy', e => this._handleCopy(e));
    addListener(this, 'down', e => this._handleDown(e));
  }

  override connectedCallback() {
    super.connectedCallback();
    this.classList.add(SelectionClass.RIGHT);
  }

  get diffBuilder() {
    if (!this._cachedDiffBuilder) {
      this._cachedDiffBuilder = this.querySelector(
        'gr-diff-builder'
      ) as GrDiffBuilderElement;
    }
    return this._cachedDiffBuilder;
  }

  @observe('diff')
  _diffChanged() {
    this._linesCache = getNewCache();
  }

  _handleDownOnRangeComment(node: Element) {
    if (isThreadEl(node)) {
      this._setClasses([
        SelectionClass.COMMENT,
        getSide(node) === Side.LEFT
          ? SelectionClass.LEFT
          : SelectionClass.RIGHT,
      ]);
      return true;
    }
    return false;
  }

  _handleDown(e: Event) {
    const target = e.target;
    if (!(target instanceof Element)) return;
    // Handle the down event on comment thread in Polymer 2
    const handled = this._handleDownOnRangeComment(target);
    if (handled) return;
    const lineEl = getLineElByChild(target);
    const blameSelected = this._elementDescendedFromClass(target, 'blame');
    if (!lineEl && !blameSelected) {
      return;
    }

    const targetClasses = [];

    if (blameSelected) {
      targetClasses.push(SelectionClass.BLAME);
    } else if (lineEl) {
      const commentSelected = this._elementDescendedFromClass(
        target,
        'gr-comment'
      );
      const side = getSideByLineEl(lineEl);

      targetClasses.push(
        side === 'left' ? SelectionClass.LEFT : SelectionClass.RIGHT
      );

      if (commentSelected) {
        targetClasses.push(SelectionClass.COMMENT);
      }
    }

    this._setClasses(targetClasses);
  }

  /**
   * Set the provided list of classes on the element, to the exclusion of all
   * other SelectionClass values.
   */
  _setClasses(targetClasses: string[]) {
    // Remove any selection classes that do not belong.
    for (const className of Object.values(SelectionClass)) {
      if (!targetClasses.includes(className)) {
        this.classList.remove(className);
      }
    }
    // Add new selection classes iff they are not already present.
    for (const _class of targetClasses) {
      if (!this.classList.contains(_class)) {
        this.classList.add(_class);
      }
    }
  }

  _getCopyEventTarget(e: Event) {
    return (dom(e) as EventApi).rootTarget;
  }

  /**
   * Utility function to determine whether an element is a descendant of
   * another element with the particular className.
   */
  _elementDescendedFromClass(element: Element, className: string) {
    return descendedFromClass(element, className, this.diffBuilder.diffElement);
  }

  _handleCopy(e: ClipboardEvent) {
    let commentSelected = false;
    const target = this._getCopyEventTarget(e);
    if (!(target instanceof Element)) return;
    if (target instanceof HTMLTextAreaElement) return;
    if (!this._elementDescendedFromClass(target, 'diff-row')) return;
    if (this.classList.contains(SelectionClass.COMMENT)) {
      commentSelected = true;
    }
    const lineEl = getLineElByChild(target);
    if (!lineEl) return;
    const side = getSideByLineEl(lineEl);
    const text = this._getSelectedText(side, commentSelected);
    if (text && e.clipboardData) {
      e.clipboardData.setData('Text', text);
      e.preventDefault();
    }
  }

  _getSelection() {
    const diffHosts = querySelectorAll(document.body, 'gr-diff');
    if (!diffHosts.length) return document.getSelection();

    const curDiffHost = diffHosts.find(diffHost => {
      if (!diffHost?.shadowRoot?.getSelection) return false;
      const selection = diffHost.shadowRoot.getSelection();
      // Pick the one with valid selection:
      // https://developer.mozilla.org/en-US/docs/Web/API/Selection/type
      return selection && selection.type !== 'None';
    });

    return curDiffHost?.shadowRoot?.getSelection
      ? curDiffHost.shadowRoot.getSelection()
      : document.getSelection();
  }

  /**
   * Get the text of the current selection. If commentSelected is
   * true, it returns only the text of comments within the selection.
   * Otherwise it returns the text of the selected diff region.
   *
   * @param side The side that is selected.
   * @param commentSelected Whether or not a comment is selected.
   * @return The selected text.
   */
  _getSelectedText(side: Side, commentSelected: boolean) {
    const sel = this._getSelection();
    if (!sel || sel.rangeCount !== 1) {
      return ''; // No multi-select support yet.
    }
    if (commentSelected) {
      return this._getCommentLines(sel, side);
    }
    const range = normalize(sel.getRangeAt(0));
    const startLineEl = getLineElByChild(range.startContainer);
    if (!startLineEl) return;
    const endLineEl = getLineElByChild(range.endContainer);
    // Happens when triple click in side-by-side mode with other side empty.
    const endsAtOtherEmptySide =
      !endLineEl &&
      range.endOffset === 0 &&
      range.endContainer.nodeName === 'TD' &&
      range.endContainer instanceof HTMLTableCellElement &&
      (range.endContainer.classList.contains('left') ||
        range.endContainer.classList.contains('right'));
    const startLineDataValue = startLineEl.getAttribute('data-value');
    if (!startLineDataValue) return;
    const startLineNum = Number(startLineDataValue);
    let endLineNum;
    if (endsAtOtherEmptySide) {
      endLineNum = startLineNum + 1;
    } else if (endLineEl) {
      const endLineDataValue = endLineEl.getAttribute('data-value');
      if (endLineDataValue) endLineNum = Number(endLineDataValue);
    }

    return this._getRangeFromDiff(
      startLineNum,
      range.startOffset,
      endLineNum,
      range.endOffset,
      side
    );
  }

  /**
   * Query the diff object for the selected lines.
   */
  _getRangeFromDiff(
    startLineNum: number,
    startOffset: number,
    endLineNum: number | undefined,
    endOffset: number,
    side: Side
  ) {
    const skipChunk = this.diff?.content.find(chunk => chunk.skip);
    if (skipChunk) {
      startLineNum -= skipChunk.skip!;
      if (endLineNum) endLineNum -= skipChunk.skip!;
    }
    const lines = this._getDiffLines(side).slice(startLineNum - 1, endLineNum);
    if (lines.length) {
      lines[lines.length - 1] = lines[lines.length - 1].substring(0, endOffset);
      lines[0] = lines[0].substring(startOffset);
    }
    return lines.join('\n');
  }

  /**
   * Query the diff object for the lines from a particular side.
   *
   * @param side The side that is currently selected.
   * @return An array of strings indexed by line number.
   */
  _getDiffLines(side: Side): string[] {
    if (this._linesCache[side]) {
      return this._linesCache[side]!;
    }
    if (!this.diff) return [];
    let lines: string[] = [];
    for (const chunk of this.diff.content) {
      if (chunk.ab) {
        lines = lines.concat(chunk.ab);
      } else if (side === Side.LEFT && chunk.a) {
        lines = lines.concat(chunk.a);
      } else if (side === Side.RIGHT && chunk.b) {
        lines = lines.concat(chunk.b);
      }
    }
    this._linesCache[side] = lines;
    return lines;
  }

  /**
   * Query the diffElement for comments and check whether they lie inside the
   * selection range.
   *
   * @param sel The selection of the window.
   * @param side The side that is currently selected.
   * @return The selected comment text.
   */
  _getCommentLines(sel: Selection, side: Side) {
    const range = normalize(sel.getRangeAt(0));
    const content = [];
    // Query the diffElement for comments.
    const messages = this.diffBuilder.diffElement.querySelectorAll(
      `.side-by-side [data-side="${side}"] .message *, .unified .message *`
    );

    for (let i = 0; i < messages.length; i++) {
      const el = messages[i];
      // Check if the comment element exists inside the selection.
      if (sel.containsNode(el, true)) {
        // Padded elements require newlines for accurate spacing.
        if (
          el.parentElement!.id === 'container' ||
          el.parentElement!.nodeName === 'BLOCKQUOTE'
        ) {
          if (content.length && content[content.length - 1] !== '') {
            content.push('');
          }
        }

        if (
          el.id === 'output' &&
          !this._elementDescendedFromClass(el, 'collapsed')
        ) {
          content.push(this._getTextContentForRange(el, sel, range));
        }
      }
    }

    return content.join('\n');
  }

  /**
   * Given a DOM node, a selection, and a selection range, recursively get all
   * of the text content within that selection.
   * Using a domNode that isn't in the selection returns an empty string.
   *
   * @param domNode The root DOM node.
   * @param sel The selection.
   * @param range The normalized selection range.
   * @return The text within the selection.
   */
  _getTextContentForRange(
    domNode: Node,
    sel: Selection,
    range: NormalizedRange
  ) {
    if (!sel.containsNode(domNode, true)) {
      return '';
    }

    let text = '';
    if (domNode instanceof Text) {
      text = domNode.textContent || '';
      if (domNode === range.endContainer) {
        text = text.substring(0, range.endOffset);
      }
      if (domNode === range.startContainer) {
        text = text.substring(range.startOffset);
      }
    } else {
      for (const childNode of domNode.childNodes) {
        text += this._getTextContentForRange(childNode, sel, range);
      }
    }
    return text;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-selection': GrDiffSelection;
  }
}
