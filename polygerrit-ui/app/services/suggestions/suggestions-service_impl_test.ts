/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import {assert} from '@open-wc/testing';
import '../../test/common-test-setup';
import {testResolver} from '../../test/common-test-setup';
import {
  createComment,
  createFixSuggestionInfo,
  createParsedChange,
} from '../../test/test-data-generators';
import {grReportingMock} from '../gr-reporting/gr-reporting_mock';
import {PluginsModel} from '../../models/plugins/plugins-model';
import {ChangeModel, changeModelToken} from '../../models/change/change-model';
import {ResponseCode} from '../../api/suggestions';
import {RevisionPatchSetNum} from '../../api/rest-api';
import {ReportSource} from './suggestions-service';
import {GrSuggestionsService} from './suggestions-service_impl';

suite('suggestions-service_impl tests', () => {
  let pluginsModel: PluginsModel;
  let changeModel: ChangeModel;
  let service: GrSuggestionsService;
  let alertListener: sinon.SinonSpy;

  setup(() => {
    pluginsModel = new PluginsModel();
    changeModel = testResolver(changeModelToken);
    changeModel.updateStateChange(createParsedChange());
    service = new GrSuggestionsService(
      grReportingMock,
      pluginsModel,
      changeModel
    );
    alertListener = sinon.spy();
    document.addEventListener('show-alert', alertListener);
  });

  teardown(() => {
    document.removeEventListener('show-alert', alertListener);
    pluginsModel.finalize();
    service.finalize();
  });

  test('enables Get AI Fix when plugin only implements requestAiFix', () => {
    pluginsModel.suggestionsRegister({
      pluginName: 'chat-only-plugin',
      provider: {
        requestAiFix: async () => {
          return {responseCode: ResponseCode.OK, fix_suggestions: []};
        },
      },
    });
    const comment = {...createComment(), path: 'src/foo.ts', line: 10};

    assert.isFalse(service.isGeneratedSuggestedFixEnabled('src/foo.ts'));
    assert.isFalse(service.isGeneratedSuggestedFixEnabledForComment(comment));
    assert.isTrue(service.isRequestAiFixEnabled('src/foo.ts'));
  });

  test('enables both inline fixes and Get AI Fix when plugin implements suggestFix', () => {
    pluginsModel.suggestionsRegister({
      pluginName: 'inline-only-plugin',
      provider: {
        suggestFix: async () => {
          return {responseCode: ResponseCode.OK, fix_suggestions: []};
        },
      },
    });
    const comment = {...createComment(), path: 'src/foo.ts', line: 10};

    assert.isTrue(service.isGeneratedSuggestedFixEnabled('src/foo.ts'));
    assert.isTrue(service.isGeneratedSuggestedFixEnabledForComment(comment));
    assert.isTrue(service.isRequestAiFixEnabled('src/foo.ts'));
  });

  test('disables fixes when plugin only implements autocompleteComment', () => {
    pluginsModel.suggestionsRegister({
      pluginName: 'autocomplete-only-plugin',
      provider: {
        autocompleteComment: async () => {
          return {responseCode: ResponseCode.OK, completion: 'done'};
        },
      },
    });
    const comment = {...createComment(), path: 'src/foo.ts', line: 10};

    assert.isFalse(service.isGeneratedSuggestedFixEnabled('src/foo.ts'));
    assert.isFalse(service.isGeneratedSuggestedFixEnabledForComment(comment));
    assert.isFalse(service.isRequestAiFixEnabled('src/foo.ts'));
  });

  test('routes Get AI Fix requests to requestAiFix and suppresses empty-OK alert', async () => {
    const requestAiFix = sinon
      .stub()
      .resolves({responseCode: ResponseCode.OK, fix_suggestions: []});
    const suggestFix = sinon.stub().resolves({
      responseCode: ResponseCode.OK,
      fix_suggestions: [
        {
          ...createFixSuggestionInfo(),
          replacements: [
            {
              path: 'src/foo.ts',
              replacement: 'const x = 1;',
              range: {
                start_line: 12,
                start_character: 0,
                end_line: 12,
                end_character: 10,
              },
            },
          ],
        },
      ],
    });
    pluginsModel.suggestionsRegister({
      pluginName: 'hybrid-plugin',
      provider: {requestAiFix, suggestFix},
    });

    const aiFixResult = await service.generateSuggestedFix({
      prompt: 'Fix null check',
      patchsetNumber: 1 as RevisionPatchSetNum,
      filePath: 'src/foo.ts',
      lineNumber: 10,
      reportSource: ReportSource.GET_AI_FIX_FOR_COMMENT,
    });

    assert.isUndefined(aiFixResult);
    assert.isTrue(requestAiFix.calledOnce);
    assert.isFalse(suggestFix.called);
    assert.isFalse(alertListener.called);

    // Draft-comment fix generation still calls suggestFix, not requestAiFix.
    const draftResult = await service.generateSuggestedFix({
      prompt: 'Fix typo',
      patchsetNumber: 1 as RevisionPatchSetNum,
      filePath: 'src/foo.ts',
      lineNumber: 12,
      reportSource: ReportSource.FIX_FOR_REVIEWER_COMMENT,
    });

    assert.isDefined(draftResult);
    assert.isTrue(requestAiFix.calledOnce);
    assert.isTrue(suggestFix.calledOnce);
  });

  test('falls back to suggestFix for Get AI Fix and fires alert on NO_SUGGESTION', async () => {
    const suggestFix = sinon.stub().resolves({
      responseCode: ResponseCode.NO_SUGGESTION,
      fix_suggestions: [],
    });
    pluginsModel.suggestionsRegister({
      pluginName: 'inline-only-plugin',
      provider: {suggestFix},
    });

    const result = await service.generateSuggestedFix({
      prompt: 'Fix lint warning',
      patchsetNumber: 1 as RevisionPatchSetNum,
      filePath: 'src/foo.ts',
      reportSource: ReportSource.GET_AI_FIX_FOR_CHECK,
    });

    assert.isUndefined(result);
    assert.isTrue(suggestFix.calledOnce);
    assert.isTrue(alertListener.calledOnce);
    assert.equal(
      alertListener.firstCall.args[0].detail.message,
      'No suitable AI fix could be found'
    );
  });
});
