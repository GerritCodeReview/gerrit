/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
import './gr-edit-preferences';
import {stubRestApi} from '../../../test/test-utils';
import {GrEditPreferences} from './gr-edit-preferences';
import {EditPreferencesInfo} from '../../../types/common';
import {IronInputElement} from '@polymer/iron-input';

const basicFixture = fixtureFromElement('gr-edit-preferences');

suite('gr-edit-preferences tests', () => {
  let element: GrEditPreferences;

  let editPreferences: EditPreferencesInfo;

  function valueOf(title: string, id: string): Element {
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
    editPreferences = {
      auto_close_brackets: false,
      cursor_blink_rate: 0,
      hide_line_numbers: false,
      hide_top_menu: false,
      indent_unit: 2,
      indent_with_tabs: false,
      key_map_type: 'DEFAULT',
      line_length: 100,
      line_wrapping: false,
      match_brackets: true,
      show_base: false,
      show_tabs: true,
      show_whitespace_errors: true,
      syntax_highlighting: true,
      tab_size: 8,
      theme: 'DEFAULT',
    };

    stubRestApi('getEditPreferences').returns(Promise.resolve(editPreferences));

    element = basicFixture.instantiate();

    await element.loadData();
    await flush();
  });

  test('renders', () => {
    // Rendered with the expected preferences selected.
    const tabWidthInput = valueOf('Tab width', 'editPreferences')
      .firstElementChild as IronInputElement;
    assert.equal(tabWidthInput.bindValue, `${editPreferences.tab_size}`);

    const columnsInput = valueOf('Columns', 'editPreferences')
      .firstElementChild as IronInputElement;
    assert.equal(columnsInput.bindValue, `${editPreferences.line_length}`);

    const indentInput = valueOf('Indent unit', 'editPreferences')
      .firstElementChild as IronInputElement;
    assert.equal(indentInput.bindValue, `${editPreferences.indent_unit}`);

    const syntaxInput = valueOf('Syntax highlighting', 'editPreferences')
      .firstElementChild as HTMLInputElement;
    assert.equal(syntaxInput.checked, editPreferences.syntax_highlighting);

    const tabsInput = valueOf('Show tabs', 'editPreferences')
      .firstElementChild as HTMLInputElement;
    assert.equal(tabsInput.checked, editPreferences.show_tabs);

    const bracketsInput = valueOf('Match brackets', 'editPreferences')
      .firstElementChild as HTMLInputElement;
    assert.equal(bracketsInput.checked, editPreferences.match_brackets);

    const wrappingInput = valueOf('Line wrapping', 'editPreferences')
      .firstElementChild as HTMLInputElement;
    assert.equal(wrappingInput.checked, editPreferences.line_wrapping);

    const indentTabsInput = valueOf('Indent with tabs', 'editPreferences')
      .firstElementChild as HTMLInputElement;
    assert.equal(indentTabsInput.checked, editPreferences.indent_with_tabs);

    const autoCloseInput = valueOf('Auto close brackets', 'editPreferences')
      .firstElementChild as HTMLInputElement;
    assert.equal(autoCloseInput.checked, editPreferences.auto_close_brackets);

    assert.isFalse(element.hasUnsavedChanges);
  });

  test('save changes', async () => {
    const showTabsCheckbox = valueOf('Show tabs', 'editPreferences')
      .firstElementChild as HTMLInputElement;
    showTabsCheckbox.checked = false;
    element._handleEditShowTabsChanged();

    assert.isTrue(element.hasUnsavedChanges);

    await element.save();
    assert.isFalse(element.hasUnsavedChanges);
  });
});
