/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup-karma';
import './gr-dropdown-list';
import {GrDropdownList} from './gr-dropdown-list';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {query, queryAll, queryAndAssert} from '../../../test/test-utils';
import {PaperListboxElement} from '@polymer/paper-listbox';
import {Timestamp} from '../../../types/common';

const basicFixture = fixtureFromElement('gr-dropdown-list');

suite('gr-dropdown-list tests', () => {
  let element: GrDropdownList;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('hide copy by default', () => {
    const copyEl = query<HTMLElement>(
      element,
      '#triggerText + gr-copy-clipboard'
    )!;
    assert.isOk(copyEl);
    assert.isTrue(copyEl.hidden);
  });

  test('show copy if enabled', () => {
    element.showCopyForTriggerText = true;
    flush();
    const copyEl = query<HTMLElement>(
      element,
      '#triggerText + gr-copy-clipboard'
    )!;
    assert.isOk(copyEl);
    assert.isFalse(copyEl.hidden);
  });

  test('tap on trigger opens menu', () => {
    sinon.stub(element, 'open').callsFake(() => {
      element.$.dropdown.open();
    });
    assert.isFalse(element.$.dropdown.opened);
    MockInteractions.tap(element.$.trigger);
    assert.isTrue(element.$.dropdown.opened);
  });

  test('_computeMobileText', () => {
    const item: any = {
      value: 1,
      text: 'text',
    };
    assert.equal(element._computeMobileText(item), item.text);
    item.mobileText = 'mobile text';
    assert.equal(element._computeMobileText(item), item.mobileText);
  });

  test('options are selected and laid out correctly', async () => {
    element.value = 2;
    element.items = [
      {
        value: 1,
        text: 'Top Text 1',
      },
      {
        value: 2,
        bottomText: 'Bottom Text 2',
        triggerText: 'Button Text 2',
        text: 'Top Text 2',
        mobileText: 'Mobile Text 2',
      },
      {
        value: 3,
        disabled: true,
        bottomText: 'Bottom Text 3',
        triggerText: 'Button Text 3',
        date: '2017-08-18 23:11:42.569000000' as Timestamp,
        text: 'Top Text 3',
        mobileText: 'Mobile Text 3',
      },
    ];
    assert.equal(
      queryAndAssert<PaperListboxElement>(element, 'paper-listbox').selected,
      element.value
    );
    assert.equal(element.text, 'Button Text 2');
    await flush();

    const items = queryAll<HTMLInputElement>(element, 'paper-item');
    const mobileItems = queryAll<HTMLOptionElement>(element, 'option');
    assert.equal(items.length, 3);
    assert.equal(mobileItems.length, 3);

    // First Item
    // The first item should be disabled, has no bottom text, and no date.
    assert.isFalse(!!items[0].disabled);
    assert.isFalse(mobileItems[0].disabled);
    assert.isFalse(items[0].classList.contains('iron-selected'));
    assert.isFalse(mobileItems[0].selected);

    assert.isNotOk(items[0].querySelector('gr-date-formatter'));
    assert.isNotOk(items[0].querySelector('.bottomContent'));
    assert.equal(items[0].dataset.value, element.items[0].value as any);
    assert.equal(mobileItems[0].value, element.items[0].value);
    assert.equal(
      queryAndAssert<HTMLDivElement>(items[0], '.topContent div').innerText,
      element.items[0].text
    );

    // Since no mobile specific text, it should fall back to text.
    assert.equal(mobileItems[0].text, element.items[0].text);

    // Second Item
    // The second item should have top text, bottom text, and no date.
    assert.isFalse(!!items[1].disabled);
    assert.isFalse(mobileItems[1].disabled);
    assert.isTrue(items[1].classList.contains('iron-selected'));
    assert.isTrue(mobileItems[1].selected);

    assert.isNotOk(items[1].querySelector('gr-date-formatter'));
    assert.isOk(items[1].querySelector('.bottomContent'));
    assert.equal(items[1].dataset.value, element.items[1].value as any);
    assert.equal(mobileItems[1].value, element.items[1].value);
    assert.equal(
      queryAndAssert<HTMLDivElement>(items[1], '.topContent div').innerText,
      element.items[1].text
    );

    // Since there is mobile specific text, it should that.
    assert.equal(mobileItems[1].text, element.items[1].mobileText);

    // Since this item is selected, and it has triggerText defined, that
    // should be used.
    assert.equal(element.text, element.items[1].triggerText);

    // Third item
    // The third item should be disabled, and have a date, and bottom content.
    assert.isTrue(!!items[2].disabled);
    assert.isTrue(mobileItems[2].disabled);
    assert.isFalse(items[2].classList.contains('iron-selected'));
    assert.isFalse(mobileItems[2].selected);

    assert.isOk(items[2].querySelector('gr-date-formatter'));
    assert.isOk(items[2].querySelector('.bottomContent'));
    assert.equal(items[2].dataset.value, element.items[2].value as any);
    assert.equal(mobileItems[2].value, element.items[2].value);
    assert.equal(
      queryAndAssert<HTMLDivElement>(items[2], '.topContent div').innerText,
      element.items[2].text
    );

    // Since there is mobile specific text, it should that.
    assert.equal(mobileItems[2].text, element.items[2].mobileText);

    // Select a new item.
    MockInteractions.tap(items[0]);
    flush();
    assert.equal(element.value, 1);
    assert.isTrue(items[0].classList.contains('iron-selected'));
    assert.isTrue(mobileItems[0].selected);

    // Since no triggerText, the fallback is used.
    assert.equal(element.text, element.items[0].text);
  });
});
