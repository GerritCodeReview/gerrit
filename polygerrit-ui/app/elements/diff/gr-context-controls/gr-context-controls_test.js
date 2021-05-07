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

 import '../../../test/common-test-setup-karma.js';
 import '../gr-diff/gr-diff-group.js';
 import './gr-context-controls.js'
//  import './gr-diff-builder.js';
 import '../gr-context-controls/gr-context-controls.js';
 import {getMockDiffResponse} from '../../../test/mocks/diff-response.js';
//  import './gr-diff-builder-element.js';
 import {stubBaseUrl} from '../../../test/test-utils.js';
 import {flush} from '@polymer/polymer/lib/legacy/polymer.dom.js';
 import {GrAnnotation} from '../gr-diff-highlight/gr-annotation.js';
 import {GrDiffLine, GrDiffLineType} from '../gr-diff/gr-diff-line.js';
 import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group.js';
 import {GrDiffBuilder} from './gr-diff-builder.js';
 import {GrDiffBuilderSideBySide} from './gr-diff-builder-side-by-side.js';
 import {html} from '@polymer/polymer/lib/utils/html-tag.js';
 import {DiffViewMode} from '../../../api/diff.js';
 import {stubRestApi} from '../../../test/test-utils.js';


 const basicFixture = fixtureFromElement('gr-context-controls');

//  const basicFixture = fixtureFromTemplate(html`
//      <gr-context-controls></gr-context-controls>
//  `);

//  const divWithTextFixture = fixtureFromTemplate(html`
//  <div>Lorem ipsum dolor sit amet, suspendisse inceptos vehicula</div>
//  `);

//  const mockDiffFixture = fixtureFromTemplate(html`
//  <gr-diff-builder view-mode="SIDE_BY_SIDE">
//        <table id="diffTable"></table>
//      </gr-diff-builder>
//  `);

 suite('gr-context-control tests', () => {
   let prefs;
   let element;
   let builder;

  //  const LINE_FEED_HTML = '<span class="style-scope gr-diff br"></span>';

   setup(() => {
     element = basicFixture.instantiate();

     prefs = {
       line_length: 10,
       show_tabs: true,
       tab_size: 4,
     };
    //  builder = new GrDiffBuilder({content: []}, prefs);
   });

   suite('context control', () => {
     function createContextGroups(options) {
       const offset = options.offset || 0;
       const numLines = options.count || 10;
       const lines = [];
       for (let i = 0; i < numLines; i++) {
         const line = new GrDiffLine(GrDiffLineType.BOTH);
         line.beforeNumber = offset + i + 1;
         line.afterNumber = offset + i + 1;
         line.text = 'lorem upsum';
         lines.push(line);
       }

       return [new GrDiffGroup(GrDiffGroupType.BOTH, lines)];
     }

     function createContextSectionForGroups() {
       const section = document.createElement('div');
       builder._createContextControls(
           section, createContextGroups(options), DiffViewMode.UNIFIED);
       return section;
     }

     function prepareControls(){
       const section = document.createElement('div');
      element.diff = {content: []};
      element.renderPrefs = {};
      element.section = document.createElement('div');
      element.contextGroups = createContextGroups({count: 10});
      return section;
     }

     test('no +10 buttons for 10 or less lines', async () => {
       prepareControls({count: 10});
       await flush();

       const buttons = element.shadowRoot.querySelectorAll('gr-button.showContext');
       assert.equal(buttons.length, 1);
       assert.equal(buttons[0].textContent.trim(), '+10 common lines');
     });


     test('context control at the top', () => {
       prepareControls({offset: 0, count: 20});
       await flush();
      //  const section = createContextSectionForGroups();
       const buttons = element.shadowRoot.querySelectorAll('gr-button.showContext');

       assert.equal(buttons.length, 2);
       assert.equal(buttons[0].textContent, '+20 common lines');
       assert.equal(buttons[1].textContent, '+10');

       assert.include([...buttons[0].classList.values()], 'belowButton');
       assert.include([...buttons[1].classList.values()], 'belowButton');
     });

  });
});




  //    test('context control in the middle', () => {
  //      builder._numLinesLeft = 50;
  //      const section = createContextSectionForGroups({offset: 10, count: 20});
  //      const buttons = section.querySelectorAll('gr-button.showContext');

  //      assert.equal(buttons.length, 3);
  //      assert.equal(buttons[0].textContent, '+20 common lines');
  //      assert.equal(buttons[1].textContent, '+10');
  //      assert.equal(buttons[2].textContent, '+10');

  //      assert.include([...buttons[0].classList.values()], 'centeredButton');
  //      assert.include([...buttons[1].classList.values()], 'aboveButton');
  //      assert.include([...buttons[2].classList.values()], 'belowButton');
  //    });

  //    test('context control at the bottom', () => {
  //      builder._numLinesLeft = 50;
  //      const section = createContextSectionForGroups({offset: 30, count: 20});
  //      const buttons = section.querySelectorAll('gr-button.showContext');

  //      assert.equal(buttons.length, 2);
  //      assert.equal(buttons[0].textContent, '+20 common lines');
  //      assert.equal(buttons[1].textContent, '+10');

  //      assert.include([...buttons[0].classList.values()], 'aboveButton');
  //      assert.include([...buttons[1].classList.values()], 'aboveButton');
  //    });

  //    suite('with block expansion', () => {
  //      setup(() => {
  //        builder._numLinesLeft = 50;
  //        renderPrefs.use_block_expansion = true;
  //        diffInfo.meta_b = {
  //          syntax_tree: [],
  //        };
  //      });

  //      test('context control with block expansion at the top', () => {
  //        const section = createContextSectionForGroups({offset: 0, count: 20});

  //        const fullExpansionButtons = section
  //            .querySelectorAll('.fullExpansion gr-button');
  //        const partialExpansionButtons = section
  //            .querySelectorAll('.partialExpansion gr-button');
  //        const blockExpansionButtons = section
  //            .querySelectorAll('.blockExpansion gr-button');
  //        assert.equal(fullExpansionButtons.length, 1);
  //        assert.equal(partialExpansionButtons.length, 1);
  //        assert.equal(blockExpansionButtons.length, 1);
  //        assert.equal(blockExpansionButtons[0].textContent, '+Block');
  //        assert.include([...blockExpansionButtons[0].classList.values()],
  //            'belowButton');
  //      });

  //      test('context control in the middle', () => {
  //        const section = createContextSectionForGroups({offset: 10, count: 20});

  //        const fullExpansionButtons = section
  //            .querySelectorAll('.fullExpansion gr-button');
  //        const partialExpansionButtons = section
  //            .querySelectorAll('.partialExpansion gr-button');
  //        const blockExpansionButtons = section
  //            .querySelectorAll('.blockExpansion gr-button');
  //        assert.equal(fullExpansionButtons.length, 1);
  //        assert.equal(partialExpansionButtons.length, 2);
  //        assert.equal(blockExpansionButtons.length, 2);
  //        assert.equal(blockExpansionButtons[0].textContent, '+Block');
  //        assert.equal(blockExpansionButtons[1].textContent, '+Block');
  //        assert.include([...blockExpansionButtons[0].classList.values()],
  //            'aboveButton');
  //        assert.include([...blockExpansionButtons[1].classList.values()],
  //            'belowButton');
  //      });

  //      test('context control at the bottom', () => {
  //        const section = createContextSectionForGroups({offset: 30, count: 20});

  //        const fullExpansionButtons = section
  //            .querySelectorAll('.fullExpansion gr-button');
  //        const partialExpansionButtons = section
  //            .querySelectorAll('.partialExpansion gr-button');
  //        const blockExpansionButtons = section
  //            .querySelectorAll('.blockExpansion gr-button');
  //        assert.equal(fullExpansionButtons.length, 1);
  //        assert.equal(partialExpansionButtons.length, 1);
  //        assert.equal(blockExpansionButtons.length, 1);
  //        assert.equal(blockExpansionButtons[0].textContent, '+Block');
  //        assert.include([...blockExpansionButtons[0].classList.values()],
  //            'aboveButton');
  //      });
  //    });
  //  });
//  });

