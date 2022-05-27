/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import '../../../test/common-test-setup-karma';
import './gr-button';
import {addListener} from '@polymer/polymer/lib/utils/gestures';
import {fixture, html} from '@open-wc/testing-helpers';
import {GrButton} from './gr-button';
import {pressKey, queryAndAssert} from '../../../test/test-utils';
import {PaperButtonElement} from '@polymer/paper-button';
import {Key, Modifier} from '../../../utils/dom-util';

suite('gr-button tests', () => {
  let element: GrButton;

  const addSpyOn = function (eventName: string) {
    const spy = sinon.spy();
    if (eventName === 'tap') {
      addListener(element, eventName, spy);
    } else {
      element.addEventListener(eventName, spy);
    }
    return spy;
  };

  setup(async () => {
    element = await fixture<GrButton>('<gr-button></gr-button>');
    await element.updateComplete;
  });

  test('renders', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `
      <paper-button
        animated=""
        aria-disabled="false"
        elevation="1"
        part="paper-button"
        raised=""
        role="button"
        tabindex="-1"
        ><slot></slot><i class="downArrow"></i>
      </paper-button>
    `);
  });

  test('disabled is set by disabled', async () => {
    const paperBtn = queryAndAssert<PaperButtonElement>(
      element,
      'paper-button'
    );
    assert.isFalse(paperBtn.disabled);
    element.disabled = true;
    await element.updateComplete;
    assert.isTrue(paperBtn.disabled);
    element.disabled = false;
    await element.updateComplete;
    assert.isFalse(paperBtn.disabled);
  });

  test('loading set from listener', async () => {
    let resolve: Function;
    element.addEventListener('click', e => {
      const target = e.target as HTMLElement;
      target.setAttribute('loading', 'true');
      resolve = () => target.removeAttribute('loading');
    });
    const paperBtn = queryAndAssert<PaperButtonElement>(
      element,
      'paper-button'
    );
    assert.isFalse(paperBtn.disabled);
    MockInteractions.tap(element);
    await element.updateComplete;
    assert.isTrue(paperBtn.disabled);
    assert.isTrue(element.hasAttribute('loading'));
    resolve!();
    await element.updateComplete;
    assert.isFalse(paperBtn.disabled);
    assert.isFalse(element.hasAttribute('loading'));
  });

  test('tabindex should be -1 if disabled', async () => {
    element.disabled = true;
    await element.updateComplete;
    assert.equal(element.getAttribute('tabindex'), '-1');
  });

  // Regression tests for Issue: 11969
  test('tabindex should be reset to 0 if enabled', async () => {
    element.disabled = false;
    await element.updateComplete;
    assert.equal(element.getAttribute('tabindex'), '0');
    element.disabled = true;
    await element.updateComplete;
    assert.equal(element.getAttribute('tabindex'), '-1');
    element.disabled = false;
    await element.updateComplete;
    assert.equal(element.getAttribute('tabindex'), '0');
  });

  test('tabindex should be preserved', async () => {
    const tabIndexElement = await fixture<GrButton>(html`
      <gr-button tabindex="3"></gr-button>
    `);
    tabIndexElement.disabled = false;
    await element.updateComplete;
    assert.equal(tabIndexElement.getAttribute('tabindex'), '3');
    tabIndexElement.disabled = true;
    await element.updateComplete;
    assert.equal(tabIndexElement.getAttribute('tabindex'), '-1');
    tabIndexElement.disabled = false;
    await element.updateComplete;
    assert.equal(tabIndexElement.getAttribute('tabindex'), '3');
  });

  // 'tap' event is tested so we don't loose backward compatibility with older
  // plugins who didn't move to on-click which is faster and well supported.
  test('dispatches click event', () => {
    const spy = addSpyOn('click');
    MockInteractions.click(element);
    assert.isTrue(spy.calledOnce);
  });

  test('dispatches tap event', () => {
    const spy = addSpyOn('tap');
    MockInteractions.tap(element);
    assert.isTrue(spy.calledOnce);
  });

  test('dispatches click from tap event', () => {
    const spy = addSpyOn('click');
    MockInteractions.tap(element);
    assert.isTrue(spy.calledOnce);
  });

  for (const key of [Key.ENTER, Key.SPACE]) {
    test(`dispatches click event on key '${key}'`, () => {
      const tapSpy = sinon.spy();
      element.addEventListener('click', tapSpy);
      pressKey(element, key);
      assert.isTrue(tapSpy.calledOnce);
    });

    test(`dispatches no click event with modifier on key '${key}'`, () => {
      const tapSpy = sinon.spy();
      element.addEventListener('click', tapSpy);
      pressKey(element, key, Modifier.ALT_KEY);
      pressKey(element, key, Modifier.CTRL_KEY);
      pressKey(element, key, Modifier.META_KEY);
      pressKey(element, key, Modifier.SHIFT_KEY);
      assert.isFalse(tapSpy.called);
    });
  }

  suite('disabled', () => {
    setup(async () => {
      element.disabled = true;
      await element.updateComplete;
    });

    for (const eventName of ['tap', 'click']) {
      test('stops ' + eventName + ' event', () => {
        const spy = addSpyOn(eventName);
        MockInteractions.tap(element);
        assert.isFalse(spy.called);
      });
    }

    for (const key of [Key.ENTER, Key.SPACE]) {
      test(`stops click event on keycode ${key}`, () => {
        const tapSpy = sinon.spy();
        element.addEventListener('click', tapSpy);
        pressKey(element, key);
        assert.isFalse(tapSpy.called);
      });
    }
  });

  suite('reporting', () => {
    let reportStub: sinon.SinonStub;
    setup(() => {
      reportStub = sinon.stub(element.reporting, 'reportInteraction');
      reportStub.reset();
    });

    test('report event after click', () => {
      MockInteractions.click(element);
      assert.isTrue(reportStub.calledOnce);
      assert.equal(reportStub.lastCall.args[0], 'button-click');
      assert.deepEqual(reportStub.lastCall.args[1], {
        path: 'html.lightTheme>body>div>gr-button',
      });
    });

    test('report event after click on nested', async () => {
      const nestedElement = await fixture<HTMLDivElement>(html`
        <div id="test">
          <gr-button class="testBtn"></gr-button>
        </div>
      `);
      MockInteractions.click(queryAndAssert(nestedElement, 'gr-button'));
      assert.isTrue(reportStub.calledOnce);
      assert.equal(reportStub.lastCall.args[0], 'button-click');
      assert.deepEqual(reportStub.lastCall.args[1], {
        path: 'html.lightTheme>body>div>div#test>gr-button.testBtn',
      });
    });
  });
});
