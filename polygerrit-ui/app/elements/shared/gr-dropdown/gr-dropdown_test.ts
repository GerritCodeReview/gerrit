/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-dropdown';
import {GrDropdown} from './gr-dropdown';
import {pressKey, queryAll, queryAndAssert} from '../../../test/test-utils';
import {GrTooltipContent} from '../gr-tooltip-content/gr-tooltip-content';
import {assertIsDefined} from '../../../utils/common-util';
import {assert, fixture, html} from '@open-wc/testing';
import {DropdownLink} from '../../../types/common';

suite('gr-dropdown tests', () => {
  let element: GrDropdown;

  setup(async () => {
    element = await fixture(html`<gr-dropdown></gr-dropdown>`);
  });

  test('tap on trigger opens menu, then closes', () => {
    sinon.stub(element, 'open').callsFake(() => {
      assertIsDefined(element.dropdown);
      element.dropdown.open();
    });
    sinon.stub(element, 'close').callsFake(() => {
      assertIsDefined(element.dropdown);
      element.dropdown.close();
    });
    assertIsDefined(element.dropdown);
    assertIsDefined(element.trigger);
    assert.isFalse(element.dropdown.opened);
    element.trigger.click();
    assert.isTrue(element.dropdown.opened);
    element.trigger.click();
    assert.isFalse(element.dropdown.opened);
  });

  test('_computeURLHelper', () => {
    const path = '/test';
    const host = 'http://www.testsite.com';
    const computedPath = element._computeURLHelper(host, path);
    assert.equal(computedPath, '//http://www.testsite.com/test');
  });

  test('link URLs', () => {
    assert.equal(
      element.computeLinkURL({url: 'http://example.com/test'}),
      'http://example.com/test'
    );
    assert.equal(
      element.computeLinkURL({url: 'https://example.com/test'}),
      'https://example.com/test'
    );
    assert.equal(
      element.computeLinkURL({url: '/test'}),
      '//' + window.location.host + '/test'
    );
    assert.equal(
      element.computeLinkURL({url: '/test', target: '_blank'}),
      '/test'
    );
  });

  test('link rel', () => {
    let link: DropdownLink = {url: '/test'};
    assert.isNull(element.computeLinkRel(link));

    link = {url: '/test', target: '_blank'};
    assert.equal(element.computeLinkRel(link), 'noopener');

    link = {url: '/test', external: true};
    assert.equal(element.computeLinkRel(link), 'external');

    link = {url: '/test', target: '_blank', external: true};
    assert.equal(element.computeLinkRel(link), 'noopener');
  });

  test('getClassIfBold', () => {
    let bold = true;
    assert.equal(element.getClassIfBold(bold), 'bold-text');

    bold = false;
    assert.equal(element.getClassIfBold(bold), '');
  });

  test('Top text exists and is bolded correctly', async () => {
    element.topContent = [{text: 'User', bold: true}, {text: 'email'}];
    await element.updateComplete;
    const topItems = queryAll<HTMLDivElement>(element, '.top-item');
    assert.equal(topItems.length, 2);
    assert.isTrue(topItems[0].classList.contains('bold-text'));
    assert.isFalse(topItems[1].classList.contains('bold-text'));
  });

  test('non link items', async () => {
    const item0 = {name: 'item one', id: 'foo'};
    element.items = [item0, {name: 'item two', id: 'bar'}];
    const fooTapped = sinon.stub();
    const tapped = sinon.stub();
    element.addEventListener('tap-item-foo', fooTapped);
    element.addEventListener('tap-item', tapped);
    await element.updateComplete;
    queryAndAssert<HTMLSpanElement>(element, '.itemAction').click();
    assert.isTrue(fooTapped.called);
    assert.isTrue(tapped.called);
    assert.deepEqual(tapped.lastCall.args[0].detail, item0);
  });

  test('disabled non link item', async () => {
    element.items = [{name: 'item one', id: 'foo'}];
    element.disabledIds = ['foo'];

    const stub = sinon.stub();
    const tapped = sinon.stub();
    element.addEventListener('tap-item-foo', stub);
    element.addEventListener('tap-item', tapped);
    await element.updateComplete;
    queryAndAssert<HTMLSpanElement>(element, '.itemAction').click();
    assert.isFalse(stub.called);
    assert.isFalse(tapped.called);
  });

  test('properly sets tooltips', async () => {
    element.items = [
      {name: 'item one', id: 'foo', tooltip: 'hello'},
      {name: 'item two', id: 'bar'},
    ];
    element.disabledIds = [];
    await element.updateComplete;
    const tooltipContents = queryAll<GrTooltipContent>(
      element,
      'iron-dropdown li gr-tooltip-content'
    );
    assert.equal(tooltipContents.length, 2);
    assert.isTrue(tooltipContents[0].hasTooltip);
    assert.equal(tooltipContents[0].getAttribute('title'), 'hello');
    assert.isFalse(tooltipContents[1].hasTooltip);
  });

  test('render', async () => {
    element.items = [
      {name: 'item one', id: 'foo', tooltip: 'hello'},
      {name: 'item two', id: 'bar', url: 'http://bar'},
    ];
    element.disabledIds = [];
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
      <gr-button
        aria-disabled="false"
        class="dropdown-trigger"
        id="trigger"
        role="button"
        tabindex="0"
      >
        <slot>
        </slot>
      </gr-button>
      <iron-dropdown
        allowoutsidescroll=""
        aria-disabled="false"
        aria-hidden="true"
        horizontal-align="left"
        id="dropdown"
        style="outline: none; display: none;"
        vertical-align="top"
      >
        <div
          class="dropdown-content"
          slot="dropdown-content"
        >
          <ul>
            <li tabindex="-1">
              <gr-tooltip-content
                has-tooltip=""
                title="hello"
              >
                <span
                  class="itemAction"
                  data-id="foo"
                  tabindex="-1"
                >
                  item one
                </span>
                <a
                  class="itemAction"
                  hidden=""
                  href=""
                  tabindex="-1"
                >
                  item one
                </a>
              </gr-tooltip-content>
            </li>
            <li tabindex="-1">
              <gr-tooltip-content>
                <span
                  class="itemAction"
                  data-id="bar"
                  hidden=""
                  tabindex="-1"
                >
                  item two
                </span>
                <a
                  class="itemAction"
                  href="http://bar"
                  tabindex="-1"
                >
                  item two
                </a>
              </gr-tooltip-content>
            </li>
        </div>
          </ul>
      </iron-dropdown>`
    );
  });

  suite('keyboard navigation', () => {
    setup(async () => {
      element.items = [
        {name: 'item one', id: 'foo', url: 'http://foo'},
        {name: 'item two', id: 'bar', url: 'http://bar'},
      ];
      await element.updateComplete;
    });

    test('down', () => {
      const stub = sinon.stub(element.cursor, 'next');
      assertIsDefined(element.dropdown);
      assert.isFalse(element.dropdown.opened);
      pressKey(element, 'ArrowDown');
      assert.isTrue(element.dropdown.opened);
      pressKey(element, 'ArrowDown');
      assert.isTrue(stub.called);
    });

    test('up', () => {
      assertIsDefined(element.dropdown);
      const stub = sinon.stub(element.cursor, 'previous');
      assert.isFalse(element.dropdown.opened);
      pressKey(element, 'ArrowUp');
      assert.isTrue(element.dropdown.opened);
      pressKey(element, 'ArrowUp');
      assert.isTrue(stub.called);
    });

    test('enter/space', async () => {
      assertIsDefined(element.dropdown);
      // Because enter and space are handled by the same fn, we need only to
      // test one.
      assert.isFalse(element.dropdown.opened);
      pressKey(element, ' ');
      await element.updateComplete;
      assert.isTrue(element.dropdown.opened);

      const el = queryAndAssert<HTMLAnchorElement>(
        element.cursor.target as HTMLElement,
        ':not([hidden]) a'
      );
      const stub = sinon.stub(el, 'click');
      pressKey(element, ' ');
      assert.isTrue(stub.called);
    });
  });
});
