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
import './gr-menu-editor';
import {GrMenuEditor} from './gr-menu-editor';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {query, queryAll} from '../../../test/test-utils';
import {PaperButtonElement} from '@polymer/paper-button';
import {TopMenuItemInfo} from '../../../types/common';

const basicFixture = fixtureFromElement('gr-menu-editor');

suite('gr-menu-editor tests', () => {
  let element: GrMenuEditor;
  let menu: TopMenuItemInfo[];

  function assertMenuNamesEqual(
    element: GrMenuEditor,
    expected: Array<string>
  ) {
    const names = element.menuItems.map(i => i.name);
    assert.equal(names.length, expected.length);
    for (let i = 0; i < names.length; i++) {
      assert.equal(names[i], expected[i]);
    }
  }

  // Click the up/down button (according to direction) for the index'th row.
  // The index of the first row is 0, corresponding to the array.
  function move(element: GrMenuEditor, index: number, direction: string) {
    const selector = `tr:nth-child(${index + 1}) .move${direction}Button`;

    const button = query<PaperButtonElement>(
      query<HTMLElement>(query<HTMLTableElement>(element, 'tbody'), selector),
      'paper-button'
    );
    MockInteractions.tap(button!);
  }

  setup(async () => {
    element = basicFixture.instantiate();
    menu = [
      {url: '/first/url', name: 'first name', target: '_blank'},
      {url: '/second/url', name: 'second name', target: '_blank'},
      {url: '/third/url', name: 'third name', target: '_blank'},
    ];
    element.set('menuItems', menu);
    await flush();
  });

  test('renders', () => {
    const rows = queryAll(query<HTMLElement>(element, 'tbody')!, 'tr');
    let tds;

    assert.equal(rows.length, menu.length);
    for (let i = 0; i < menu.length; i++) {
      tds = rows[i].querySelectorAll('td');
      assert.equal(tds[0].textContent, menu[i].name);
      assert.equal(tds[1].textContent, menu[i].url);
    }

    assert.isTrue(
      element._computeAddDisabled(element._newName, element._newUrl)
    );
  });

  test('_computeAddDisabled', () => {
    assert.isTrue(element._computeAddDisabled('', ''));
    assert.isTrue(element._computeAddDisabled('name', ''));
    assert.isTrue(element._computeAddDisabled('', 'url'));
    assert.isFalse(element._computeAddDisabled('name', 'url'));
  });

  test('add a new menu item', () => {
    const newName = 'new name';
    const newUrl = 'new url';

    element._newName = newName;
    element._newUrl = newUrl;
    assert.isFalse(
      element._computeAddDisabled(element._newName, element._newUrl)
    );

    const originalMenuLength = element.menuItems.length;

    element._handleAddButton();

    assert.equal(element.menuItems.length, originalMenuLength + 1);
    assert.equal(element.menuItems[element.menuItems.length - 1].name, newName);
    assert.equal(element.menuItems[element.menuItems.length - 1].url, newUrl);
  });

  test('move items down', () => {
    assertMenuNamesEqual(element, ['first name', 'second name', 'third name']);

    // Move the middle item down
    move(element, 1, 'Down');
    assertMenuNamesEqual(element, ['first name', 'third name', 'second name']);

    // Moving the bottom item down is a no-op.
    move(element, 2, 'Down');
    assertMenuNamesEqual(element, ['first name', 'third name', 'second name']);
  });

  test('move items up', () => {
    assertMenuNamesEqual(element, ['first name', 'second name', 'third name']);

    // Move the last item up twice to be the first.
    move(element, 2, 'Up');
    move(element, 1, 'Up');
    assertMenuNamesEqual(element, ['third name', 'first name', 'second name']);

    // Moving the top item up is a no-op.
    move(element, 0, 'Up');
    assertMenuNamesEqual(element, ['third name', 'first name', 'second name']);
  });

  test('remove item', () => {
    assertMenuNamesEqual(element, ['first name', 'second name', 'third name']);

    // Tap the delete button for the middle item.
    MockInteractions.tap(
      query<PaperButtonElement>(
        query<HTMLElement>(
          query<HTMLTableElement>(element, 'tbody'),
          'tr:nth-child(2) .remove-button'
        ),
        'paper-button'
      )!
    );

    assertMenuNamesEqual(element, ['first name', 'third name']);

    // Delete remaining items.
    for (let i = 0; i < 2; i++) {
      MockInteractions.tap(
        query<PaperButtonElement>(
          query<HTMLElement>(
            query<HTMLTableElement>(element, 'tbody'),
            'tr:first-child .remove-button'
          ),
          'paper-button'
        )!
      );
    }
    assertMenuNamesEqual(element, []);

    // Add item to empty menu.
    element._newName = 'new name';
    element._newUrl = 'new url';
    element._handleAddButton();
    assertMenuNamesEqual(element, ['new name']);
  });
});
