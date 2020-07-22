/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import {getMockDiffResponse} from '../../../test/mocks/diff-response.js';
import './gr-diff.js';
import {dom, flush} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GrDiffBuilderImage} from '../gr-diff-builder/gr-diff-builder-image.js';
import {getComputedStyleValue} from '../../../utils/dom-util.js';
import {_setHiddenScroll} from '../../../scripts/hiddenscroll.js';
import {runA11yAudit} from '../../../test/a11y-test-utils.js';
import '@polymer/paper-button/paper-button.js';
import {SPECIAL_PATCH_SET_NUM} from '../../../utils/patch-set-util.js';

const basicFixture = fixtureFromElement('gr-diff');

suite('gr-diff a11y test', () => {
  test('audit', async () => {
    await runA11yAudit(basicFixture);
  });
});

suite('gr-diff tests', () => {
  let element;

  const MINIMAL_PREFS = {tab_size: 2, line_length: 80};

  setup(() => {

  });

  suite('selectionchange event handling', () => {
    const emulateSelection = function() {
      document.dispatchEvent(new CustomEvent('selectionchange'));
    };

    setup(() => {
      element = basicFixture.instantiate();
      sinon.stub(element.$.highlights, 'handleSelectionChange');
    });

    test('enabled if logged in', () => {
      element.loggedIn = true;
      emulateSelection();
      assert.isTrue(element.$.highlights.handleSelectionChange.called);
    });

    test('ignored if logged out', () => {
      element.loggedIn = false;
      emulateSelection();
      assert.isFalse(element.$.highlights.handleSelectionChange.called);
    });
  });

  test('cancel', () => {
    element = basicFixture.instantiate();
    const cancelStub = sinon.stub(element.$.diffBuilder, 'cancel');
    element.cancel();
    assert.isTrue(cancelStub.calledOnce);
  });

  test('line limit with line_wrapping', () => {
    element = basicFixture.instantiate();
    element.prefs = {...MINIMAL_PREFS, line_wrapping: true};
    flushAsynchronousOperations();
    assert.equal(getComputedStyleValue('--line-limit', element), '80ch');
  });

  test('line limit without line_wrapping', () => {
    element = basicFixture.instantiate();
    element.prefs = {...MINIMAL_PREFS, line_wrapping: false};
    flushAsynchronousOperations();
    assert.isNotOk(getComputedStyleValue('--line-limit', element));
  });

  suite('_get{PatchNum|IsParentComment}ByLineAndContent', () => {
    let lineEl;
    let contentEl;

    setup(() => {
      element = basicFixture.instantiate();
      lineEl = document.createElement('td');
      contentEl = document.createElement('span');
    });

    suite('_getPatchNumByLineAndContent', () => {
      test('right side', () => {
        element.patchRange = {patchNum: 4, basePatchNum: 'PARENT'};
        lineEl.classList.add('right');
        assert.equal(element._getPatchNumByLineAndContent(lineEl, contentEl),
            4);
      });

      test('left side parent by linenum', () => {
        element.patchRange = {patchNum: 4, basePatchNum: 'PARENT'};
        lineEl.classList.add('left');
        assert.equal(element._getPatchNumByLineAndContent(lineEl, contentEl),
            4);
      });

      test('left side parent by content', () => {
        element.patchRange = {patchNum: 4, basePatchNum: 'PARENT'};
        contentEl.classList.add('remove');
        assert.equal(element._getPatchNumByLineAndContent(lineEl, contentEl),
            4);
      });

      test('left side merge parent', () => {
        element.patchRange = {patchNum: 4, basePatchNum: -2};
        contentEl.classList.add('remove');
        assert.equal(element._getPatchNumByLineAndContent(lineEl, contentEl),
            4);
      });

      test('left side non parent', () => {
        element.patchRange = {patchNum: 4, basePatchNum: 3};
        contentEl.classList.add('remove');
        assert.equal(element._getPatchNumByLineAndContent(lineEl, contentEl),
            3);
      });
    });

    suite('_getIsParentCommentByLineAndContent', () => {
      test('right side', () => {
        element.patchRange = {patchNum: 4, basePatchNum: 'PARENT'};
        lineEl.classList.add('right');
        assert.isFalse(
            element._getIsParentCommentByLineAndContent(lineEl, contentEl));
      });

      test('left side parent by linenum', () => {
        element.patchRange = {patchNum: 4, basePatchNum: 'PARENT'};
        lineEl.classList.add('left');
        assert.isTrue(
            element._getIsParentCommentByLineAndContent(lineEl, contentEl));
      });

      test('left side parent by content', () => {
        element.patchRange = {patchNum: 4, basePatchNum: 'PARENT'};
        contentEl.classList.add('remove');
        assert.isTrue(
            element._getIsParentCommentByLineAndContent(lineEl, contentEl));
      });

      test('left side merge parent', () => {
        element.patchRange = {patchNum: 4, basePatchNum: -2};
        contentEl.classList.add('remove');
        assert.isTrue(
            element._getIsParentCommentByLineAndContent(lineEl, contentEl));
      });

      test('left side non parent', () => {
        element.patchRange = {patchNum: 4, basePatchNum: 3};
        contentEl.classList.add('remove');
        assert.isFalse(
            element._getIsParentCommentByLineAndContent(lineEl, contentEl));
      });
    });
  });

  suite('not logged in', () => {
    setup(() => {
      const getLoggedInPromise = Promise.resolve(false);
      stub('gr-rest-api-interface', {
        getLoggedIn() { return getLoggedInPromise; },
      });
      element = basicFixture.instantiate();
      return getLoggedInPromise;
    });

    test('toggleLeftDiff', () => {
      element.toggleLeftDiff();
      assert.isTrue(element.classList.contains('no-left'));
      element.toggleLeftDiff();
      assert.isFalse(element.classList.contains('no-left'));
    });

    test('addDraftAtLine', () => {
      sinon.stub(element, '_selectLine');
      const loggedInErrorSpy = sinon.spy();
      element.addEventListener('show-auth-required', loggedInErrorSpy);
      element.addDraftAtLine();
      assert.isTrue(loggedInErrorSpy.called);
    });

    test('view does not start with displayLine classList', () => {
      assert.isFalse(
          element.shadowRoot
              .querySelector('.diffContainer')
              .classList
              .contains('displayLine'));
    });

    test('displayLine class added called when displayLine is true', () => {
      const spy = sinon.spy(element, '_computeContainerClass');
      element.displayLine = true;
      assert.isTrue(spy.called);
      assert.isTrue(
          element.shadowRoot
              .querySelector('.diffContainer')
              .classList
              .contains('displayLine'));
    });

    test('thread groups', () => {
      const contentEl = document.createElement('div');

      element.changeNum = 123;
      element.patchRange = {basePatchNum: 1, patchNum: 2};
      element.path = 'file.txt';

      element.$.diffBuilder._builder = element.$.diffBuilder._getDiffBuilder(
          getMockDiffResponse(), {...MINIMAL_PREFS});

      // No thread groups.
      assert.isNotOk(element._getThreadGroupForLine(contentEl));

      // A thread group gets created.
      const threadGroupEl = element._getOrCreateThreadGroup(contentEl);
      assert.isOk(threadGroupEl);

      // The new thread group can be fetched.
      assert.isOk(element._getThreadGroupForLine(contentEl));

      assert.equal(contentEl.querySelectorAll('.thread-group').length, 1);
    });

    suite('image diffs', () => {
      let mockFile1;
      let mockFile2;
      setup(() => {
        mockFile1 = {
          body: 'Qk06AAAAAAAAADYAAAAoAAAAAQAAAP////8BACAAAAAAAAAAAAATCwAAE' +
          'wsAAAAAAAAAAAAAAAAA/w==',
          type: 'image/bmp',
        };
        mockFile2 = {
          body: 'Qk06AAAAAAAAADYAAAAoAAAAAQAAAP////8BACAAAAAAAAAAAAATCwAAE' +
          'wsAAAAAAAAAAAAA/////w==',
          type: 'image/bmp',
        };

        element.patchRange = {basePatchNum: 'PARENT', patchNum: 1};
        element.isImageDiff = true;
        element.prefs = {
          auto_hide_diff_table_header: true,
          context: 10,
          cursor_blink_rate: 0,
          font_size: 12,
          ignore_whitespace: 'IGNORE_NONE',
          intraline_difference: true,
          line_length: 100,
          line_wrapping: false,
          show_line_endings: true,
          show_tabs: true,
          show_whitespace_errors: true,
          syntax_highlighting: true,
          tab_size: 8,
          theme: 'DEFAULT',
        };
      });

      test('renders image diffs with same file name', done => {
        const rendered = () => {
          // Recognizes that it should be an image diff.
          assert.isTrue(element.isImageDiff);
          assert.instanceOf(
              element.$.diffBuilder._builder, GrDiffBuilderImage);

          // Left image rendered with the parent commit's version of the file.
          const leftImage = element.$.diffTable.querySelector('td.left img');
          const leftLabel =
              element.$.diffTable.querySelector('td.left label');
          const leftLabelContent = leftLabel.querySelector('.label');
          const leftLabelName = leftLabel.querySelector('.name');

          const rightImage =
              element.$.diffTable.querySelector('td.right img');
          const rightLabel = element.$.diffTable.querySelector(
              'td.right label');
          const rightLabelContent = rightLabel.querySelector('.label');
          const rightLabelName = rightLabel.querySelector('.name');

          assert.isNotOk(rightLabelName);
          assert.isNotOk(leftLabelName);

          let leftLoaded = false;
          let rightLoaded = false;

          leftImage.addEventListener('load', () => {
            assert.isOk(leftImage);
            assert.equal(leftImage.getAttribute('src'),
                'data:image/bmp;base64, ' + mockFile1.body);
            assert.equal(leftLabelContent.textContent, '1\u00d71 image/bmp');// \u00d7 - '×'
            leftLoaded = true;
            if (rightLoaded) {
              element.removeEventListener('render', rendered);
              done();
            }
          });

          rightImage.addEventListener('load', () => {
            assert.isOk(rightImage);
            assert.equal(rightImage.getAttribute('src'),
                'data:image/bmp;base64, ' + mockFile2.body);
            assert.equal(rightLabelContent.textContent, '1\u00d71 image/bmp');// \u00d7 - '×'

            rightLoaded = true;
            if (leftLoaded) {
              element.removeEventListener('render', rendered);
              done();
            }
          });
        };

        element.addEventListener('render', rendered);

        element.baseImage = mockFile1;
        element.revisionImage = mockFile2;
        element.diff = {
          meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 66},
          meta_b: {name: 'carrot.jpg', content_type: 'image/jpeg',
            lines: 560},
          intraline_status: 'OK',
          change_type: 'MODIFIED',
          diff_header: [
            'diff --git a/carrot.jpg b/carrot.jpg',
            'index 2adc47d..f9c2f2c 100644',
            '--- a/carrot.jpg',
            '+++ b/carrot.jpg',
            'Binary files differ',
          ],
          content: [{skip: 66}],
          binary: true,
        };
      });

      test('renders image diffs with a different file name', done => {
        const mockDiff = {
          meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 66},
          meta_b: {name: 'carrot2.jpg', content_type: 'image/jpeg',
            lines: 560},
          intraline_status: 'OK',
          change_type: 'MODIFIED',
          diff_header: [
            'diff --git a/carrot.jpg b/carrot2.jpg',
            'index 2adc47d..f9c2f2c 100644',
            '--- a/carrot.jpg',
            '+++ b/carrot2.jpg',
            'Binary files differ',
          ],
          content: [{skip: 66}],
          binary: true,
        };

        const rendered = () => {
          // Recognizes that it should be an image diff.
          assert.isTrue(element.isImageDiff);
          assert.instanceOf(
              element.$.diffBuilder._builder, GrDiffBuilderImage);

          // Left image rendered with the parent commit's version of the file.
          const leftImage = element.$.diffTable.querySelector('td.left img');
          const leftLabel =
              element.$.diffTable.querySelector('td.left label');
          const leftLabelContent = leftLabel.querySelector('.label');
          const leftLabelName = leftLabel.querySelector('.name');

          const rightImage =
              element.$.diffTable.querySelector('td.right img');
          const rightLabel = element.$.diffTable.querySelector(
              'td.right label');
          const rightLabelContent = rightLabel.querySelector('.label');
          const rightLabelName = rightLabel.querySelector('.name');

          assert.isOk(rightLabelName);
          assert.isOk(leftLabelName);
          assert.equal(leftLabelName.textContent, mockDiff.meta_a.name);
          assert.equal(rightLabelName.textContent, mockDiff.meta_b.name);

          let leftLoaded = false;
          let rightLoaded = false;

          leftImage.addEventListener('load', () => {
            assert.isOk(leftImage);
            assert.equal(leftImage.getAttribute('src'),
                'data:image/bmp;base64, ' + mockFile1.body);
            assert.equal(leftLabelContent.textContent, '1\u00d71 image/bmp');// \u00d7 - '×'
            leftLoaded = true;
            if (rightLoaded) {
              element.removeEventListener('render', rendered);
              done();
            }
          });

          rightImage.addEventListener('load', () => {
            assert.isOk(rightImage);
            assert.equal(rightImage.getAttribute('src'),
                'data:image/bmp;base64, ' + mockFile2.body);
            assert.equal(rightLabelContent.textContent, '1\u00d71 image/bmp');// \u00d7 - '×'

            rightLoaded = true;
            if (leftLoaded) {
              element.removeEventListener('render', rendered);
              done();
            }
          });
        };

        element.addEventListener('render', rendered);

        element.baseImage = mockFile1;
        element.baseImage._name = mockDiff.meta_a.name;
        element.revisionImage = mockFile2;
        element.revisionImage._name = mockDiff.meta_b.name;
        element.diff = mockDiff;
      });

      test('renders added image', done => {
        const mockDiff = {
          meta_b: {name: 'carrot.jpg', content_type: 'image/jpeg',
            lines: 560},
          intraline_status: 'OK',
          change_type: 'ADDED',
          diff_header: [
            'diff --git a/carrot.jpg b/carrot.jpg',
            'index 0000000..f9c2f2c 100644',
            '--- /dev/null',
            '+++ b/carrot.jpg',
            'Binary files differ',
          ],
          content: [{skip: 66}],
          binary: true,
        };

        function rendered() {
          // Recognizes that it should be an image diff.
          assert.isTrue(element.isImageDiff);
          assert.instanceOf(
              element.$.diffBuilder._builder, GrDiffBuilderImage);

          const leftImage = element.$.diffTable.querySelector('td.left img');
          const rightImage = element.$.diffTable.querySelector('td.right img');

          assert.isNotOk(leftImage);
          assert.isOk(rightImage);
          done();
          element.removeEventListener('render', rendered);
        }
        element.addEventListener('render', rendered);

        element.revisionImage = mockFile2;
        element.diff = mockDiff;
      });

      test('renders removed image', done => {
        const mockDiff = {
          meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg',
            lines: 560},
          intraline_status: 'OK',
          change_type: 'DELETED',
          diff_header: [
            'diff --git a/carrot.jpg b/carrot.jpg',
            'index f9c2f2c..0000000 100644',
            '--- a/carrot.jpg',
            '+++ /dev/null',
            'Binary files differ',
          ],
          content: [{skip: 66}],
          binary: true,
        };

        function rendered() {
          // Recognizes that it should be an image diff.
          assert.isTrue(element.isImageDiff);
          assert.instanceOf(
              element.$.diffBuilder._builder, GrDiffBuilderImage);

          const leftImage = element.$.diffTable.querySelector('td.left img');
          const rightImage = element.$.diffTable.querySelector('td.right img');

          assert.isOk(leftImage);
          assert.isNotOk(rightImage);
          done();
          element.removeEventListener('render', rendered);
        }
        element.addEventListener('render', rendered);

        element.baseImage = mockFile1;
        element.diff = mockDiff;
      });

      test('does not render disallowed image type', done => {
        const mockDiff = {
          meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg-evil',
            lines: 560},
          intraline_status: 'OK',
          change_type: 'DELETED',
          diff_header: [
            'diff --git a/carrot.jpg b/carrot.jpg',
            'index f9c2f2c..0000000 100644',
            '--- a/carrot.jpg',
            '+++ /dev/null',
            'Binary files differ',
          ],
          content: [{skip: 66}],
          binary: true,
        };
        mockFile1.type = 'image/jpeg-evil';

        function rendered() {
          // Recognizes that it should be an image diff.
          assert.isTrue(element.isImageDiff);
          assert.instanceOf(
              element.$.diffBuilder._builder, GrDiffBuilderImage);
          const leftImage = element.$.diffTable.querySelector('td.left img');
          assert.isNotOk(leftImage);
          done();
          element.removeEventListener('render', rendered);
        }
        element.addEventListener('render', rendered);

        element.baseImage = mockFile1;
        element.diff = mockDiff;
      });
    });

    test('_handleTap lineNum', done => {
      const addDraftStub = sinon.stub(element, 'addDraftAtLine');
      const el = document.createElement('div');
      el.className = 'lineNum';
      el.addEventListener('click', e => {
        element._handleTap(e);
        assert.isTrue(addDraftStub.called);
        assert.equal(addDraftStub.lastCall.args[0], el);
        done();
      });
      el.click();
    });

    test('_handleTap context', done => {
      const showContextStub =
          sinon.stub(element.$.diffBuilder, 'showContext');
      const el = document.createElement('div');
      el.className = 'showContext';
      el.addEventListener('click', e => {
        element._handleTap(e);
        assert.isTrue(showContextStub.called);
        done();
      });
      el.click();
    });

    test('_handleTap content', done => {
      const content = document.createElement('div');
      const lineEl = document.createElement('div');

      const selectStub = sinon.stub(element, '_selectLine');
      sinon.stub(element.$.diffBuilder, 'getLineElByChild')
          .callsFake(() => lineEl);

      content.className = 'content';
      content.addEventListener('click', e => {
        element._handleTap(e);
        assert.isTrue(selectStub.called);
        assert.equal(selectStub.lastCall.args[0], lineEl);
        done();
      });
      content.click();
    });

    suite('getCursorStops', () => {
      const setupDiff = function() {
        element.diff = getMockDiffResponse();
        element.prefs = {
          context: 10,
          tab_size: 8,
          font_size: 12,
          line_length: 100,
          cursor_blink_rate: 0,
          line_wrapping: false,
          intraline_difference: true,
          show_line_endings: true,
          show_tabs: true,
          show_whitespace_errors: true,
          syntax_highlighting: true,
          auto_hide_diff_table_header: true,
          theme: 'DEFAULT',
          ignore_whitespace: 'IGNORE_NONE',
        };

        element._renderDiffTable();
        flushAsynchronousOperations();
      };

      test('getCursorStops returns [] when hidden and noAutoRender', () => {
        element.noAutoRender = true;
        setupDiff();
        element.hidden = true;
        assert.equal(element.getCursorStops().length, 0);
      });

      test('getCursorStops', () => {
        setupDiff();
        const ROWS = 48;
        const FILE_ROW = 1;
        assert.equal(element.getCursorStops().length, ROWS + FILE_ROW);
      });
    });

    test('adds .hiddenscroll', () => {
      _setHiddenScroll(true);
      element.displayLine = true;
      assert.include(element.shadowRoot
          .querySelector('.diffContainer').className, 'hiddenscroll');
    });
  });

  suite('logged in', () => {
    let fakeLineEl;
    setup(() => {
      element = basicFixture.instantiate();
      element.loggedIn = true;
      element.patchRange = {};

      fakeLineEl = {
        getAttribute: sinon.stub().returns(42),
        classList: {
          contains: sinon.stub().returns(true),
        },
      };
    });

    test('addDraftAtLine', () => {
      sinon.stub(element, '_selectLine');
      sinon.stub(element, '_createComment');
      element.addDraftAtLine(fakeLineEl);
      assert.isTrue(element._createComment
          .calledWithExactly(fakeLineEl, 42));
    });

    test('addDraftAtLine on an edit', () => {
      element.patchRange.basePatchNum = SPECIAL_PATCH_SET_NUM.EDIT;
      sinon.stub(element, '_selectLine');
      sinon.stub(element, '_createComment');
      const alertSpy = sinon.spy();
      element.addEventListener('show-alert', alertSpy);
      element.addDraftAtLine(fakeLineEl);
      assert.isTrue(alertSpy.called);
      assert.isFalse(element._createComment.called);
    });

    test('addDraftAtLine on an edit base', () => {
      element.patchRange.patchNum = SPECIAL_PATCH_SET_NUM.EDIT;
      element.patchRange.basePatchNum = SPECIAL_PATCH_SET_NUM.PARENT;
      sinon.stub(element, '_selectLine');
      sinon.stub(element, '_createComment');
      const alertSpy = sinon.spy();
      element.addEventListener('show-alert', alertSpy);
      element.addDraftAtLine(fakeLineEl);
      assert.isTrue(alertSpy.called);
      assert.isFalse(element._createComment.called);
    });

    suite('change in preferences', () => {
      setup(() => {
        element.diff = {
          meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 66},
          meta_b: {name: 'carrot.jpg', content_type: 'image/jpeg',
            lines: 560},
          diff_header: [],
          intraline_status: 'OK',
          change_type: 'MODIFIED',
          content: [{skip: 66}],
        };
        element.flushDebouncer('renderDiffTable');
      });

      test('change in preferences re-renders diff', () => {
        sinon.stub(element, '_renderDiffTable');
        element.prefs = {
          ...MINIMAL_PREFS, time_format: 'HHMM_12'};
        element.flushDebouncer('renderDiffTable');
        assert.isTrue(element._renderDiffTable.called);
      });

      test('adding/removing property in preferences re-renders diff', () => {
        const stub = sinon.stub(element, '_renderDiffTable');
        const newPrefs1 = {...MINIMAL_PREFS,
          line_wrapping: true};
        element.prefs = newPrefs1;
        element.flushDebouncer('renderDiffTable');
        assert.isTrue(element._renderDiffTable.called);
        stub.reset();

        const newPrefs2 = {...newPrefs1};
        delete newPrefs2.line_wrapping;
        element.prefs = newPrefs2;
        element.flushDebouncer('renderDiffTable');
        assert.isTrue(element._renderDiffTable.called);
      });

      test('change in preferences does not re-renders diff with ' +
          'noRenderOnPrefsChange', () => {
        sinon.stub(element, '_renderDiffTable');
        element.noRenderOnPrefsChange = true;
        element.prefs = {
          ...MINIMAL_PREFS, time_format: 'HHMM_12'};
        element.flushDebouncer('renderDiffTable');
        assert.isFalse(element._renderDiffTable.called);
      });
    });
  });

  suite('diff header', () => {
    setup(() => {
      element = basicFixture.instantiate();
      element.diff = {
        meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 66},
        meta_b: {name: 'carrot.jpg', content_type: 'image/jpeg',
          lines: 560},
        diff_header: [],
        intraline_status: 'OK',
        change_type: 'MODIFIED',
        content: [{skip: 66}],
      };
    });

    test('hidden', () => {
      assert.equal(element._diffHeaderItems.length, 0);
      element.push('diff.diff_header', 'diff --git a/test.jpg b/test.jpg');
      assert.equal(element._diffHeaderItems.length, 0);
      element.push('diff.diff_header', 'index 2adc47d..f9c2f2c 100644');
      assert.equal(element._diffHeaderItems.length, 0);
      element.push('diff.diff_header', '--- a/test.jpg');
      assert.equal(element._diffHeaderItems.length, 0);
      element.push('diff.diff_header', '+++ b/test.jpg');
      assert.equal(element._diffHeaderItems.length, 0);
      element.push('diff.diff_header', 'test');
      assert.equal(element._diffHeaderItems.length, 1);
      flushAsynchronousOperations();

      assert.equal(element.$.diffHeader.textContent.trim(), 'test');
    });

    test('binary files', () => {
      element.diff.binary = true;
      assert.equal(element._diffHeaderItems.length, 0);
      element.push('diff.diff_header', 'diff --git a/test.jpg b/test.jpg');
      assert.equal(element._diffHeaderItems.length, 0);
      element.push('diff.diff_header', 'test');
      assert.equal(element._diffHeaderItems.length, 1);
      element.push('diff.diff_header', 'Binary files differ');
      assert.equal(element._diffHeaderItems.length, 1);
    });
  });

  suite('safety and bypass', () => {
    let renderStub;

    setup(() => {
      element = basicFixture.instantiate();
      renderStub = sinon.stub(element.$.diffBuilder, 'render').callsFake(
          () => {
            element.$.diffBuilder.dispatchEvent(
                new CustomEvent('render', {bubbles: true, composed: true}));
            return Promise.resolve({});
          });
      sinon.stub(element, 'getDiffLength').returns(10000);
      element.diff = getMockDiffResponse();
      element.noRenderOnPrefsChange = true;
    });

    test('large render w/ context = 10', done => {
      element.prefs = {...MINIMAL_PREFS, context: 10};
      function rendered() {
        assert.isTrue(renderStub.called);
        assert.isFalse(element._showWarning);
        done();
        element.removeEventListener('render', rendered);
      }
      element.addEventListener('render', rendered);
      element._renderDiffTable();
    });

    test('large render w/ whole file and bypass', done => {
      element.prefs = {...MINIMAL_PREFS, context: -1};
      element._safetyBypass = 10;
      function rendered() {
        assert.isTrue(renderStub.called);
        assert.isFalse(element._showWarning);
        done();
        element.removeEventListener('render', rendered);
      }
      element.addEventListener('render', rendered);
      element._renderDiffTable();
    });

    test('large render w/ whole file and no bypass', done => {
      element.prefs = {...MINIMAL_PREFS, context: -1};
      function rendered() {
        assert.isFalse(renderStub.called);
        assert.isTrue(element._showWarning);
        done();
        element.removeEventListener('render', rendered);
      }
      element.addEventListener('render', rendered);
      element._renderDiffTable();
    });
  });

  suite('blame', () => {
    setup(() => {
      element = basicFixture.instantiate();
    });

    test('unsetting', () => {
      element.blame = [];
      const setBlameSpy = sinon.spy(element.$.diffBuilder, 'setBlame');
      element.classList.add('showBlame');
      element.blame = null;
      assert.isTrue(setBlameSpy.calledWithExactly(null));
      assert.isFalse(element.classList.contains('showBlame'));
    });

    test('setting', () => {
      element.blame = [{id: 'commit id', ranges: [{start: 1, end: 2}]}];
      assert.isTrue(element.classList.contains('showBlame'));
    });
  });

  suite('trailing newline warnings', () => {
    const NO_NEWLINE_BASE = 'No newline at end of base file.';
    const NO_NEWLINE_REVISION = 'No newline at end of revision file.';

    const getWarning = element =>
      element.shadowRoot.querySelector('.newlineWarning').textContent;

    setup(() => {
      element = basicFixture.instantiate();
      element.showNewlineWarningLeft = false;
      element.showNewlineWarningRight = false;
    });

    test('shows combined warning if both sides set to warn', () => {
      element.showNewlineWarningLeft = true;
      element.showNewlineWarningRight = true;
      assert.include(getWarning(element),
          NO_NEWLINE_BASE + ' \u2014 ' + NO_NEWLINE_REVISION);// \u2014 - '—'
    });

    suite('showNewlineWarningLeft', () => {
      test('show warning if true', () => {
        element.showNewlineWarningLeft = true;
        assert.include(getWarning(element), NO_NEWLINE_BASE);
      });

      test('hide warning if false', () => {
        element.showNewlineWarningLeft = false;
        assert.notInclude(getWarning(element), NO_NEWLINE_BASE);
      });

      test('hide warning if undefined', () => {
        element.showNewlineWarningLeft = undefined;
        assert.notInclude(getWarning(element), NO_NEWLINE_BASE);
      });
    });

    suite('showNewlineWarningRight', () => {
      test('show warning if true', () => {
        element.showNewlineWarningRight = true;
        assert.include(getWarning(element), NO_NEWLINE_REVISION);
      });

      test('hide warning if false', () => {
        element.showNewlineWarningRight = false;
        assert.notInclude(getWarning(element), NO_NEWLINE_REVISION);
      });

      test('hide warning if undefined', () => {
        element.showNewlineWarningRight = undefined;
        assert.notInclude(getWarning(element), NO_NEWLINE_REVISION);
      });
    });

    test('_computeNewlineWarningClass', () => {
      const hidden = 'newlineWarning hidden';
      const shown = 'newlineWarning';
      assert.equal(element._computeNewlineWarningClass(null, true), hidden);
      assert.equal(element._computeNewlineWarningClass('foo', true), hidden);
      assert.equal(element._computeNewlineWarningClass(null, false), hidden);
      assert.equal(element._computeNewlineWarningClass('foo', false), shown);
    });

    test('_prefsEqual', () => {
      element = basicFixture.instantiate();
      assert.isTrue(element._prefsEqual(null, null));
      assert.isTrue(element._prefsEqual({}, {}));
      assert.isTrue(element._prefsEqual({x: 1}, {x: 1}));
      assert.isTrue(
          element._prefsEqual({x: 1, abc: 'def'}, {x: 1, abc: 'def'}));
      const somePref = {abc: 'def', p: true};
      assert.isTrue(element._prefsEqual(somePref, somePref));

      assert.isFalse(element._prefsEqual({}, null));
      assert.isFalse(element._prefsEqual(null, {}));
      assert.isFalse(element._prefsEqual({x: 1}, {x: 2}));
      assert.isFalse(element._prefsEqual({x: 1, y: 'abc'}, {x: 1, y: 'abcd'}));
      assert.isFalse(element._prefsEqual({x: 1, y: 'abc'}, {x: 1}));
      assert.isFalse(element._prefsEqual({x: 1}, {x: 1, y: 'abc'}));
    });
  });

  suite('key locations', () => {
    let renderStub;

    setup(() => {
      element = basicFixture.instantiate();
      element.prefs = {};
      renderStub = sinon.stub(element.$.diffBuilder, 'render')
          .returns(new Promise(() => {}));
    });

    test('lineOfInterest is a key location', () => {
      element.lineOfInterest = {number: 789, leftSide: true};
      element._renderDiffTable();
      assert.isTrue(renderStub.called);
      assert.deepEqual(renderStub.lastCall.args[0], {
        left: {789: true},
        right: {},
      });
    });

    test('line comments are key locations', () => {
      const threadEl = document.createElement('div');
      threadEl.className = 'comment-thread';
      threadEl.setAttribute('comment-side', 'right');
      threadEl.setAttribute('line-num', 3);
      dom(element).appendChild(threadEl);
      flush();

      element._renderDiffTable();
      assert.isTrue(renderStub.called);
      assert.deepEqual(renderStub.lastCall.args[0], {
        left: {},
        right: {3: true},
      });
    });

    test('file comments are key locations', () => {
      const threadEl = document.createElement('div');
      threadEl.className = 'comment-thread';
      threadEl.setAttribute('comment-side', 'left');
      dom(element).appendChild(threadEl);
      flush();

      element._renderDiffTable();
      assert.isTrue(renderStub.called);
      assert.deepEqual(renderStub.lastCall.args[0], {
        left: {FILE: true},
        right: {},
      });
    });
  });
  const setupSampleDiff = function(params) {
    const {ignore_whitespace, content} = params;
    // binary can't be undefined, use false if not set
    const binary = params.binary || false;
    element = basicFixture.instantiate();
    element.prefs = {
      ignore_whitespace: ignore_whitespace || 'IGNORE_ALL',
      auto_hide_diff_table_header: true,
      context: 10,
      cursor_blink_rate: 0,
      font_size: 12,
      intraline_difference: true,
      line_length: 100,
      line_wrapping: false,
      show_line_endings: true,
      show_tabs: true,
      show_whitespace_errors: true,
      syntax_highlighting: true,
      tab_size: 8,
      theme: 'DEFAULT',
    };
    element.diff = {
      intraline_status: 'OK',
      change_type: 'MODIFIED',
      diff_header: [
        'diff --git a/carrot.js b/carrot.js',
        'index 2adc47d..f9c2f2c 100644',
        '--- a/carrot.js',
        '+++ b/carrot.jjs',
        'file differ',
      ],
      content,
      binary,
    };
    element._renderDiffTable();
    flushAsynchronousOperations();
  };

  test('clear diff table content as soon as diff changes', () => {
    const content = [{
      a: ['all work and no play make andybons a dull boy'],
    }, {
      b: [
        'Non eram nescius, Brute, cum, quae summis ingeniis ',
      ],
    }];
    function assertDiffTableWithContent() {
      assert.isTrue(element.$.diffTable.innerText.includes(content[0].a));
    }
    setupSampleDiff({content});
    assertDiffTableWithContent();
    element.diff = {...element.diff};
    // immediately cleaned up
    assert.equal(element.$.diffTable.innerHTML, '');
    element._renderDiffTable();
    flushAsynchronousOperations();
    // rendered again
    assertDiffTableWithContent();
  });

  suite('selection test', () => {
    test('user-select set correctly on side-by-side view', () => {
      const content = [{
        a: ['all work and no play make andybons a dull boy'],
        b: ['elgoog elgoog elgoog'],
      }, {
        ab: [
          'Non eram nescius, Brute, cum, quae summis ingeniis ',
          'exquisitaque doctrina philosophi Graeco sermone tractavissent',
        ],
      }];
      setupSampleDiff({content});
      flushAsynchronousOperations();
      const diffLine = element.shadowRoot.querySelectorAll('.contentText')[2];
      assert.equal(getComputedStyle(diffLine).userSelect, 'none');
      // click to mark it as selected
      MockInteractions.tap(diffLine);
      assert.equal(getComputedStyle(diffLine).userSelect, 'text');
    });

    test('user-select set correctly on unified view', () => {
      const content = [{
        a: ['all work and no play make andybons a dull boy'],
        b: ['elgoog elgoog elgoog'],
      }, {
        ab: [
          'Non eram nescius, Brute, cum, quae summis ingeniis ',
          'exquisitaque doctrina philosophi Graeco sermone tractavissent',
        ],
      }];
      setupSampleDiff({content});
      element.viewMode = 'UNIFIED_DIFF';
      flushAsynchronousOperations();
      const diffLine = element.shadowRoot.querySelectorAll('.contentText')[2];
      assert.equal(getComputedStyle(diffLine).userSelect, 'none');
      MockInteractions.tap(diffLine);
      assert.equal(getComputedStyle(diffLine).userSelect, 'text');
    });
  });

  suite('whitespace changes only message', () => {
    test('show the message if ignore_whitespace is criteria matches', () => {
      setupSampleDiff({content: [{skip: 100}]});
      assert.isTrue(element.showNoChangeMessage(
          /* loading= */ false,
          element.prefs,
          element._diffLength,
          element.diff
      ));
    });

    test('do not show the message for binary files', () => {
      setupSampleDiff({content: [{skip: 100}], binary: true});
      assert.isFalse(element.showNoChangeMessage(
          /* loading= */ false,
          element.prefs,
          element._diffLength,
          element.diff
      ));
    });

    test('do not show the message if still loading', () => {
      setupSampleDiff({content: [{skip: 100}]});
      assert.isFalse(element.showNoChangeMessage(
          /* loading= */ true,
          element.prefs,
          element._diffLength,
          element.diff
      ));
    });

    test('do not show the message if contains valid changes', () => {
      const content = [{
        a: ['all work and no play make andybons a dull boy'],
        b: ['elgoog elgoog elgoog'],
      }, {
        ab: [
          'Non eram nescius, Brute, cum, quae summis ingeniis ',
          'exquisitaque doctrina philosophi Graeco sermone tractavissent',
        ],
      }];
      setupSampleDiff({content});
      assert.equal(element._diffLength, 3);
      assert.isFalse(element.showNoChangeMessage(
          /* loading= */ false,
          element.prefs,
          element._diffLength,
          element.diff
      ));
    });

    test('do not show message if ignore whitespace is disabled', () => {
      const content = [{
        a: ['all work and no play make andybons a dull boy'],
        b: ['elgoog elgoog elgoog'],
      }, {
        ab: [
          'Non eram nescius, Brute, cum, quae summis ingeniis ',
          'exquisitaque doctrina philosophi Graeco sermone tractavissent',
        ],
      }];
      setupSampleDiff({ignore_whitespace: 'IGNORE_NONE', content});
      assert.isFalse(element.showNoChangeMessage(
          /* loading= */ false,
          element.prefs,
          element._diffLength,
          element.diff
      ));
    });
  });

  test('getDiffLength', () => {
    const diff = getMockDiffResponse();
    assert.equal(element.getDiffLength(diff), 52);
  });

  test('`render` event has contentRendered field in detail', done => {
    element = basicFixture.instantiate();
    element.prefs = {};
    sinon.stub(element.$.diffBuilder, 'render')
        .returns(Promise.resolve());
    element.addEventListener('render', event => {
      assert.isTrue(event.detail.contentRendered);
      done();
    });
    element._renderDiffTable();
  });

  test('_prefsEqual', () => {
    element = basicFixture.instantiate();
    assert.isTrue(element._prefsEqual(null, null));
    assert.isTrue(element._prefsEqual({}, {}));
    assert.isTrue(element._prefsEqual({x: 1}, {x: 1}));
    assert.isTrue(element._prefsEqual({x: 1, abc: 'def'}, {x: 1, abc: 'def'}));
    const somePref = {abc: 'def', p: true};
    assert.isTrue(element._prefsEqual(somePref, somePref));

    assert.isFalse(element._prefsEqual({}, null));
    assert.isFalse(element._prefsEqual(null, {}));
    assert.isFalse(element._prefsEqual({x: 1}, {x: 2}));
    assert.isFalse(element._prefsEqual({x: 1, y: 'abc'}, {x: 1, y: 'abcd'}));
    assert.isFalse(element._prefsEqual({x: 1, y: 'abc'}, {x: 1}));
    assert.isFalse(element._prefsEqual({x: 1}, {x: 1, y: 'abc'}));
  });
});
