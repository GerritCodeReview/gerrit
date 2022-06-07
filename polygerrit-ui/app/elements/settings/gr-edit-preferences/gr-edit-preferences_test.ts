/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-edit-preferences';
import {queryAll, stubRestApi} from '../../../test/test-utils';
import {GrEditPreferences} from './gr-edit-preferences';
import {EditPreferencesInfo, ParsedJSON} from '../../../types/common';
import {IronInputElement} from '@polymer/iron-input';
import {createDefaultEditPrefs} from '../../../constants/constants';

const basicFixture = fixtureFromElement('gr-edit-preferences');

suite('gr-edit-preferences tests', () => {
  let element: GrEditPreferences;
  let editPreferences: EditPreferencesInfo;

  function valueOf(title: string, id: string): Element {
    const sections = queryAll(element, `#${id} section`) ?? [];
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
    editPreferences = createDefaultEditPrefs();

    stubRestApi('getEditPreferences').returns(Promise.resolve(editPreferences));

    element = basicFixture.instantiate();

    await element.updateComplete;
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

    assert.isFalse(element.hasUnsavedChanges());
  });

  test('save changes', async () => {
    assert.isTrue(element.editPrefs?.show_tabs);

    const showTabsCheckbox = valueOf('Show tabs', 'editPreferences')
      .firstElementChild as HTMLInputElement;
    showTabsCheckbox.checked = false;
    element.handleEditShowTabsChanged();

    assert.isTrue(element.hasUnsavedChanges());

    const getResponseObjStub = stubRestApi('getResponseObject').returns(
      Promise.resolve(element.editPrefs! as unknown as ParsedJSON)
    );

    await element.save();

    assert.isTrue(getResponseObjStub.called);

    assert.isFalse(element.editPrefs?.show_tabs);

    assert.isFalse(element.hasUnsavedChanges());
  });
});
