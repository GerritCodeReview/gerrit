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
import {DiffLayer, DiffLayerListener} from '../../../types/types';
import {GrDiffLine, Side} from '../../../api/diff';
import {GrAnnotation} from '../gr-diff-highlight/gr-annotation';
import {debounce, DelayedTask} from '../../../utils/async-util';
import {
  getLineNumberByChild,
  lineNumberToNumber,
} from '../gr-diff/gr-diff-utils';

const tokenMatcher = new RegExp(/\w+/g);

/** CSS class for all tokens. */
const TOKEN = 'token';

/** CSS class for the currently hovered token. */
const TOKEN_HIGHLIGHT = 'token-highlight';

const UPDATE_TOKEN_TASK_DELAY_MS = 50;

/**
 * When a user hovers over a token in the diff, then this layer makes sure that
 * all occurrences of this token are annotated with the 'token-highlight' css
 * class. And removes that class when the user moves the mouse away from the
 * token.
 *
 * The layer does not react to mouse events directly by adding a css class to
 * the appropriate elements, but instead it just sets the currently highlighted
 * token and notifies the diff renderer that certain lines must be re-rendered.
 * And when that re-rendering happens the appropriate css class is added.
 */
export class TokenHighlightLayer implements DiffLayer {
  /** The only listener is typically the renderer of gr-diff. */
  private listeners: DiffLayerListener[] = [];

  /** The currently highlighted token. */
  private currentHighlight?: string;

  /**
   * The line of the currently highlighted token. We store this in order to
   * re-render only relevant lines of the diff. Only lines visible on the screen
   * need a highlight. For example in a file with 10,000 lines it is sufficient
   * to just re-render the ~100 lines that are visible to the user.
   *
   * It is a known issue that we are only storing the line number on the side of
   * where the user is hovering and we use that also to determine which line
   * numbers to re-render on the other side, but it is non-trivial to look up or
   * store a reliable mapping of line numbers, so we just accept this
   * shortcoming with the reasoning that the user is mostly interested in the
   * tokens on the side where they are hovering anyway.
   */
  private currentHighlightLineNumber = 0;

  /**
   * Keeps track of where tokens occur in a file during rendering, so that it is
   * easy to look up when processing mouse events.
   */
  private tokenToLinesLeft = new Map<string, Set<number>>();

  private tokenToLinesRight = new Map<string, Set<number>>();

  private updateTokenTask?: DelayedTask;

  annotate(
    el: HTMLElement,
    _: HTMLElement,
    line: GrDiffLine,
    side: Side
  ): void {
    const text = el.textContent;
    if (!text) return;
    // Binary files encoded as text for example can have super long lines
    // with super long tokens. Let's guard against against this scenario.
    if (text.length > 500) return;
    let match;
    let atLeastOneTokenMatched = false;
    while ((match = tokenMatcher.exec(text))) {
      const token = match[0];
      const index = match.index;
      // Binary files encoded as text for example can have super long lines
      // with super long tokens. Let's guard against against this scenario.
      if (token.length > 100) continue;
      atLeastOneTokenMatched = true;
      GrAnnotation.annotateElement(el, index, token.length, TOKEN);
      if (token === this.currentHighlight) {
        GrAnnotation.annotateElement(el, index, token.length, TOKEN_HIGHLIGHT);
      }
      // We could try to detect whether we are re-rendering instead of initially
      // rendering the line. Then we would not have to call storeLineForToken()
      // again. But since the Set swallows the duplicates we don't care.
      this.storeLineForToken(token, line, side);
    }
    if (atLeastOneTokenMatched) {
      // These listeners do not have to be cleaned, because listeners are
      // garbage collected along with the element itself once it is not attached
      // to the DOM anymore and no references exist anymore.
      el.addEventListener('mouseover', this.handleMouseOver);
      el.addEventListener('mouseout', this.handleMouseOut);
    }
  }

  private storeLineForToken(token: string, line: GrDiffLine, side: Side) {
    const tokenToLines =
      side === Side.LEFT ? this.tokenToLinesLeft : this.tokenToLinesRight;
    // Just to make sure that we don't break down on large files.
    if (tokenToLines.size > 10000) return;
    let numbers = tokenToLines.get(token);
    if (!numbers) {
      numbers = new Set<number>();
      tokenToLines.set(token, numbers);
    }
    // Just to make sure that we don't break down on large files.
    if (numbers.size > 1000) return;
    const lineNumber =
      side === Side.LEFT ? line.beforeNumber : line.afterNumber;
    numbers.add(Number(lineNumber));
  }

  private readonly handleMouseOut = (e: MouseEvent) => {
    if (!this.currentHighlight) return;
    const el = this.findTokenAncestor(e?.target);
    if (!el) return;
    this.updateTokenHighlight(undefined, undefined);
  };

  private readonly handleMouseOver = (e: MouseEvent) => {
    const el = this.findTokenAncestor(e?.target);
    if (!el?.textContent) return;
    const oldHighlight = this.currentHighlight;
    const newHighlight = el.textContent;
    if (!newHighlight || newHighlight === oldHighlight) return;
    this.updateTokenHighlight(el, newHighlight);
  };

  private updateTokenHighlight(
    el: Element | undefined,
    newHighlight: string | undefined
  ) {
    this.updateTokenTask = debounce(
      this.updateTokenTask,
      () => {
        const newLineNumber = lineNumberToNumber(getLineNumberByChild(el));
        const oldHighlight = this.currentHighlight;
        const oldLineNumber = this.currentHighlightLineNumber;
        this.currentHighlight = newHighlight;
        this.currentHighlightLineNumber = newLineNumber;
        this.notifyForToken(oldHighlight, oldLineNumber);
        this.notifyForToken(newHighlight, newLineNumber);
      },
      UPDATE_TOKEN_TASK_DELAY_MS
    );
  }

  findTokenAncestor(el?: EventTarget | Element | null): Element | undefined {
    if (!(el instanceof Element)) return undefined;
    if (el.classList.contains(TOKEN)) return el;
    if (el.tagName === 'TD') return undefined;
    return this.findTokenAncestor(el.parentElement);
  }

  notifyForToken(token: string | undefined, lineNumber: number) {
    if (!token) return;
    const linesLeft = this.tokenToLinesLeft.get(token);
    linesLeft?.forEach(line => {
      if (Math.abs(line - lineNumber) < 100) {
        this.notifyListeners(line, Side.LEFT);
      }
    });
    const linesRight = this.tokenToLinesRight.get(token);
    linesRight?.forEach(line => {
      if (Math.abs(line - lineNumber) < 100) {
        this.notifyListeners(line, Side.RIGHT);
      }
    });
  }

  addListener(listener: DiffLayerListener) {
    this.listeners.push(listener);
  }

  removeListener(listener: DiffLayerListener) {
    this.listeners = this.listeners.filter(f => f !== listener);
  }

  notifyListeners(line: number, side: Side) {
    for (const listener of this.listeners) {
      listener(line, line, side);
    }
  }
}
