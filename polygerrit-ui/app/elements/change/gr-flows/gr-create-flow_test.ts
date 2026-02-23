/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-create-flow';
import {assert, fixture, html} from '@open-wc/testing';
import {GrCreateFlow} from './gr-create-flow';
import {query, queryAll, queryAndAssert} from '../../../test/test-utils';
import {
  AccountId,
  EmailAddress,
  NumericChangeId,
  RepoName,
} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrAutocomplete} from '../../shared/gr-autocomplete/gr-autocomplete';
import {GrSearchAutocomplete} from '../../core/gr-search-autocomplete/gr-search-autocomplete';
import {FlowsModel, flowsModelToken} from '../../../models/flows/flows-model';
import {changeModelToken} from '../../../models/change/change-model';
import {
  createParsedChange,
  createRevision,
} from '../../../test/test-data-generators';
import {testResolver} from '../../../test/common-test-setup';
import {MdOutlinedTextField} from '@material/web/textfield/outlined-text-field';
import {getAppContext} from '../../../services/app-context';
import {FlowActionInfo, RevisionPatchSetNum} from '../../../api/rest-api';
import {MdOutlinedSelect} from '@material/web/select/outlined-select';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';

suite('gr-create-flow tests', () => {
  let element: GrCreateFlow;
  let flowsModel: FlowsModel;

  setup(async () => {
    const restApi = getAppContext().restApiService;
    sinon
      .stub(restApi, 'listFlowActions')
      .resolves([
        {name: 'act-1'},
        {name: 'act-2'},
        {name: 'vote'},
        {name: 'add-reviewer'},
        {name: 'submit'},
        {name: 'vote'},
      ] as FlowActionInfo[]);

    flowsModel = testResolver(flowsModelToken);
    const hostUrl =
      'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321';
    element = await fixture<GrCreateFlow>(
      html`<gr-create-flow
        .changeNum=${123 as NumericChangeId}
        .hostUrl=${hostUrl}
      ></gr-create-flow>`
    );
    await element.updateComplete;
  });

  suite('default actions', () => {
    test('renders initially', () => {
      const createButton = queryAndAssert<GrButton>(
        element,
        'gr-button[aria-label="Create Flow"]'
      );
      createButton.click();
      const createModal = queryAndAssert<HTMLDialogElement>(
        element,
        '#createModal'
      );
      assert.isTrue(createModal.open);

      const grDialog = queryAndAssert<GrDialog>(createModal, 'gr-dialog');

      assert.isDefined(queryAndAssert(grDialog, 'gr-search-autocomplete'));
      assert.isDefined(
        queryAndAssert(grDialog, 'md-outlined-select[label="Action"]')
      );
      assert.isDefined(
        queryAndAssert(grDialog, 'md-outlined-text-field[label="Parameters"]')
      );
      assert.isDefined(
        queryAndAssert(grDialog, 'gr-button[aria-label="Add Stage"]')
      );
    });

    test('opens and closes dialog', async () => {
      const createButton = queryAndAssert<GrButton>(
        element,
        'gr-button[aria-label="Create Flow"]'
      );
      const createModal = queryAndAssert<HTMLDialogElement>(
        element,
        '#createModal'
      );

      createButton.click();
      await element.updateComplete;
      assert.isTrue(createModal.open);

      const grDialog = queryAndAssert<GrDialog>(createModal, 'gr-dialog');
      const cancelButton = queryAndAssert<GrButton>(grDialog, '#cancel');
      cancelButton.click();
      await element.updateComplete;
      assert.isFalse(createModal.open);
    });

    test('adds and removes stages', async () => {
      const createButton = queryAndAssert<GrButton>(
        element,
        'gr-button[aria-label="Create Flow"]'
      );
      createButton.click();
      await element.updateComplete;
      const createModal = queryAndAssert<HTMLDialogElement>(
        element,
        '#createModal'
      );
      const grDialog = queryAndAssert<GrDialog>(createModal, 'gr-dialog');

      const searchAutocomplete = queryAndAssert<GrSearchAutocomplete>(
        grDialog,
        'gr-search-autocomplete'
      );
      const actionInput = queryAndAssert<MdOutlinedSelect>(
        grDialog,
        'md-outlined-select[label="Action"]'
      );
      const addButton = queryAndAssert<GrButton>(
        grDialog,
        'gr-button[aria-label="Add Stage"]'
      );

      searchAutocomplete.value = 'cond 1';
      await element.updateComplete;
      actionInput.value = 'act-1';
      actionInput.dispatchEvent(new Event('change'));
      await element.updateComplete;
      addButton.click();
      await element.updateComplete;

      assert.deepEqual(element.stages, [
        {
          condition:
            'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is cond 1',
          action: 'act-1',
          parameterStr: '',
        },
      ]);
      assert.equal(element['currentCondition'], '');
      assert.equal(element['currentAction'], '');

      searchAutocomplete.value = 'cond 2';
      await element.updateComplete;
      actionInput.value = 'act-2';
      actionInput.dispatchEvent(new Event('change'));
      await element.updateComplete;
      addButton.click();
      await element.updateComplete;

      assert.deepEqual(element.stages, [
        {
          condition:
            'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is cond 1',
          action: 'act-1',
          parameterStr: '',
        },
        {
          condition:
            'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is cond 2',
          action: 'act-2',
          parameterStr: '',
        },
      ]);

      let removeButtons = queryAll<GrButton>(
        grDialog,
        '.stage-list-item gr-button'
      );
      assert.lengthOf(removeButtons, 2);

      removeButtons[0].click();
      await element.updateComplete;

      assert.deepEqual(element.stages, [
        {
          condition:
            'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is cond 2',
          action: 'act-2',
          parameterStr: '',
        },
      ]);
      removeButtons = queryAll<GrButton>(
        grDialog,
        '.stage-list-item gr-button'
      );
      assert.lengthOf(removeButtons, 1);
    });

    test('creates a flow with one stage', async () => {
      const createFlowStub = sinon.stub(flowsModel, 'createFlow');

      const createButton = queryAndAssert<GrButton>(
        element,
        'gr-button[aria-label="Create Flow"]'
      );
      createButton.click();
      await element.updateComplete;
      const createModal = queryAndAssert<HTMLDialogElement>(
        element,
        '#createModal'
      );
      const grDialog = queryAndAssert<GrDialog>(createModal, 'gr-dialog');

      const searchAutocomplete = queryAndAssert<GrSearchAutocomplete>(
        grDialog,
        'gr-search-autocomplete'
      );
      const actionInput = queryAndAssert<MdOutlinedSelect>(
        grDialog,
        'md-outlined-select[label="Action"]'
      );
      const addButton = queryAndAssert<GrButton>(
        grDialog,
        'gr-button[aria-label="Add Stage"]'
      );
      searchAutocomplete.value = 'single condition';
      await element.updateComplete;
      actionInput.value = 'add-reviewer';
      actionInput.dispatchEvent(new Event('change'));
      await element.updateComplete;
      addButton.click();
      await element.updateComplete;

      const confirmButton = queryAndAssert<GrButton>(grDialog, '#confirm');
      confirmButton.click();
      await element.updateComplete;

      assert.isTrue(createFlowStub.calledOnce);
      const flowInput = createFlowStub.lastCall.args[0];
      assert.deepEqual(flowInput.stage_expressions, [
        {
          condition:
            'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is single condition',
          action: {name: 'add-reviewer'},
        },
      ]);
      assert.isFalse(createModal.open);
    });

    test('creates a flow with parameters', async () => {
      const createFlowStub = sinon.stub(flowsModel, 'createFlow');

      const createButton = queryAndAssert<GrButton>(
        element,
        'gr-button[aria-label="Create Flow"]'
      );
      createButton.click();
      await element.updateComplete;
      const createModal = queryAndAssert<HTMLDialogElement>(
        element,
        '#createModal'
      );
      const grDialog = queryAndAssert<GrDialog>(createModal, 'gr-dialog');

      const searchAutocomplete = queryAndAssert<GrSearchAutocomplete>(
        grDialog,
        'gr-search-autocomplete'
      );
      const actionInput = queryAndAssert<MdOutlinedSelect>(
        grDialog,
        'md-outlined-select[label="Action"]'
      );
      const parametersInput = queryAndAssert<MdOutlinedTextField>(
        grDialog,
        'md-outlined-text-field[label="Parameters"]'
      );
      const addButton = queryAndAssert<GrButton>(
        grDialog,
        'gr-button[aria-label="Add Stage"]'
      );
      searchAutocomplete.value = 'single condition';
      await element.updateComplete;
      actionInput.value = 'add-reviewer';
      actionInput.dispatchEvent(new Event('change'));
      await element.updateComplete;
      parametersInput.value = 'param1 param2';
      parametersInput.dispatchEvent(new Event('input'));
      await element.updateComplete;
      addButton.click();
      await element.updateComplete;

      const confirmButton = queryAndAssert<GrButton>(grDialog, '#confirm');
      confirmButton.click();
      await element.updateComplete;

      assert.isTrue(createFlowStub.calledOnce);
      const flowInput = createFlowStub.lastCall.args[0];
      assert.deepEqual(flowInput.stage_expressions, [
        {
          condition:
            'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is single condition',
          action: {name: 'add-reviewer', parameters: ['param1', 'param2']},
        },
      ]);
      assert.isFalse(createModal.open);
    });

    test('creates a flow with multiple stages', async () => {
      const createFlowStub = sinon.stub(flowsModel, 'createFlow');

      const createButton = queryAndAssert<GrButton>(
        element,
        'gr-button[aria-label="Create Flow"]'
      );
      createButton.click();
      await element.updateComplete;
      const createModal = queryAndAssert<HTMLDialogElement>(
        element,
        '#createModal'
      );
      const grDialog = queryAndAssert<GrDialog>(createModal, 'gr-dialog');

      const searchAutocomplete = queryAndAssert<GrSearchAutocomplete>(
        grDialog,
        'gr-search-autocomplete'
      );
      const actionInput = queryAndAssert<MdOutlinedSelect>(
        grDialog,
        'md-outlined-select[label="Action"]'
      );
      const addButton = queryAndAssert<GrButton>(
        grDialog,
        'gr-button[aria-label="Add Stage"]'
      );

      searchAutocomplete.value = 'cond 1';
      await element.updateComplete;
      actionInput.value = 'act-1';
      actionInput.dispatchEvent(new Event('change'));
      await element.updateComplete;
      addButton.click();
      await element.updateComplete;

      searchAutocomplete.value = 'cond 2';
      await element.updateComplete;
      actionInput.value = 'act-2';
      actionInput.dispatchEvent(new Event('change'));
      await element.updateComplete;
      addButton.click();
      await element.updateComplete;

      const confirmButton = queryAndAssert<GrButton>(grDialog, '#confirm');
      confirmButton.click();
      await element.updateComplete;

      assert.isTrue(createFlowStub.calledOnce);
      const flowInput = createFlowStub.lastCall.args[0];
      assert.deepEqual(flowInput.stage_expressions, [
        {
          condition:
            'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is cond 1',
          action: {name: 'act-1'},
        },
        {
          condition:
            'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is cond 2',
          action: {name: 'act-2'},
        },
      ]);
      assert.isFalse(createModal.open);
    });

    test('create flow with added stages and current input', async () => {
      const createFlowStub = sinon.stub(flowsModel, 'createFlow');

      const createButton = queryAndAssert<GrButton>(
        element,
        'gr-button[aria-label="Create Flow"]'
      );
      createButton.click();
      await element.updateComplete;
      const createModal = queryAndAssert<HTMLDialogElement>(
        element,
        '#createModal'
      );
      const grDialog = queryAndAssert<GrDialog>(createModal, 'gr-dialog');

      const searchAutocomplete = queryAndAssert<GrSearchAutocomplete>(
        grDialog,
        'gr-search-autocomplete'
      );
      const actionInput = queryAndAssert<MdOutlinedSelect>(
        grDialog,
        'md-outlined-select[label="Action"]'
      );
      const addButton = queryAndAssert<GrButton>(
        grDialog,
        'gr-button[aria-label="Add Stage"]'
      );

      searchAutocomplete.value = 'cond 1';
      await element.updateComplete;
      actionInput.value = 'act-1';
      actionInput.dispatchEvent(new Event('change'));
      await element.updateComplete;
      addButton.click();
      await element.updateComplete;
      searchAutocomplete.value = 'cond 2';
      await element.updateComplete;
      actionInput.value = 'act-2';
      actionInput.dispatchEvent(new Event('change'));
      await element.updateComplete;

      const confirmButton = queryAndAssert<GrButton>(grDialog, '#confirm');
      confirmButton.click();
      await element.updateComplete;

      assert.isTrue(createFlowStub.calledOnce);
      const flowInput = createFlowStub.lastCall.args[0];
      assert.deepEqual(flowInput.stage_expressions, [
        {
          condition:
            'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is cond 1',
          action: {name: 'act-1'},
        },
        {
          condition:
            'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is cond 2',
          action: {name: 'act-2'},
        },
      ]);
      assert.isFalse(createModal.open);
    });

    test('raw flow textarea is updated', async () => {
      const createButton = queryAndAssert<GrButton>(
        element,
        'gr-button[aria-label="Create Flow"]'
      );
      createButton.click();
      await element.updateComplete;
      const createModal = queryAndAssert<HTMLDialogElement>(
        element,
        '#createModal'
      );
      const grDialog = queryAndAssert<GrDialog>(createModal, 'gr-dialog');

      element.copyPasteExpanded = true;
      await element.updateComplete;

      const rawFlowTextarea = queryAndAssert<MdOutlinedTextField>(
        grDialog,
        'md-outlined-text-field[label="Copy and Paste existing flows"]'
      );
      assert.isDefined(rawFlowTextarea);
      assert.equal(rawFlowTextarea.value, '');

      const searchAutocomplete = queryAndAssert<GrSearchAutocomplete>(
        grDialog,
        'gr-search-autocomplete'
      );
      const actionInput = queryAndAssert<MdOutlinedSelect>(
        grDialog,
        'md-outlined-select[label="Action"]'
      );
      const paramsInput = queryAndAssert<MdOutlinedTextField>(
        grDialog,
        'md-outlined-text-field[label="Parameters"]'
      );
      const addButton = queryAndAssert<GrButton>(
        grDialog,
        'gr-button[aria-label="Add Stage"]'
      );

      // Add first stage
      searchAutocomplete.value = 'cond 1';
      await element.updateComplete;
      actionInput.value = 'act-1';
      actionInput.dispatchEvent(new Event('change'));
      await element.updateComplete;
      addButton.click();
      await element.updateComplete;

      assert.equal(
        element.flowString,
        'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is cond 1 -> act-1'
      );

      // Add second stage with parameters
      searchAutocomplete.value = 'cond 2';
      await element.updateComplete;
      actionInput.value = 'act-2';
      actionInput.dispatchEvent(new Event('change'));
      await element.updateComplete;
      paramsInput.value = 'param';
      paramsInput.dispatchEvent(new Event('input'));
      await element.updateComplete;
      addButton.click();
      await element.updateComplete;

      assert.equal(
        element.flowString,
        'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is cond 1 -> act-1;https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is cond 2 -> act-2 param'
      );

      // Remove first stage
      const removeButtons = queryAll<GrButton>(
        grDialog,
        '.stage-list-item gr-button'
      );
      removeButtons[0].click();
      await element.updateComplete;

      assert.equal(
        element.flowString,
        'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is cond 2 -> act-2 param'
      );
    });

    test('typing -> does not get overwritten', async () => {
      // Open Dialog
      const createButton = queryAndAssert<GrButton>(
        element,
        'gr-button[aria-label="Create Flow"]'
      );
      createButton.click();
      await element.updateComplete;
      const createModal = queryAndAssert<HTMLDialogElement>(
        element,
        '#createModal'
      );
      const grDialog = queryAndAssert<GrDialog>(createModal, 'gr-dialog');

      element.copyPasteExpanded = true;
      await element.updateComplete;

      // Find textarea
      const rawFlowTextarea = queryAndAssert<MdOutlinedTextField>(
        grDialog,
        'md-outlined-text-field[label="Copy and Paste existing flows"]'
      );

      // Simulate user typing a condition and '-> '
      rawFlowTextarea.value = 'cond 1 -';
      rawFlowTextarea.dispatchEvent(new InputEvent('input'));
      await element.updateComplete;
      assert.equal(element.flowString, 'cond 1 -');

      rawFlowTextarea.value = 'cond 1 -> ';
      rawFlowTextarea.dispatchEvent(new InputEvent('input'));
      await element.updateComplete;
      // Expected to preserve '-> ' and not revert to 'cond 1'
      assert.equal(element.flowString, 'cond 1 -> ');
    });

    test('adding stage with empty condition fails', async () => {
      const alertStub = sinon.stub();
      element.addEventListener('show-alert', alertStub);

      const createButton = queryAndAssert<GrButton>(
        element,
        'gr-button[aria-label="Create Flow"]'
      );
      createButton.click();
      await element.updateComplete;

      const grDialog = queryAndAssert<GrDialog>(element, 'gr-dialog');
      const actionInput = queryAndAssert<MdOutlinedSelect>(
        grDialog,
        'md-outlined-select[label="Action"]'
      );
      const addButton = queryAndAssert<GrButton>(
        grDialog,
        'gr-button[aria-label="Add Stage"]'
      );

      actionInput.value = 'act-1';
      actionInput.dispatchEvent(new Event('change'));
      await element.updateComplete;

      addButton.click();
      await element.updateComplete;

      assert.isTrue(alertStub.calledOnce);
      assert.equal(
        alertStub.lastCall.args[0].detail.message,
        'Condition string cannot be empty.'
      );
      assert.lengthOf(element.stages, 0);
    });

    test('creating flow with empty condition fails', async () => {
      const alertStub = sinon.stub();
      element.addEventListener('show-alert', alertStub);
      const createFlowStub = sinon.stub(flowsModel, 'createFlow');

      const createButton = queryAndAssert<GrButton>(
        element,
        'gr-button[aria-label="Create Flow"]'
      );
      createButton.click();
      await element.updateComplete;

      const grDialog = queryAndAssert<GrDialog>(element, 'gr-dialog');

      element.copyPasteExpanded = true;
      await element.updateComplete;

      // Add a stage with empty condition via raw flow textarea
      const rawFlowTextarea = queryAndAssert<MdOutlinedTextField>(
        grDialog,
        'md-outlined-text-field[label="Copy and Paste existing flows"]'
      );
      rawFlowTextarea.value = '-> act-1';
      rawFlowTextarea.dispatchEvent(new InputEvent('input'));
      await element.updateComplete;

      assert.lengthOf(element.stages, 1);
      assert.equal(element.stages[0].condition, '');

      const confirmButton = queryAndAssert<GrButton>(grDialog, '#confirm');
      confirmButton.click();
      await element.updateComplete;

      assert.isTrue(alertStub.calledOnce);
      assert.equal(
        alertStub.lastCall.args[0].detail.message,
        'All stages must have a condition.'
      );
      assert.isFalse(createFlowStub.called);
    });
  });

  suite('parameter input field', () => {
    test('is disabled when no action is selected', async () => {
      element.currentAction = '';
      await element.updateComplete;

      let textfield = queryAndAssert<MdOutlinedTextField>(
        element,
        '.textfield-input'
      );
      assert.isTrue(textfield.disabled);

      const actionInput = queryAndAssert<MdOutlinedSelect>(
        element,
        'md-outlined-select[label="Action"]'
      );
      actionInput.value = 'act-1';
      actionInput.dispatchEvent(new Event('change'));
      await element.updateComplete;

      textfield = queryAndAssert<MdOutlinedTextField>(
        element,
        '.textfield-input'
      );
      assert.isFalse(textfield.disabled);
    });

    test('renders md-outlined-text-field for non-add-reviewer action', async () => {
      const actionInput = queryAndAssert<MdOutlinedSelect>(
        element,
        'md-outlined-select[label="Action"]'
      );
      actionInput.value = 'act-1';
      actionInput.dispatchEvent(new Event('change'));
      await element.updateComplete;

      assert.isNotNull(query(element, '.textfield-input'));
      assert.isUndefined(query(element, '.autocomplete-input'));
    });

    test('renders gr-autocomplete for add-reviewer action', async () => {
      const actionInput = queryAndAssert<MdOutlinedSelect>(
        element,
        'md-outlined-select[label="Action"]'
      );
      actionInput.value = 'add-reviewer';
      actionInput.dispatchEvent(new Event('change'));
      await element.updateComplete;

      assert.isNotNull(query(element, '.autocomplete-input'));
      assert.isUndefined(query(element, '.textfield-input'));
    });

    test('shows correct placeholder for add-reviewer', async () => {
      element.currentAction = 'add-reviewer';
      await element.updateComplete;
      const autocomplete = queryAndAssert<GrAutocomplete>(
        element,
        '.autocomplete-input'
      );
      assert.equal(autocomplete.placeholder, 'user@example.com');
    });

    test('shows correct placeholder for vote', async () => {
      element.repoLabels = [];
      element.currentAction = 'vote';
      await element.updateComplete;
      const textfield = queryAndAssert<MdOutlinedTextField>(
        element,
        '.textfield-input'
      );
      assert.equal(textfield.placeholder, '<Label>+/-<Value>');
    });

    test('hides the parameter input for submit', async () => {
      element.currentAction = 'submit';
      await element.updateComplete;
      assert.isUndefined(query(element, '.textfield-input'));
      assert.isUndefined(query(element, '.autocomplete-input'));
    });

    test('shows default placeholder for other actions', async () => {
      element.currentAction = 'some-other-action';
      await element.updateComplete;
      const textfield = queryAndAssert<MdOutlinedTextField>(
        element,
        '.textfield-input'
      );
      assert.equal(textfield.placeholder, 'Parameters');
    });
  });

  suite('reviewer suggestions', () => {
    let queryAccountsStub: sinon.SinonStub;

    setup(() => {
      const restApi = getAppContext().restApiService;
      queryAccountsStub = sinon.stub(restApi, 'queryAccounts').resolves([
        {
          _account_id: 1 as AccountId,
          name: 'Test User 1',
          email: 'test1@example.com' as EmailAddress,
        },
        {
          _account_id: 2 as AccountId,
          name: 'Test User 2',
          email: 'test2@example.com' as EmailAddress,
        },
      ]);
      queryAccountsStub.resetHistory();
    });

    test('simulates typing two reviewer suggestions', async () => {
      // Open dialog
      const createButton = queryAndAssert<GrButton>(
        element,
        'gr-button[aria-label="Create Flow"]'
      );
      createButton.click();
      await element.updateComplete;
      const createModal = queryAndAssert<HTMLDialogElement>(
        element,
        '#createModal'
      );
      const grDialog = queryAndAssert<GrDialog>(createModal, 'gr-dialog');

      // Set action to add-reviewer
      const actionInput = queryAndAssert<MdOutlinedSelect>(
        grDialog,
        'md-outlined-select[label="Action"]'
      );
      actionInput.value = 'add-reviewer';
      actionInput.dispatchEvent(new Event('change'));
      await element.updateComplete;

      const autocomplete = queryAndAssert<GrAutocomplete>(
        grDialog,
        '.autocomplete-input'
      );

      // Simulate typing 't' and selecting first suggestion
      autocomplete.text = 't';
      await element.updateComplete;
      await autocomplete.updateComplete;
      autocomplete.value = 'test1@example.com';
      autocomplete.dispatchEvent(
        new CustomEvent('text-changed', {
          detail: {value: 'test1@example.com'},
        })
      );
      await element.updateComplete;

      assert.equal(element.currentParameter, 'test1@example.com');

      // Simulate typing ','
      autocomplete.text = 'test1@example.com,';
      autocomplete.dispatchEvent(new InputEvent('input'));
      await element.updateComplete;

      assert.equal(element.currentParameter, 'test1@example.com,');

      // Simulate typing 'u' and selecting second suggestion
      autocomplete.text = 'test1@example.com,u';
      autocomplete.value = 'test1@example.com,test2@example.com';
      autocomplete.dispatchEvent(
        new CustomEvent('text-changed', {
          detail: {value: 'test1@example.com,test2@example.com'},
        })
      );
      await element.updateComplete;

      assert.equal(
        element.currentParameter,
        'test1@example.com,test2@example.com'
      );
    });
  });

  suite('vote action', () => {
    setup(async () => {
      element.repoLabels = [
        {
          name: 'Code-Review',
          values: {
            '-2': 'Do not submit',
            '-1': "I would prefer that you didn't submit this",
            ' 0': 'No score',
            '+1': 'Looks good to me, but someone else must approve',
            '+2': 'Looks good to me, approved',
          },
        },
        {
          name: 'Verified',
          values: {
            '-1': 'Fails',
            ' 0': 'No score',
            '+1': 'Verified',
          },
        },
      ];
      await element.updateComplete;
    });

    test('sets default label and value when action is changed to vote', async () => {
      const actionInput = queryAndAssert<MdOutlinedSelect>(
        element,
        'md-outlined-select[label="Action"]'
      );
      actionInput.value = 'vote';
      actionInput.dispatchEvent(new Event('change'));
      await element.updateComplete;

      assert.equal(element['selectedLabelForVote'], 'Code-Review');
      assert.equal(element['selectedValueForVote'], '-2');
      assert.equal(element['currentParameter'], 'Code-Review-2');
    });

    test('updates parameter when label is changed', async () => {
      const actionInput = queryAndAssert<MdOutlinedSelect>(
        element,
        'md-outlined-select[label="Action"]'
      );
      actionInput.value = 'vote';
      actionInput.dispatchEvent(new Event('change'));
      await element.updateComplete;

      const voteParamInputs = queryAll<MdOutlinedSelect>(
        element,
        '.vote-parameter-input'
      );
      const labelSelect = voteParamInputs[0];

      labelSelect.setAttribute('value', 'Verified');
      labelSelect.value = 'Verified';
      labelSelect.dispatchEvent(new Event('input', {bubbles: true}));
      labelSelect.dispatchEvent(new Event('change', {bubbles: true}));
      await element.updateComplete;

      assert.equal(element['selectedLabelForVote'], 'Verified');
      assert.equal(element['selectedValueForVote'], '-1');
      assert.equal(element['currentParameter'], 'Verified-1');
    });

    test('updates parameter when value is changed', async () => {
      const actionInput = queryAndAssert<MdOutlinedSelect>(
        element,
        'md-outlined-select[label="Action"]'
      );
      actionInput.value = 'vote';
      actionInput.dispatchEvent(new Event('change'));
      await element.updateComplete;

      const voteParamInputs = queryAll<MdOutlinedSelect>(
        element,
        '.vote-parameter-input'
      );
      const valueSelect = voteParamInputs[1];

      // TODO: remove setting of attributes and fix reading from value
      valueSelect.setAttribute('value', '+1');
      valueSelect.value = '+1';
      valueSelect.dispatchEvent(
        new Event('input', {bubbles: true, composed: true})
      );
      valueSelect.dispatchEvent(
        new Event('change', {bubbles: true, composed: true})
      );
      await element.updateComplete;

      assert.equal(element['selectedLabelForVote'], 'Code-Review');
      assert.equal(element['selectedValueForVote'], '+1');
      assert.equal(element['currentParameter'], 'Code-Review+1');
    });

    test('updates parameter to +0 when value is 0', async () => {
      const actionInput = queryAndAssert<MdOutlinedSelect>(
        element,
        'md-outlined-select[label="Action"]'
      );
      actionInput.value = 'vote';
      actionInput.dispatchEvent(new Event('change'));
      await element.updateComplete;

      const voteParamInputs = queryAll<MdOutlinedSelect>(
        element,
        '.vote-parameter-input'
      );
      const valueSelect = voteParamInputs[1];

      // TODO: remove setting of attributes and fix reading from value
      valueSelect.setAttribute('value', '0');
      valueSelect.value = '0';
      valueSelect.dispatchEvent(
        new Event('input', {bubbles: true, composed: true})
      );
      valueSelect.dispatchEvent(
        new Event('change', {bubbles: true, composed: true})
      );
      await element.updateComplete;

      assert.equal(element['selectedLabelForVote'], 'Code-Review');
      assert.equal(element['selectedValueForVote'], '0');
      assert.equal(element['currentParameter'], 'Code-Review+0');
    });

    test('renders text input for vote when no labels are available', async () => {
      element.repoLabels = [];
      await element.updateComplete;

      const actionInput = queryAndAssert<MdOutlinedSelect>(
        element,
        'md-outlined-select[label="Action"]'
      );
      actionInput.value = 'vote';
      actionInput.dispatchEvent(new Event('change'));
      await element.updateComplete;

      assert.isNotNull(query(element, '.textfield-input'));
      assert.lengthOf(queryAll(element, '.vote-parameter-input'), 0);
    });
  });

  suite('parseStagesFromRawFlow tests', () => {
    test('parses a single condition', async () => {
      const rawFlow = 'cond 1';
      element['parseStagesFromRawFlow'](rawFlow);
      await element.updateComplete;
      assert.deepEqual(element.stages, [
        {
          condition: 'cond 1',
          action: '',
          parameterStr: '',
        },
      ]);
    });

    test('parses a single condition with action', async () => {
      const rawFlow = 'cond 1 -> act-1';
      element['parseStagesFromRawFlow'](rawFlow);
      await element.updateComplete;
      assert.deepEqual(element.stages, [
        {
          condition: 'cond 1',
          action: 'act-1',
          parameterStr: '',
        },
      ]);
    });

    test('parses a single condition with action and params', async () => {
      const rawFlow = 'cond 1 -> act-1 param1 param2';
      element['parseStagesFromRawFlow'](rawFlow);
      await element.updateComplete;
      assert.deepEqual(element.stages, [
        {
          condition: 'cond 1',
          action: 'act-1',
          parameterStr: 'param1 param2',
        },
      ]);
    });

    test('parses multiple stages', async () => {
      const rawFlow = 'cond 1 -> act-1; cond 2 -> act-2 p2; cond 3';
      element['parseStagesFromRawFlow'](rawFlow);
      await element.updateComplete;
      assert.deepEqual(element.stages, [
        {
          condition: 'cond 1',
          action: 'act-1',
          parameterStr: '',
        },
        {
          condition: 'cond 2',
          action: 'act-2',
          parameterStr: 'p2',
        },
        {
          condition: 'cond 3',
          action: '',
          parameterStr: '',
        },
      ]);
    });

    test('parses an empty string', async () => {
      const rawFlow = '';
      element['parseStagesFromRawFlow'](rawFlow);
      await element.updateComplete;
      assert.deepEqual(element.stages, []);
    });

    test('parses with extra spacing', async () => {
      const rawFlow = '  cond 1   ->  act-1  p1 ;  cond 2  ';
      element['parseStagesFromRawFlow'](rawFlow);
      await element.updateComplete;
      assert.deepEqual(element.stages, [
        {
          condition: 'cond 1',
          action: 'act-1',
          parameterStr: 'p1',
        },
        {
          condition: 'cond 2',
          action: '',
          parameterStr: '',
        },
      ]);
    });
  });

  suite('repoLabels calculation', () => {
    test('repoLabels is calculated from change.permitted_labels', async () => {
      const changeModel = testResolver(changeModelToken);
      changeModel.updateStateChange({
        ...createParsedChange(),
        project: 'test-project' as RepoName,
        permitted_labels: {
          'Code-Review': ['-1', ' 0', '+1'],
          Verified: ['-1', ' 0', '+1'],
        },
        revisions: {
          a: createRevision(1 as RevisionPatchSetNum),
        },
      });
      await element.updateComplete;

      assert.isDefined(element['repoLabels']);
      assert.lengthOf(element['repoLabels'], 2);
      assert.equal(element['repoLabels'][0].name, 'Code-Review');
      assert.deepEqual(element['repoLabels'][0].values, {
        '-1': '',
        ' 0': '',
        '+1': '',
      });
      assert.equal(element['repoLabels'][1].name, 'Verified');
      assert.deepEqual(element['repoLabels'][1].values, {
        '-1': '',
        ' 0': '',
        '+1': '',
      });
    });

    test('repoLabels is sorted by name', async () => {
      const changeModel = testResolver(changeModelToken);
      changeModel.updateStateChange({
        ...createParsedChange(),
        project: 'test-project' as RepoName,
        permitted_labels: {
          Verified: ['-1', ' 0', '+1'],
          'Code-Review': ['-1', ' 0', '+1'],
          'A-Label': [' 0'],
        },
        revisions: {
          a: createRevision(1 as RevisionPatchSetNum),
        },
      });
      await element.updateComplete;

      assert.isDefined(element['repoLabels']);
      assert.lengthOf(element['repoLabels'], 3);
      assert.equal(element['repoLabels'][0].name, 'A-Label');
      assert.equal(element['repoLabels'][1].name, 'Code-Review');
      assert.equal(element['repoLabels'][2].name, 'Verified');
    });
  });
});
