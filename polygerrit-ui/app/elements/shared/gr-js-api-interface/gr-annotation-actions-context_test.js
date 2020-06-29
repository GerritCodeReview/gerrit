/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

import '../../../test/common-test-setup-karma.js';
import './gr-js-api-interface.js';
import {GrAnnotation} from '../../diff/gr-diff-highlight/gr-annotation.js';
import {GrAnnotationActionsContext} from './gr-annotation-actions-context.js';
import {_testOnly_initGerritPluginApi} from './gr-gerrit.js';

const pluginApi = _testOnly_initGerritPluginApi();

suite('gr-annotation-actions-context tests', () => {
  let instance;

  let el;
  let lineNumberEl;
  let plugin;

  setup(() => {
    pluginApi.install(p => { plugin = p; }, '0.1',
        'http://test.com/plugins/testplugin/static/test.js');

    const str = 'lorem ipsum blah blah';
    const line = {text: str};
    el = document.createElement('div');
    el.textContent = str;
    el.setAttribute('data-side', 'right');
    lineNumberEl = document.createElement('td');
    lineNumberEl.classList.add('right');
    document.body.appendChild(el);
    instance = new GrAnnotationActionsContext(
        el, lineNumberEl, line, 'dummy/path', '123', '1');
  });

  teardown(() => {
    el.remove();
  });

  test('test annotateRange', () => {
    const annotateElementSpy = sinon.spy(GrAnnotation, 'annotateElement');
    const start = 0;
    const end = 100;
    const cssStyleObject = plugin.styles().css('background-color: #000000');

    // Assert annotateElement is not called when side is different.
    instance.annotateRange(start, end, cssStyleObject, 'left');
    assert.equal(annotateElementSpy.callCount, 0);

    // Assert annotateElement is called once when side is the same.
    instance.annotateRange(start, end, cssStyleObject, 'right');
    assert.equal(annotateElementSpy.callCount, 1);
    const args = annotateElementSpy.getCalls()[0].args;
    assert.equal(args[0], el);
    assert.equal(args[1], start);
    assert.equal(args[2], end);
    assert.equal(args[3], cssStyleObject.getClassName(el));
  });

  test('test annotateLineNumber', () => {
    const cssStyleObject = plugin.styles().css('background-color: #000000');

    const className = cssStyleObject.getClassName(lineNumberEl);

    // Assert that css class is *not* applied when side is different.
    instance.annotateLineNumber(cssStyleObject, 'left');
    assert.isFalse(lineNumberEl.classList.contains(className));

    // Assert that css class is applied when side is the same.
    instance.annotateLineNumber(cssStyleObject, 'right');
    assert.isTrue(lineNumberEl.classList.contains(className));
  });
});

