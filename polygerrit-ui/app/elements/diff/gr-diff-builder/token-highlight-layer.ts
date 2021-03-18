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

const tokenMatcher = new RegExp(/\w+/g);

/** CSS class for all tokens. */
const TOKEN = 'token';

/** CSS class for the currently hovered token. */
const TOKEN_HIGHLIGHT = 'token-highlight';

/**
 * When a user hovers over a token in the diff, then this layer makes sure that
 * all occurences of this token are annotated with the 'token-highlight' css
 * class. And removes that class when the user moves the mouse away from the
 * token.
 *
 * The layer does not react to mouse events directly by adding a css class to
 * the appropriate elements, but instead it just sets the currently highlighted
 * token and notifies the diff renderer that certain lines must be rerendered.
 * And when that rerendering happens the appropriate css class is added.
 */
export class TokenHighlightLayer implements DiffLayer {
  /** The only listener is typically the renderer of gr-diff. */
  private listeners: DiffLayerListener[] = [];

  /** The currently highlighted token. */
  private currentHighlight?: string;

  /**
   * Keeps track of where tokens occur in a file during rendering, so that it is
   * easy to look up when processing mouse events.
   */
  private tokenToLines = new Map<string, Set<number>>();

  annotate(el: HTMLElement, _: HTMLElement, line: GrDiffLine): void {
    const text = el.textContent;
    if (!text) return;
    let match;
    while ((match = tokenMatcher.exec(text))) {
      const token = match[0];
      const index = match.index;
      GrAnnotation.annotateElement(el, index, token.length, TOKEN);
      if (token === this.currentHighlight) {
        GrAnnotation.annotateElement(el, index, token.length, TOKEN_HIGHLIGHT);
      }
      this.storeLinesForToken(token, line);
    }
    el.addEventListener('mouseover', this.handleMouseOver);
    el.addEventListener('mouseout', this.handleMouseOut);
  }

  private storeLinesForToken(token: string, line: GrDiffLine) {
    let numbers = this.tokenToLines.get(token);
    if (!numbers) {
      numbers = new Set<number>();
      this.tokenToLines.set(token, numbers);
    }
    // The annotate() method provides no clue about LEFT or RIGHT, so just
    // store both line numbers. That is obviously not perfectly optimized. :-)
    numbers.add(Number(line.beforeNumber));
    numbers.add(Number(line.afterNumber));
  }

  private readonly handleMouseOut = (e: MouseEvent) => {
    if (!this.currentHighlight) return;
    const el = this.findTokenAncestor(e?.target);
    if (!el) return;
    const oldHighlight = this.currentHighlight;
    const newHighlight = undefined;
    this.currentHighlight = newHighlight;
    this.notifyForToken(oldHighlight);
  };

  private readonly handleMouseOver = (e: MouseEvent) => {
    const el = this.findTokenAncestor(e?.target);
    if (!el?.textContent) return;
    const oldHighlight = this.currentHighlight;
    const newHighlight = el.textContent;
    if (!newHighlight || newHighlight === oldHighlight) return;
    this.currentHighlight = newHighlight;
    this.notifyForToken(oldHighlight);
    this.notifyForToken(newHighlight);
  };

  findTokenAncestor(el?: EventTarget | Element | null): Element | undefined {
    if (!(el instanceof Element)) return undefined;
    if (el.classList.contains(TOKEN)) return el;
    if (el.tagName === 'TD') return undefined;
    return this.findTokenAncestor(el.parentElement);
  }

  notifyForToken(token?: string) {
    if (!token) return;
    const lines = this.tokenToLines.get(token);
    for (const line of lines ?? []) {
      this.notifyListeners(line);
    }
  }

  addListener(listener: DiffLayerListener) {
    this.listeners.push(listener);
  }

  removeListener(listener: DiffLayerListener) {
    this.listeners = this.listeners.filter(f => f !== listener);
  }

  notifyListeners(line: number) {
    for (const listener of this.listeners) {
      // The annotate() method provides no clue about LEFT or RIGHT, so just
      // notify both. That is obviously not perfectly optimized. :-)
      listener(line, line, Side.LEFT);
      listener(line, line, Side.RIGHT);
    }
  }
}
