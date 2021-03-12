/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-coverage-layer_html';
import {CoverageRange, CoverageType, DiffLayer} from '../../../types/types';
import {customElement, property} from '@polymer/decorators';

declare global {
  interface HTMLElementTagNameMap {
    'gr-coverage-layer': GrCoverageLayer;
  }
}

const TOOLTIP_MAP = new Map([
  [CoverageType.COVERED, 'Covered by tests.'],
  [CoverageType.NOT_COVERED, 'Not covered by tests.'],
  [CoverageType.PARTIALLY_COVERED, 'Partially covered by tests.'],
  [CoverageType.NOT_INSTRUMENTED, 'Not instrumented by any tests.'],
]);

@customElement('gr-coverage-layer')
export class GrCoverageLayer extends PolymerElement implements DiffLayer {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Must be sorted by code_range.start_line.
   * Must only contain ranges that match the side.
   */
  @property({type: Array})
  coverageRanges: CoverageRange[] = [];

  @property({type: String})
  side?: string;

  /**
   * We keep track of the line number from the previous annotate() call,
   * and also of the index of the coverage range that had matched.
   * annotate() calls are coming in with increasing line numbers and
   * coverage ranges are sorted by line number. So this is a very simple
   * and efficient way for finding the coverage range that matches a given
   * line number.
   */
  @property({type: Number})
  _lineNumber = 0;

  @property({type: Number})
  _index = 0;

  /**
   * Layer method to add annotations to a line.
   *
   * @param _el Not used for this layer. (unused parameter)
   * @param lineNumberEl The <td> element with the line number.
   * @param line Not used for this layer.
   */
  annotate(_el: HTMLElement, lineNumberEl: HTMLElement) {
    if (
      !this.side ||
      !lineNumberEl ||
      !lineNumberEl.classList.contains(this.side)
    ) {
      return;
    }
    let elementLineNumber;
    const dataValue = lineNumberEl.getAttribute('data-value');
    if (dataValue) {
      elementLineNumber = Number(dataValue);
    }
    if (!elementLineNumber || elementLineNumber < 1) return;

    // If the line number is smaller than before, then we have to reset our
    // algorithm and start searching the coverage ranges from the beginning.
    // That happens for example when you expand diff sections.
    if (elementLineNumber < this._lineNumber) {
      this._index = 0;
    }
    this._lineNumber = elementLineNumber;

    // We simply loop through all the coverage ranges until we find one that
    // matches the line number.
    while (this._index < this.coverageRanges.length) {
      const coverageRange = this.coverageRanges[this._index];

      // If the line number has moved past the current coverage range, then
      // try the next coverage range.
      if (this._lineNumber > coverageRange.code_range.end_line) {
        this._index++;
        continue;
      }

      // If the line number has not reached the next coverage range (and the
      // range before also did not match), then this line has not been
      // instrumented. Nothing to do for this line.
      if (this._lineNumber < coverageRange.code_range.start_line) {
        return;
      }

      // The line number is within the current coverage range. Style it!
      lineNumberEl.classList.add(coverageRange.type);
      lineNumberEl.title = TOOLTIP_MAP.get(coverageRange.type) || '';
      return;
    }
  }
}
