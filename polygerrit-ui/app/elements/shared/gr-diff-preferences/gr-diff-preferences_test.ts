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

import '../../../test/common-test-setup-karma';
import './gr-diff-preferences';
import {GrDiffPreferences} from './gr-diff-preferences';
import {stubRestApi} from '../../../test/test-utils';
import {DiffPreferencesInfo} from '../../../types/diff';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {IronInputElement} from '@polymer/iron-input';
import {GrSelect} from '../gr-select/gr-select';

const basicFixture = fixtureFromElement('gr-diff-preferences');

suite('gr-diff-preferences tests', () => {
  let element: GrDiffPreferences;

  let diffPreferences: DiffPreferencesInfo;

  function valueOf(title: string, id: string) {
    const sections = element.root?.querySelectorAll(`#${id} section`) ?? [];
    let titleEl;
    for (let i = 0; i < sections.length; i++) {
      titleEl = sections[i].querySelector('.title');
      if (titleEl?.textContent?.trim() === title) {
        const el = sections[i].querySelector('.value');
        if (el) return el;
      }
    }
    assert.fail(`element with title ${title} not found`);
  }

  setup(async () => {
    diffPreferences = createDefaultDiffPrefs();

    stubRestApi('getDiffPreferences').returns(Promise.resolve(diffPreferences));

    element = basicFixture.instantiate();

    await flush();
  });

  test('renders', () => {
    // Rendered with the expected preferences selected.
    const contextInput = valueOf('Context', 'diffPreferences')
      .firstElementChild as IronInputElement;
    assert.equal(contextInput.bindValue, `${diffPreferences.context}`);

    const lineWrappingInput = valueOf('Fit to screen', 'diffPreferences')
      .firstElementChild as HTMLInputElement;
    assert.equal(lineWrappingInput.checked, diffPreferences.line_wrapping);

    const lineLengthInput = valueOf('Diff width', 'diffPreferences')
      .firstElementChild as IronInputElement;
    assert.equal(lineLengthInput.bindValue, `${diffPreferences.line_length}`);

    const tabSizeInput = valueOf('Tab width', 'diffPreferences')
      .firstElementChild as IronInputElement;
    assert.equal(tabSizeInput.bindValue, `${diffPreferences.tab_size}`);

    const fontSizeInput = valueOf('Font size', 'diffPreferences')
      .firstElementChild as IronInputElement;
    assert.equal(fontSizeInput.bindValue, `${diffPreferences.font_size}`);

    const showTabsInput = valueOf('Show tabs', 'diffPreferences')
      .firstElementChild as HTMLInputElement;
    assert.equal(showTabsInput.checked, diffPreferences.show_tabs);

    const showWhitespaceErrorsInput = valueOf(
      'Show trailing whitespace',
      'diffPreferences'
    ).firstElementChild as HTMLInputElement;
    assert.equal(
      showWhitespaceErrorsInput.checked,
      diffPreferences.show_whitespace_errors
    );

    const syntaxHighlightingInput = valueOf(
      'Syntax highlighting',
      'diffPreferences'
    ).firstElementChild as HTMLInputElement;
    assert.equal(
      syntaxHighlightingInput.checked,
      diffPreferences.syntax_highlighting
    );

    const manualReviewInput = valueOf(
      'Automatically mark viewed files reviewed',
      'diffPreferences'
    ).firstElementChild as HTMLInputElement;
    assert.equal(manualReviewInput.checked, !diffPreferences.manual_review);

    const ignoreWhitespaceInput = valueOf(
      'Ignore Whitespace',
      'diffPreferences'
    ).firstElementChild as GrSelect;
    assert.equal(
      ignoreWhitespaceInput.bindValue,
      diffPreferences.ignore_whitespace
    );

    assert.isFalse(element.hasUnsavedChanges);
  });

  test('save changes', async () => {
    const showTrailingWhitespaceCheckbox = valueOf(
      'Show trailing whitespace',
      'diffPreferences'
    ).firstElementChild as HTMLInputElement;
    showTrailingWhitespaceCheckbox.checked = false;
    element._handleShowTrailingWhitespaceTap();

    assert.isTrue(element.hasUnsavedChanges);

    // Save the change.
    await element.save();
    assert.isFalse(element.hasUnsavedChanges);
  });
});
