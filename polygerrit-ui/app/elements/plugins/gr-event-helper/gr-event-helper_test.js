/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma.js';
import {addListener} from '@polymer/polymer/lib/utils/gestures.js';
import {Polymer} from '@polymer/polymer/lib/legacy/polymer-fn.js';
import {mockPromise} from '../../../test/test-utils.js';

Polymer({
  is: 'gr-event-helper-some-element',

  properties: {
    fooBar: {
      type: Object,
      notify: true,
    },
  },
});

const basicFixture = fixtureFromElement('gr-event-helper-some-element');

suite('gr-event-helper tests', () => {
  let element;
  let instance;

  setup(() => {
    let plugin;
    window.Gerrit.install(p => { plugin = p; }, '0.1',
        'http://test.com/plugins/testplugin/static/test.js');
    element = basicFixture.instantiate();
    instance = plugin.eventHelper(element);
  });

  test('onTap()', async () => {
    const promise = mockPromise();
    instance.onTap(() => {
      promise.resolve();
    });
    MockInteractions.tap(element);
    await promise;
  });

  test('onTap() cancel', () => {
    const tapStub = sinon.stub();
    addListener(element.parentElement, 'tap', tapStub);
    instance.onTap(() => false);
    MockInteractions.tap(element);
    flush();
    assert.isFalse(tapStub.called);
  });

  test('onClick() cancel', () => {
    const tapStub = sinon.stub();
    element.parentElement.addEventListener('click', tapStub);
    instance.onTap(() => false);
    MockInteractions.tap(element);
    flush();
    assert.isFalse(tapStub.called);
  });
});

