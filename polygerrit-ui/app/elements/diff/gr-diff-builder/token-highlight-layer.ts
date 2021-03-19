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
import {appContext} from '../../../services/app-context';
import {KnownExperimentId} from '../../../services/flags/flags';

const tokenMatcher = new RegExp(/[a-zA-Z0-9_-]+/g);

/** CSS class for all tokens. */
const CSS_TOKEN = 'token';

/** CSS class for the currently hovered token. */
const CSS_HIGHLIGHT = 'token-highlight';

const UPDATE_TOKEN_TASK_DELAY_MS = 50;

const LINE_LENGTH_LIMIT = 500;

const TOKEN_LENGTH_LIMIT = 100;

const TOKEN_COUNT_LIMIT = 10000;

const TOKEN_OCCURRENCES_LIMIT = 1000;

/**
 * Token highlighting is only useful for code on-screen, so don't bother
 * highlighting tokens that are further away than this threshold from where the
 * user is hovering.
 */
const LINE_DISTANCE_THRESHOLD = 100;

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
   *
   * Another known issue is that we are not able to see past collapsed lines
   * with the current implementation.
   */
  private currentHighlightLineNumber = 0;

  /**
   * Keeps track of where tokens occur in a file during rendering, so that it is
   * easy to look up when processing mouse events.
   */
  private tokenToLinesLeft = new Map<string, Set<number>>();

  private tokenToLinesRight = new Map<string, Set<number>>();

  private updateTokenTask?: DelayedTask;

  private readonly enabled = appContext.flagsService.isEnabled(
    KnownExperimentId.TOKEN_HIGHLIGHTING
  );

  annotate(
    el: HTMLElement,
    _: HTMLElement,
    line: GrDiffLine,
    side: Side
  ): void {
    if (!this.enabled) return;
    const text = el.textContent;
    if (!text) return;
    // Binary files encoded as text for example can have super long lines
    // with super long tokens. Let's guard against against this scenario.
    if (text.length > LINE_LENGTH_LIMIT) return;
    let match;
    let atLeastOneTokenMatched = false;
    while ((match = tokenMatcher.exec(text))) {
      const token = match[0];
      const index = match.index;
      const length = token.length;
      // Binary files encoded as text for example can have super long lines
      // with super long tokens. Let's guard against this scenario.
      if (length > TOKEN_LENGTH_LIMIT) continue;
      atLeastOneTokenMatched = true;
      const css = token === this.currentHighlight ? CSS_HIGHLIGHT : CSS_TOKEN;
      // We add the tk-* class so that we can look up the token later easily
      // even if the token element was split up into multiple smaller nodes.
      GrAnnotation.annotateElement(el, index, length, `tk-${token} ${css}`);
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
    if (tokenToLines.size > TOKEN_COUNT_LIMIT) return;
    let numbers = tokenToLines.get(token);
    if (!numbers) {
      numbers = new Set<number>();
      tokenToLines.set(token, numbers);
    }
    // Just to make sure that we don't break down on large files.
    if (numbers.size > TOKEN_OCCURRENCES_LIMIT) return;
    const lineNumber =
      side === Side.LEFT ? line.beforeNumber : line.afterNumber;
    numbers.add(Number(lineNumber));
  }

  private readonly handleMouseOut = (e: MouseEvent) => {
    if (!this.currentHighlight) return;
    if (this.interferesWithSelection(e)) return;
    const el = this.findTokenAncestor(e?.target);
    if (!el) return;
    this.updateTokenHighlight(undefined, undefined);
  };

  private readonly handleMouseOver = (e: MouseEvent) => {
    if (this.interferesWithSelection(e)) return;
    const {line, token} = this.findTokenAncestor(e?.target);
    if (!token) return;
    const oldHighlight = this.currentHighlight;
    const newHighlight = token;
    if (!newHighlight || newHighlight === oldHighlight) return;
    if (this.countOccurrences(newHighlight) <= 1) return;
    this.updateTokenHighlight(line, newHighlight);
  };

  private interferesWithSelection(e: MouseEvent) {
    if (e.buttons > 0) return true;
    if (window.getSelection()?.type === 'Range') return true;
    return false;
  }

  private updateTokenHighlight(
    newLineNumber: number | undefined,
    newHighlight: string | undefined
  ) {
    this.updateTokenTask = debounce(
      this.updateTokenTask,
      () => {
        const oldHighlight = this.currentHighlight;
        const oldLineNumber = this.currentHighlightLineNumber;
        this.currentHighlight = newHighlight;
        this.currentHighlightLineNumber = newLineNumber ?? 0;
        this.notifyForToken(oldHighlight, oldLineNumber);
        this.notifyForToken(newHighlight, newLineNumber ?? 0);
      },
      UPDATE_TOKEN_TASK_DELAY_MS
    );
  }

  findTokenAncestor(
    el?: EventTarget | Element | null
  ): {
    token?: string;
    line: number;
  } {
    if (!(el instanceof Element)) return {line: 0, token: undefined};
    if (
      el.classList.contains(CSS_TOKEN) ||
      el.classList.contains(CSS_HIGHLIGHT)
    ) {
      const tkClass = [...el.classList].find(c => c.startsWith('tk-'));
      const line = lineNumberToNumber(getLineNumberByChild(el));
      if (!line || !tkClass) return {line: 0, token: undefined};
      return {line, token: tkClass.substring(3)};
    }
    if (el.tagName === 'TD') return {line: 0, token: undefined};
    return this.findTokenAncestor(el.parentElement);
  }

  countOccurrences(token: string | undefined) {
    if (!token) return 0;
    const linesLeft = this.tokenToLinesLeft.get(token);
    const linesRight = this.tokenToLinesRight.get(token);
    return (linesLeft?.size ?? 0) + (linesRight?.size ?? 0);
  }

  notifyForToken(token: string | undefined, lineNumber: number) {
    if (!token) return;
    const linesLeft = this.tokenToLinesLeft.get(token);
    linesLeft?.forEach(line => {
      if (Math.abs(line - lineNumber) < LINE_DISTANCE_THRESHOLD) {
        this.notifyListeners(line, Side.LEFT);
      }
    });
    const linesRight = this.tokenToLinesRight.get(token);
    linesRight?.forEach(line => {
      if (Math.abs(line - lineNumber) < LINE_DISTANCE_THRESHOLD) {
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
