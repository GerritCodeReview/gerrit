/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import {createDiff} from '../../../test/test-data-generators';
import './gr-diff';
import {GrDiffBuilderImage} from '../gr-diff-builder/gr-diff-builder-image';
import {getComputedStyleValue} from '../../../utils/dom-util';
import {_setHiddenScroll} from '../../../scripts/hiddenscroll';
import {runA11yAudit} from '../../../test/a11y-test-utils';
import '@polymer/paper-button/paper-button';
import {
  DiffContent,
  DiffInfo,
  DiffPreferencesInfo,
  DiffViewMode,
  IgnoreWhitespaceType,
  Side,
} from '../../../api/diff';
import {
  mockPromise,
  mouseDown,
  query,
  queryAll,
  queryAndAssert,
} from '../../../test/test-utils';
import {AbortStop} from '../../../api/core';
import {waitForEventOnce} from '../../../utils/event-util';
import {GrDiff} from './gr-diff';
import {ImageInfo} from '../../../types/common';
import {GrRangedCommentHint} from '../gr-ranged-comment-hint/gr-ranged-comment-hint';
import {assertIsDefined} from '../../../utils/common-util';
import {fixture, html} from '@open-wc/testing-helpers';

suite('gr-diff a11y test', () => {
  test('audit', async () => {
    await runA11yAudit(fixtureFromElement('gr-diff'));
  });
});

suite('gr-diff tests', () => {
  let element: GrDiff;

  const MINIMAL_PREFS: DiffPreferencesInfo = {
    tab_size: 2,
    line_length: 80,
    font_size: 12,
    context: 3,
    ignore_whitespace: 'IGNORE_NONE',
  };

  setup(async () => {
    element = await fixture<GrDiff>(html`<gr-diff></gr-diff>`);
  });

  suite('selectionchange event handling', () => {
    let handleSelectionChangeStub: sinon.SinonSpy;

    const emulateSelection = function () {
      document.dispatchEvent(new CustomEvent('selectionchange'));
    };

    setup(async () => {
      handleSelectionChangeStub = sinon.spy(
        element.highlights,
        'handleSelectionChange'
      );
    });

    test('enabled if logged in', async () => {
      element.loggedIn = true;
      await element.updateComplete;
      emulateSelection();
      assert.isTrue(handleSelectionChangeStub.called);
    });

    test('ignored if logged out', async () => {
      element.loggedIn = false;
      await element.updateComplete;
      emulateSelection();
      assert.isFalse(handleSelectionChangeStub.called);
    });
  });

  test('cancel', () => {
    const cancelStub = sinon.stub(element.diffBuilder, 'cancel');
    element.cancel();
    assert.isTrue(cancelStub.calledOnce);
  });

  test('line limit with line_wrapping', async () => {
    element.prefs = {...MINIMAL_PREFS, line_wrapping: true};
    await element.updateComplete;
    assert.equal(getComputedStyleValue('--line-limit-marker', element), '80ch');
  });

  test('line limit without line_wrapping', async () => {
    element.prefs = {...MINIMAL_PREFS, line_wrapping: false};
    await element.updateComplete;
    assert.equal(getComputedStyleValue('--line-limit-marker', element), '-1px');
  });

  suite('FULL_RESPONSIVE mode', () => {
    setup(async () => {
      element.prefs = {...MINIMAL_PREFS};
      element.renderPrefs = {responsive_mode: 'FULL_RESPONSIVE'};
      await element.updateComplete;
    });

    test('line limit is based on line_length', async () => {
      element.prefs = {...element.prefs!, line_length: 100};
      await element.updateComplete;
      assert.equal(
        getComputedStyleValue('--line-limit-marker', element),
        '100ch'
      );
    });

    test('content-width should not be defined', () => {
      assert.equal(getComputedStyleValue('--content-width', element), 'none');
    });
  });

  suite('SHRINK_ONLY mode', () => {
    setup(async () => {
      element.prefs = {...MINIMAL_PREFS};
      element.renderPrefs = {responsive_mode: 'SHRINK_ONLY'};
      await element.updateComplete;
    });

    test('content-width should not be defined', () => {
      assert.equal(getComputedStyleValue('--content-width', element), 'none');
    });

    test('max-width considers two content columns in side-by-side', async () => {
      element.viewMode = DiffViewMode.SIDE_BY_SIDE;
      await element.updateComplete;
      assert.equal(
        getComputedStyleValue('--diff-max-width', element),
        'calc(2 * 80ch + 2 * 48px + 0ch + 1px + 2px)'
      );
    });

    test('max-width considers one content column in unified', async () => {
      element.viewMode = DiffViewMode.UNIFIED;
      await element.updateComplete;
      assert.equal(
        getComputedStyleValue('--diff-max-width', element),
        'calc(1 * 80ch + 2 * 48px + 0ch + 1px + 2px)'
      );
    });

    test('max-width considers font-size', async () => {
      element.prefs = {...element.prefs!, font_size: 13};
      await element.updateComplete;
      // Each line number column: 4 * 13 = 52px
      assert.equal(
        getComputedStyleValue('--diff-max-width', element),
        'calc(2 * 80ch + 2 * 52px + 0ch + 1px + 2px)'
      );
    });

    test('sign cols are considered if show_sign_col is true', async () => {
      element.renderPrefs = {...element.renderPrefs, show_sign_col: true};
      await element.updateComplete;
      assert.equal(
        getComputedStyleValue('--diff-max-width', element),
        'calc(2 * 80ch + 2 * 48px + 2ch + 1px + 2px)'
      );
    });
  });

  suite('not logged in', () => {
    setup(async () => {
      element.loggedIn = false;
      await element.updateComplete;
    });

    test('toggleLeftDiff', () => {
      element.toggleLeftDiff();
      assert.isTrue(element.classList.contains('no-left'));
      element.toggleLeftDiff();
      assert.isFalse(element.classList.contains('no-left'));
    });

    test('view does not start with displayLine classList', () => {
      const container = queryAndAssert(element, '.diffContainer');
      assert.isFalse(container.classList.contains('displayLine'));
    });

    test('displayLine class added when displayLine is true', async () => {
      element.displayLine = true;
      await element.updateComplete;
      const container = queryAndAssert(element, '.diffContainer');
      assert.isTrue(container.classList.contains('displayLine'));
    });

    test('thread groups', () => {
      const contentEl = document.createElement('div');

      element.path = 'file.txt';

      // No thread groups.
      assert.equal(contentEl.querySelectorAll('.thread-group').length, 0);

      // A thread group gets created.
      const threadGroupEl = element.getOrCreateThreadGroup(
        contentEl,
        Side.LEFT
      );
      assert.isOk(threadGroupEl);

      // The new thread group can be fetched.
      assert.equal(contentEl.querySelectorAll('.thread-group').length, 1);
    });

    suite('image diffs', () => {
      let mockFile1: ImageInfo;
      let mockFile2: ImageInfo;
      setup(() => {
        mockFile1 = {
          body:
            'Qk06AAAAAAAAADYAAAAoAAAAAQAAAP////8BACAAAAAAAAAAAAATCwAAE' +
            'wsAAAAAAAAAAAAAAAAA/w==',
          type: 'image/bmp',
        };
        mockFile2 = {
          body:
            'Qk06AAAAAAAAADYAAAAoAAAAAQAAAP////8BACAAAAAAAAAAAAATCwAAE' +
            'wsAAAAAAAAAAAAA/////w==',
          type: 'image/bmp',
        };

        element.isImageDiff = true;
        element.prefs = {
          context: 10,
          cursor_blink_rate: 0,
          font_size: 12,
          ignore_whitespace: 'IGNORE_NONE',
          line_length: 100,
          line_wrapping: false,
          show_line_endings: true,
          show_tabs: true,
          show_whitespace_errors: true,
          syntax_highlighting: true,
          tab_size: 8,
        };
      });

      test('renders image diffs with same file name', async () => {
        element.baseImage = mockFile1;
        element.revisionImage = mockFile2;
        element.diff = {
          meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 66},
          meta_b: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 560},
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
        await waitForEventOnce(element, 'render');

        // Recognizes that it should be an image diff.
        assert.isTrue(element.isImageDiff);
        assert.instanceOf(element.diffBuilder.builder, GrDiffBuilderImage);

        // Left image rendered with the parent commit's version of the file.
        assertIsDefined(element.diffTable);
        const diffTable = element.diffTable;
        const leftImage = queryAndAssert(diffTable, 'td.left img');
        const leftLabel = queryAndAssert(diffTable, 'td.left label');
        const leftLabelContent = queryAndAssert(leftLabel, '.label');
        const leftLabelName = query(leftLabel, '.name');

        const rightImage = queryAndAssert(diffTable, 'td.right img');
        const rightLabel = queryAndAssert(diffTable, 'td.right label');
        const rightLabelContent = queryAndAssert(rightLabel, '.label');
        const rightLabelName = query(rightLabel, '.name');

        assert.isNotOk(rightLabelName);
        assert.isNotOk(leftLabelName);

        assert.equal(
          leftImage.getAttribute('src'),
          'data:image/bmp;base64,' + mockFile1.body
        );
        assert.isTrue(leftLabelContent.textContent?.includes('image/bmp'));

        assert.equal(
          rightImage.getAttribute('src'),
          'data:image/bmp;base64,' + mockFile2.body
        );
        assert.isTrue(rightLabelContent.textContent?.includes('image/bmp'));
      });

      test('renders image diffs with a different file name', async () => {
        const mockDiff: DiffInfo = {
          meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 66},
          meta_b: {name: 'carrot2.jpg', content_type: 'image/jpeg', lines: 560},
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

        element.baseImage = mockFile1;
        element.baseImage._name = mockDiff.meta_a!.name;
        element.revisionImage = mockFile2;
        element.revisionImage._name = mockDiff.meta_b!.name;
        element.diff = mockDiff;
        await waitForEventOnce(element, 'render');

        // Recognizes that it should be an image diff.
        assert.isTrue(element.isImageDiff);
        assert.instanceOf(element.diffBuilder.builder, GrDiffBuilderImage);

        // Left image rendered with the parent commit's version of the file.
        assertIsDefined(element.diffTable);
        const diffTable = element.diffTable;
        const leftImage = queryAndAssert(diffTable, 'td.left img');
        const leftLabel = queryAndAssert(diffTable, 'td.left label');
        const leftLabelContent = queryAndAssert(leftLabel, '.label');
        const leftLabelName = queryAndAssert(leftLabel, '.name');

        const rightImage = queryAndAssert(diffTable, 'td.right img');
        const rightLabel = queryAndAssert(diffTable, 'td.right label');
        const rightLabelContent = queryAndAssert(rightLabel, '.label');
        const rightLabelName = queryAndAssert(rightLabel, '.name');

        assert.isOk(rightLabelName);
        assert.isOk(leftLabelName);
        assert.equal(leftLabelName.textContent, mockDiff.meta_a?.name);
        assert.equal(rightLabelName.textContent, mockDiff.meta_b?.name);

        assert.isOk(leftImage);
        assert.equal(
          leftImage.getAttribute('src'),
          'data:image/bmp;base64,' + mockFile1.body
        );
        assert.isTrue(leftLabelContent.textContent?.includes('image/bmp'));

        assert.isOk(rightImage);
        assert.equal(
          rightImage.getAttribute('src'),
          'data:image/bmp;base64,' + mockFile2.body
        );
        assert.isTrue(rightLabelContent.textContent?.includes('image/bmp'));
      });

      test('renders added image', async () => {
        const mockDiff: DiffInfo = {
          meta_b: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 560},
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

        const promise = mockPromise();
        function rendered() {
          promise.resolve();
        }
        element.addEventListener('render', rendered);

        element.revisionImage = mockFile2;
        element.diff = mockDiff;
        await promise;
        element.removeEventListener('render', rendered);
        // Recognizes that it should be an image diff.
        assert.isTrue(element.isImageDiff);
        assert.instanceOf(element.diffBuilder.builder, GrDiffBuilderImage);

        assertIsDefined(element.diffTable);
        const diffTable = element.diffTable;
        const leftImage = query(diffTable, 'td.left img');
        assert.isNotOk(leftImage);
        queryAndAssert(diffTable, 'td.right img');
      });

      test('renders removed image', async () => {
        const mockDiff: DiffInfo = {
          meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 560},
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
        const promise = mockPromise();
        function rendered() {
          promise.resolve();
        }
        element.addEventListener('render', rendered);

        element.baseImage = mockFile1;
        element.diff = mockDiff;
        await promise;
        element.removeEventListener('render', rendered);
        // Recognizes that it should be an image diff.
        assert.isTrue(element.isImageDiff);
        assert.instanceOf(element.diffBuilder.builder, GrDiffBuilderImage);

        assertIsDefined(element.diffTable);
        const diffTable = element.diffTable;
        queryAndAssert(diffTable, 'td.left img');
        const rightImage = query(diffTable, 'td.right img');
        assert.isNotOk(rightImage);
      });

      test('does not render disallowed image type', async () => {
        const mockDiff: DiffInfo = {
          meta_a: {
            name: 'carrot.jpg',
            content_type: 'image/jpeg-evil',
            lines: 560,
          },
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

        const promise = mockPromise();
        function rendered() {
          promise.resolve();
        }
        element.addEventListener('render', rendered);

        element.baseImage = mockFile1;
        element.diff = mockDiff;
        await promise;
        element.removeEventListener('render', rendered);
        // Recognizes that it should be an image diff.
        assert.isTrue(element.isImageDiff);
        assert.instanceOf(element.diffBuilder.builder, GrDiffBuilderImage);
        assertIsDefined(element.diffTable);
        const diffTable = element.diffTable;
        const leftImage = query(diffTable, 'td.left img');
        assert.isNotOk(leftImage);
      });
    });

    test('handleTap lineNum', async () => {
      const addDraftStub = sinon.stub(element, 'addDraftAtLine');
      const el = document.createElement('div');
      el.className = 'lineNum';
      const promise = mockPromise();
      el.addEventListener('click', e => {
        element.handleTap(e);
        assert.isTrue(addDraftStub.called);
        assert.equal(addDraftStub.lastCall.args[0], el);
        promise.resolve();
      });
      el.click();
      await promise;
    });

    test('handleTap content', async () => {
      const content = document.createElement('div');
      const lineEl = document.createElement('div');
      lineEl.className = 'lineNum';
      const row = document.createElement('div');
      row.appendChild(lineEl);
      row.appendChild(content);

      const selectStub = sinon.stub(element, 'selectLine');

      content.className = 'content';
      const promise = mockPromise();
      content.addEventListener('click', e => {
        element.handleTap(e);
        assert.isTrue(selectStub.called);
        assert.equal(selectStub.lastCall.args[0], lineEl);
        promise.resolve();
      });
      content.click();
      await promise;
    });

    suite('getCursorStops', () => {
      async function setupDiff() {
        element.diff = createDiff();
        element.prefs = {
          context: 10,
          tab_size: 8,
          font_size: 12,
          line_length: 100,
          cursor_blink_rate: 0,
          line_wrapping: false,

          show_line_endings: true,
          show_tabs: true,
          show_whitespace_errors: true,
          syntax_highlighting: true,
          ignore_whitespace: 'IGNORE_NONE',
        };
        await element.updateComplete;
        element.renderDiffTable();
      }

      test('returns [] when hidden and noAutoRender', async () => {
        element.noAutoRender = true;
        await setupDiff();
        element.loading = false;
        await element.updateComplete;
        element.hidden = true;
        await element.updateComplete;
        assert.equal(element.getCursorStops().length, 0);
      });

      test('returns one stop per line and one for the file row', async () => {
        await setupDiff();
        element.loading = false;
        await element.updateComplete;
        const ROWS = 48;
        const FILE_ROW = 1;
        assert.equal(element.getCursorStops().length, ROWS + FILE_ROW);
      });

      test('returns an additional AbortStop when still loading', async () => {
        await setupDiff();
        element.loading = true;
        await element.updateComplete;
        const ROWS = 48;
        const FILE_ROW = 1;
        const actual = element.getCursorStops();
        assert.equal(actual.length, ROWS + FILE_ROW + 1);
        assert.isTrue(actual[actual.length - 1] instanceof AbortStop);
      });
    });

    test('adds .hiddenscroll', async () => {
      _setHiddenScroll(true);
      element.displayLine = true;
      await element.updateComplete;
      const container = queryAndAssert(element, '.diffContainer');
      assert.include(container.className, 'hiddenscroll');
    });
  });

  suite('logged in', async () => {
    let fakeLineEl: HTMLElement;
    setup(async () => {
      element.loggedIn = true;

      fakeLineEl = {
        getAttribute: sinon.stub().returns(42),
        classList: {
          contains: sinon.stub().returns(true),
        },
      } as unknown as HTMLElement;
      await element.updateComplete;
    });

    test('addDraftAtLine', () => {
      sinon.stub(element, 'selectLine');
      const createCommentStub = sinon.stub(element, 'createComment');
      element.addDraftAtLine(fakeLineEl);
      assert.isTrue(createCommentStub.calledWithExactly(fakeLineEl, 42));
    });

    test('adds long range comment hint', async () => {
      const range = {
        start_line: 1,
        end_line: 12,
        start_character: 0,
        end_character: 0,
      };
      const threadEl = document.createElement('div');
      threadEl.className = 'comment-thread';
      threadEl.setAttribute('diff-side', 'right');
      threadEl.setAttribute('line-num', '1');
      threadEl.setAttribute('range', JSON.stringify(range));
      threadEl.setAttribute('slot', 'right-1');
      const content = [
        {
          a: ['asdf'],
        },
        {
          ab: Array(13).fill('text'),
        },
      ];
      await setupSampleDiff({content});
      await waitForEventOnce(element, 'render');

      element.appendChild(threadEl);
      await waitForEventOnce(element, 'render');

      const hint = queryAndAssert<GrRangedCommentHint>(
        element,
        'gr-ranged-comment-hint'
      );
      assert.deepEqual(hint.range, range);
    });

    test('no duplicate range hint for same thread', async () => {
      const range = {
        start_line: 1,
        end_line: 12,
        start_character: 0,
        end_character: 0,
      };
      const threadEl = document.createElement('div');
      threadEl.className = 'comment-thread';
      threadEl.setAttribute('diff-side', 'right');
      threadEl.setAttribute('line-num', '1');
      threadEl.setAttribute('range', JSON.stringify(range));
      threadEl.setAttribute('slot', 'right-1');
      const firstHint = document.createElement('gr-ranged-comment-hint');
      firstHint.range = range;
      firstHint.setAttribute('slot', 'right-1');
      const content = [
        {
          a: ['asdf'],
        },
        {
          ab: Array(13).fill('text'),
        },
      ];
      await setupSampleDiff({content});
      await waitForEventOnce(element, 'render');

      element.appendChild(firstHint);
      element.appendChild(threadEl);
      await waitForEventOnce(element, 'render');

      assert.equal(
        element.querySelectorAll('gr-ranged-comment-hint').length,
        1
      );
    });

    test('removes long range comment hint when comment is discarded', async () => {
      const range = {
        start_line: 1,
        end_line: 7,
        start_character: 0,
        end_character: 0,
      };
      const threadEl = document.createElement('div');
      threadEl.className = 'comment-thread';
      threadEl.setAttribute('diff-side', 'right');
      threadEl.setAttribute('line-num', '1');
      threadEl.setAttribute('range', JSON.stringify(range));
      threadEl.setAttribute('slot', 'right-1');
      const content = [
        {
          a: [],
          b: [],
        },
        {
          ab: Array(8).fill('text'),
        },
      ];
      setupSampleDiff({content});
      element.appendChild(threadEl);
      await flush();

      threadEl.remove();
      await flush();

      assert.isEmpty(element.querySelectorAll('gr-ranged-comment-hint'));
    });

    suite('change in preferences', () => {
      setup(async () => {
        element.diff = {
          meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 66},
          meta_b: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 560},
          diff_header: [],
          intraline_status: 'OK',
          change_type: 'MODIFIED',
          content: [{skip: 66}],
        };
        await element.updateComplete;
        await element.renderDiffTableTask?.flush();
      });

      test('change in preferences re-renders diff', async () => {
        const stub = sinon.stub(element, 'renderDiffTable');
        element.prefs = {
          ...MINIMAL_PREFS,
        };
        await element.updateComplete;
        await element.renderDiffTableTask?.flush();
        assert.isTrue(stub.called);
      });

      test('adding/removing property in preferences re-renders diff', async () => {
        const stub = sinon.stub(element, 'renderDiffTable');
        const newPrefs1: DiffPreferencesInfo = {
          ...MINIMAL_PREFS,
          line_wrapping: true,
        };
        element.prefs = newPrefs1;
        await element.updateComplete;
        await element.renderDiffTableTask?.flush();
        assert.isTrue(stub.called);
        stub.reset();

        const newPrefs2 = {...newPrefs1};
        delete newPrefs2.line_wrapping;
        element.prefs = newPrefs2;
        await element.updateComplete;
        await element.renderDiffTableTask?.flush();
        assert.isTrue(stub.called);
      });

      test(
        'change in preferences does not re-renders diff with ' +
          'noRenderOnPrefsChange',
        async () => {
          const stub = sinon.stub(element, 'renderDiffTable');
          element.noRenderOnPrefsChange = true;
          element.prefs = {
            ...MINIMAL_PREFS,
            context: 12,
          };
          await element.updateComplete;
          await element.renderDiffTableTask?.flush();
          assert.isFalse(stub.called);
        }
      );
    });
  });

  suite('diff header', () => {
    setup(async () => {
      element.diff = {
        meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 66},
        meta_b: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 560},
        diff_header: [],
        intraline_status: 'OK',
        change_type: 'MODIFIED',
        content: [{skip: 66}],
      };
      await element.updateComplete;
    });

    test('hidden', async () => {
      assert.equal(element.computeDiffHeaderItems().length, 0);
      element.diff?.diff_header?.push('diff --git a/test.jpg b/test.jpg');
      assert.equal(element.computeDiffHeaderItems().length, 0);
      element.diff?.diff_header?.push('index 2adc47d..f9c2f2c 100644');
      assert.equal(element.computeDiffHeaderItems().length, 0);
      element.diff?.diff_header?.push('--- a/test.jpg');
      assert.equal(element.computeDiffHeaderItems().length, 0);
      element.diff?.diff_header?.push('+++ b/test.jpg');
      assert.equal(element.computeDiffHeaderItems().length, 0);
      element.diff?.diff_header?.push('test');
      assert.equal(element.computeDiffHeaderItems().length, 1);
      element.requestUpdate('diff');
      await element.updateComplete;

      const header = queryAndAssert(element, '#diffHeader');
      assert.equal(header.textContent?.trim(), 'test');
    });

    test('binary files', () => {
      element.diff!.binary = true;
      assert.equal(element.computeDiffHeaderItems().length, 0);
      element.diff?.diff_header?.push('diff --git a/test.jpg b/test.jpg');
      assert.equal(element.computeDiffHeaderItems().length, 0);
      element.diff?.diff_header?.push('test');
      assert.equal(element.computeDiffHeaderItems().length, 1);
      element.diff?.diff_header?.push('Binary files differ');
      assert.equal(element.computeDiffHeaderItems().length, 1);
    });
  });

  suite('safety and bypass', () => {
    let renderStub: sinon.SinonStub;

    setup(async () => {
      renderStub = sinon.stub(element.diffBuilder, 'render').callsFake(() => {
        assertIsDefined(element.diffTable);
        const diffTable = element.diffTable;
        diffTable.dispatchEvent(
          new CustomEvent('render', {bubbles: true, composed: true})
        );
        return Promise.resolve();
      });
      sinon.stub(element, 'getDiffLength').returns(10000);
      element.diff = createDiff();
      element.noRenderOnPrefsChange = true;
      await element.updateComplete;
    });

    test('large render w/ context = 10', async () => {
      element.prefs = {...MINIMAL_PREFS, context: 10};
      const promise = mockPromise();
      function rendered() {
        assert.isTrue(renderStub.called);
        assert.isFalse(element.showWarning);
        promise.resolve();
        element.removeEventListener('render', rendered);
      }
      element.addEventListener('render', rendered);
      element.renderDiffTable();
      await promise;
    });

    test('large render w/ whole file and bypass', async () => {
      element.prefs = {...MINIMAL_PREFS, context: -1};
      element.safetyBypass = 10;
      const promise = mockPromise();
      function rendered() {
        assert.isTrue(renderStub.called);
        assert.isFalse(element.showWarning);
        promise.resolve();
        element.removeEventListener('render', rendered);
      }
      element.addEventListener('render', rendered);
      element.renderDiffTable();
      await promise;
    });

    test('large render w/ whole file and no bypass', async () => {
      element.prefs = {...MINIMAL_PREFS, context: -1};
      const promise = mockPromise();
      function rendered() {
        assert.isFalse(renderStub.called);
        assert.isTrue(element.showWarning);
        promise.resolve();
        element.removeEventListener('render', rendered);
      }
      element.addEventListener('render', rendered);
      element.renderDiffTable();
      await promise;
    });

    test('toggles expand context using bypass', async () => {
      element.prefs = {...MINIMAL_PREFS, context: 3};

      element.toggleAllContext();
      element.renderDiffTable();
      await element.updateComplete;

      assert.equal(element.prefs.context, 3);
      assert.equal(element.safetyBypass, -1);
      assert.equal(element.diffBuilder.prefs.context, -1);
    });

    test('toggles collapse context from bypass', async () => {
      element.prefs = {...MINIMAL_PREFS, context: 3};
      element.safetyBypass = -1;

      element.toggleAllContext();
      element.renderDiffTable();
      await element.updateComplete;

      assert.equal(element.prefs.context, 3);
      assert.isNull(element.safetyBypass);
      assert.equal(element.diffBuilder.prefs.context, 3);
    });

    test('toggles collapse context from pref using default', async () => {
      element.prefs = {...MINIMAL_PREFS, context: -1};

      element.toggleAllContext();
      element.renderDiffTable();
      await element.updateComplete;

      assert.equal(element.prefs.context, -1);
      assert.equal(element.safetyBypass, 10);
      assert.equal(element.diffBuilder.prefs.context, 10);
    });
  });

  suite('blame', () => {
    test('unsetting', async () => {
      element.blame = [];
      const setBlameSpy = sinon.spy(element.diffBuilder, 'setBlame');
      element.classList.add('showBlame');
      element.blame = null;
      await element.updateComplete;
      assert.isTrue(setBlameSpy.calledWithExactly(null));
      assert.isFalse(element.classList.contains('showBlame'));
    });

    test('setting', async () => {
      element.blame = [
        {
          author: 'test-author',
          time: 12345,
          commit_msg: '',
          id: 'commit id',
          ranges: [{start: 1, end: 2}],
        },
      ];
      await element.updateComplete;
      assert.isTrue(element.classList.contains('showBlame'));
    });
  });

  suite('trailing newline warnings', () => {
    const NO_NEWLINE_LEFT = 'No newline at end of left file.';
    const NO_NEWLINE_RIGHT = 'No newline at end of right file.';

    const getWarning = (element: GrDiff) => {
      const warningElement = queryAndAssert(element, '.newlineWarning');
      return warningElement.textContent;
    };

    setup(async () => {
      element.showNewlineWarningLeft = false;
      element.showNewlineWarningRight = false;
      await element.updateComplete;
    });

    test('shows combined warning if both sides set to warn', async () => {
      element.showNewlineWarningLeft = true;
      element.showNewlineWarningRight = true;
      await element.updateComplete;
      assert.include(
        getWarning(element),
        NO_NEWLINE_LEFT + ' \u2014 ' + NO_NEWLINE_RIGHT
      ); // \u2014 - '—'
    });

    suite('showNewlineWarningLeft', () => {
      test('show warning if true', async () => {
        element.showNewlineWarningLeft = true;
        await element.updateComplete;
        assert.include(getWarning(element), NO_NEWLINE_LEFT);
      });

      test('hide warning if false', async () => {
        element.showNewlineWarningLeft = false;
        await element.updateComplete;
        assert.notInclude(getWarning(element), NO_NEWLINE_LEFT);
      });
    });

    suite('showNewlineWarningRight', () => {
      test('show warning if true', async () => {
        element.showNewlineWarningRight = true;
        await element.updateComplete;
        assert.include(getWarning(element), NO_NEWLINE_RIGHT);
      });

      test('hide warning if false', async () => {
        element.showNewlineWarningRight = false;
        await element.updateComplete;
        assert.notInclude(getWarning(element), NO_NEWLINE_RIGHT);
      });
    });

    test('computeNewlineWarningClass', () => {
      const hidden = 'newlineWarning hidden';
      const shown = 'newlineWarning';
      element.loading = true;
      assert.equal(element.computeNewlineWarningClass(false), hidden);
      assert.equal(element.computeNewlineWarningClass(true), hidden);
      element.loading = false;
      assert.equal(element.computeNewlineWarningClass(false), hidden);
      assert.equal(element.computeNewlineWarningClass(true), shown);
    });
  });

  suite('key locations', () => {
    let renderStub: sinon.SinonStub;

    setup(async () => {
      element.prefs = {...MINIMAL_PREFS};
      renderStub = sinon.stub(element.diffBuilder, 'render');
      await element.updateComplete;
    });

    test('lineOfInterest is a key location', () => {
      element.lineOfInterest = {lineNum: 789, side: Side.LEFT};
      element.renderDiffTable();
      assert.isTrue(renderStub.called);
      assert.deepEqual(renderStub.lastCall.args[0], {
        left: {789: true},
        right: {},
      });
    });

    test('line comments are key locations', async () => {
      const threadEl = document.createElement('div');
      threadEl.className = 'comment-thread';
      threadEl.setAttribute('diff-side', 'right');
      threadEl.setAttribute('line-num', '3');
      element.appendChild(threadEl);
      await element.updateComplete;

      element.renderDiffTable();
      assert.isTrue(renderStub.called);
      assert.deepEqual(renderStub.lastCall.args[0], {
        left: {},
        right: {3: true},
      });
    });

    test('file comments are key locations', async () => {
      const threadEl = document.createElement('div');
      threadEl.className = 'comment-thread';
      threadEl.setAttribute('diff-side', 'left');
      element.appendChild(threadEl);
      await element.updateComplete;

      element.renderDiffTable();
      assert.isTrue(renderStub.called);
      assert.deepEqual(renderStub.lastCall.args[0], {
        left: {FILE: true},
        right: {},
      });
    });
  });
  const setupSampleDiff = async function (params: {
    content: DiffContent[];
    ignore_whitespace?: IgnoreWhitespaceType;
    binary?: boolean;
  }) {
    const {ignore_whitespace, content} = params;
    // binary can't be undefined, use false if not set
    const binary = params.binary || false;
    element.prefs = {
      ignore_whitespace: ignore_whitespace || 'IGNORE_ALL',
      context: 10,
      cursor_blink_rate: 0,
      font_size: 12,

      line_length: 100,
      line_wrapping: false,
      show_line_endings: true,
      show_tabs: true,
      show_whitespace_errors: true,
      syntax_highlighting: true,
      tab_size: 8,
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
    await element.updateComplete;
    element.renderDiffTable();
  };

  test('clear diff table content as soon as diff changes', async () => {
    const content = [
      {
        a: ['all work and no play make andybons a dull boy'],
      },
      {
        b: ['Non eram nescius, Brute, cum, quae summis ingeniis '],
      },
    ];
    function assertDiffTableWithContent() {
      assertIsDefined(element.diffTable);
      const diffTable = element.diffTable;
      assert.isTrue(diffTable.innerText.includes(content[0].a?.[0] ?? ''));
    }
    await setupSampleDiff({content});
    await element.renderDiffTableTask;
    assertDiffTableWithContent();
    element.diff = {...element.diff!};
    await element.updateComplete;
    // immediately cleaned up
    assertIsDefined(element.diffTable);
    const diffTable = element.diffTable;
    assert.equal(diffTable.innerHTML, '');
    element.renderDiffTable();
    await element.updateComplete;
    // rendered again
    assertDiffTableWithContent();
  });

  suite('selection test', () => {
    test('user-select set correctly on side-by-side view', async () => {
      const content = [
        {
          a: ['all work and no play make andybons a dull boy'],
          b: ['elgoog elgoog elgoog'],
        },
        {
          ab: [
            'Non eram nescius, Brute, cum, quae summis ingeniis ',
            'exquisitaque doctrina philosophi Graeco sermone tractavissent',
          ],
        },
      ];
      await setupSampleDiff({content});
      await element.updateComplete;

      const diffLine = queryAll<HTMLElement>(element, '.contentText')[2];
      assert.equal(getComputedStyle(diffLine).userSelect, 'none');
      mouseDown(diffLine);
      assert.equal(getComputedStyle(diffLine).userSelect, 'text');
    });

    test('user-select set correctly on unified view', async () => {
      const content = [
        {
          a: ['all work and no play make andybons a dull boy'],
          b: ['elgoog elgoog elgoog'],
        },
        {
          ab: [
            'Non eram nescius, Brute, cum, quae summis ingeniis ',
            'exquisitaque doctrina philosophi Graeco sermone tractavissent',
          ],
        },
      ];
      await setupSampleDiff({content});
      element.viewMode = DiffViewMode.UNIFIED;
      await element.updateComplete;
      const diffLine = queryAll<HTMLElement>(element, '.contentText')[2];
      assert.equal(getComputedStyle(diffLine).userSelect, 'none');
      mouseDown(diffLine);
      assert.equal(getComputedStyle(diffLine).userSelect, 'text');
    });
  });

  suite('whitespace changes only message', () => {
    test('show the message if ignore_whitespace is criteria matches', async () => {
      await setupSampleDiff({content: [{skip: 100}]});
      element.loading = false;
      assert.isTrue(element.showNoChangeMessage());
    });

    test('do not show the message for binary files', async () => {
      await setupSampleDiff({content: [{skip: 100}], binary: true});
      element.loading = false;
      assert.isFalse(element.showNoChangeMessage());
    });

    test('do not show the message if still loading', async () => {
      await setupSampleDiff({content: [{skip: 100}]});
      element.loading = true;
      assert.isFalse(element.showNoChangeMessage());
    });

    test('do not show the message if contains valid changes', async () => {
      const content = [
        {
          a: ['all work and no play make andybons a dull boy'],
          b: ['elgoog elgoog elgoog'],
        },
        {
          ab: [
            'Non eram nescius, Brute, cum, quae summis ingeniis ',
            'exquisitaque doctrina philosophi Graeco sermone tractavissent',
          ],
        },
      ];
      await setupSampleDiff({content});
      element.loading = false;
      assert.equal(element.diffLength, 3);
      assert.isFalse(element.showNoChangeMessage());
    });

    test('do not show message if ignore whitespace is disabled', async () => {
      const content = [
        {
          a: ['all work and no play make andybons a dull boy'],
          b: ['elgoog elgoog elgoog'],
        },
        {
          ab: [
            'Non eram nescius, Brute, cum, quae summis ingeniis ',
            'exquisitaque doctrina philosophi Graeco sermone tractavissent',
          ],
        },
      ];
      await setupSampleDiff({ignore_whitespace: 'IGNORE_NONE', content});
      element.loading = false;
      assert.isFalse(element.showNoChangeMessage());
    });
  });

  test('getDiffLength', () => {
    const diff = createDiff();
    assert.equal(element.getDiffLength(diff), 52);
  });
});
