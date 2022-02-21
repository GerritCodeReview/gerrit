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
import './gr-selection-action-box';
import {html} from '@polymer/polymer/lib/utils/html-tag';
import {GrSelectionActionBox} from './gr-selection-action-box';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';

const basicFixture = fixtureFromTemplate(html`
  <div>
    <gr-selection-action-box></gr-selection-action-box>
    <div class="target">some text</div>
  </div>
`);

suite('gr-selection-action-box', () => {
  let container: GrSelectionActionBox;
  let element: GrSelectionActionBox;
  let dispatchEventStub: sinon.SinonStub;

  setup(() => {
    container = basicFixture.instantiate() as GrSelectionActionBox;
    element = container.querySelector(
      'gr-selection-action-box'
    ) as GrSelectionActionBox;

    dispatchEventStub = sinon.stub(element, 'dispatchEvent');
  });

  test('ignores regular keys', () => {
    MockInteractions.pressAndReleaseKeyOn(document.body, 27, null, 'esc');
    assert.isFalse(dispatchEventStub.called);
  });

  suite('mousedown reacts only to main button', () => {
    let e: any;

    setup(() => {
      e = {
        button: 0,
        preventDefault: sinon.stub(),
        stopPropagation: sinon.stub(),
      };
    });

    test('event handled if main button', () => {
      element._handleMouseDown(e);
      assert.isTrue(e.preventDefault.called);
      assert.equal(
        dispatchEventStub.lastCall.args[0].type,
        'create-comment-requested'
      );
    });

    test('event ignored if not main button', () => {
      e.button = 1;
      element._handleMouseDown(e);
      assert.isFalse(e.preventDefault.called);
      assert.isFalse(dispatchEventStub.called);
    });
  });

  suite('placeAbove', () => {
    let target: HTMLDivElement;
    let getTargetBoundingRectStub: sinon.SinonStub;

    setup(() => {
      target = container.querySelector('.target') as HTMLDivElement;
      sinon.stub(container, 'getBoundingClientRect').returns({
        top: 1,
        bottom: 2,
        left: 3,
        right: 4,
        width: 50,
        height: 6,
      } as DOMRect);
      getTargetBoundingRectStub = sinon
        .stub(element, '_getTargetBoundingRect')
        .returns({
          top: 42,
          bottom: 20,
          left: 30,
          right: 40,
          width: 100,
          height: 60,
        } as DOMRect);
      sinon
        .stub(element.$.tooltip, 'getBoundingClientRect')
        .returns({width: 10, height: 10} as DOMRect);
    });

    test('placeAbove for Element argument', async () => {
      await element.placeAbove(target);
      assert.equal(element.style.top, '25px');
      assert.equal(element.style.left, '72px');
    });

    test('placeAbove for Text Node argument', async () => {
      await element.placeAbove(target.firstChild as HTMLElement);
      assert.equal(element.style.top, '25px');
      assert.equal(element.style.left, '72px');
    });

    test('placeBelow for Element argument', async () => {
      await element.placeBelow(target);
      assert.equal(element.style.top, '45px');
      assert.equal(element.style.left, '72px');
    });

    test('placeBelow for Text Node argument', async () => {
      await element.placeBelow(target.firstChild as HTMLElement);
      assert.equal(element.style.top, '45px');
      assert.equal(element.style.left, '72px');
    });

    test('uses document.createRange', async () => {
      const createRangeSpy = sinon.spy(document, 'createRange');
      getTargetBoundingRectStub.restore();
      sinon.spy(element, '_getTargetBoundingRect');
      await element.placeAbove(target.firstChild as HTMLElement);
      assert.isTrue(createRangeSpy.called);
    });
  });
});
