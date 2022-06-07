/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-repo';
import {GrRepo} from './gr-repo';
import {mockPromise} from '../../../test/test-utils';
import {
  addListenerForTest,
  queryAll,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {
  createInheritedBoolean,
  createServerInfo,
} from '../../../test/test-data-generators';
import {
  ConfigInfo,
  GitRef,
  GroupId,
  GroupName,
  InheritedBooleanInfo,
  MaxObjectSizeLimitInfo,
  PluginParameterToConfigParameterInfoMap,
  ProjectAccessGroups,
  ProjectAccessInfoMap,
  RepoName,
} from '../../../types/common';
import {
  ConfigParameterInfoType,
  InheritedBooleanInfoConfiguredValue,
  ProjectState,
  SubmitType,
} from '../../../constants/constants';
import {
  createConfig,
  createDownloadSchemes,
} from '../../../test/test-data-generators';
import {PageErrorEvent} from '../../../types/events.js';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrSelect} from '../../shared/gr-select/gr-select';
import {GrTextarea} from '../../shared/gr-textarea/gr-textarea';
import {IronInputElement} from '@polymer/iron-input/iron-input';

const basicFixture = fixtureFromElement('gr-repo');

suite('gr-repo tests', () => {
  let element: GrRepo;
  let loggedInStub: sinon.SinonStub;
  let repoStub: sinon.SinonStub;

  const repoConf: ConfigInfo = {
    description: 'Access inherited by all other projects.',
    use_contributor_agreements: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    use_content_merge: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    use_signed_off_by: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    create_new_change_for_all_not_in_target: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    require_change_id: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    enable_signed_push: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    require_signed_push: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    reject_implicit_merges: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    match_author_to_committer_date: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    reject_empty_commit: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    enable_reviewer_by_email: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    private_by_default: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    work_in_progress_by_default: {
      value: false,
      configured_value: InheritedBooleanInfoConfiguredValue.FALSE,
    },
    max_object_size_limit: {},
    commentlinks: {},
    submit_type: SubmitType.MERGE_IF_NECESSARY,
    default_submit_type: {
      value: SubmitType.MERGE_IF_NECESSARY,
      configured_value: SubmitType.INHERIT,
      inherited_value: SubmitType.MERGE_IF_NECESSARY,
    },
  };

  const REPO = 'test-repo';
  const SCHEMES = {
    ...createDownloadSchemes(),
    http: {
      url: 'test',
      is_auth_required: false,
      is_auth_supported: false,
      commands: 'test',
      clone_commands: {clone: 'test'},
    },
    repo: {
      url: 'test',
      is_auth_required: false,
      is_auth_supported: false,
      commands: 'test',
      clone_commands: {clone: 'test'},
    },
    ssh: {
      url: 'test',
      is_auth_required: false,
      is_auth_supported: false,
      commands: 'test',
      clone_commands: {clone: 'test'},
    },
  };

  function getFormFields() {
    const selects = Array.from(queryAll(element, 'select'));
    const textareas = Array.from(queryAll(element, 'iron-autogrow-textarea'));
    const inputs = Array.from(queryAll(element, 'input'));
    return inputs.concat(textareas).concat(selects);
  }

  setup(async () => {
    loggedInStub = stubRestApi('getLoggedIn').returns(Promise.resolve(false));
    stubRestApi('getConfig').returns(Promise.resolve(createServerInfo()));
    repoStub = stubRestApi('getProjectConfig').returns(
      Promise.resolve(repoConf)
    );
    element = basicFixture.instantiate();
    await element.updateComplete;
  });

  test('_computePluginData', async () => {
    element.repoConfig = {
      ...createConfig(),
      plugin_config: {},
    };
    await element.updateComplete;
    assert.deepEqual(element.computePluginData(), []);

    element.repoConfig.plugin_config = {
      'test-plugin': {
        test: {display_name: 'test plugin', type: 'STRING'},
      } as PluginParameterToConfigParameterInfoMap,
    };
    await element.updateComplete;
    assert.deepEqual(element.computePluginData(), [
      {
        name: 'test-plugin',
        config: {
          test: {
            display_name: 'test plugin',
            type: 'STRING' as ConfigParameterInfoType,
          },
        },
      },
    ]);
  });

  test('handlePluginConfigChanged', async () => {
    const requestUpdateStub = sinon.stub(element, 'requestUpdate');
    element.repoConfig = {
      ...createConfig(),
      plugin_config: {},
    };
    element.handlePluginConfigChanged({
      detail: {
        name: 'test',
        config: {
          test: {display_name: 'test plugin', type: 'STRING'},
        } as PluginParameterToConfigParameterInfoMap,
      },
    });
    await element.updateComplete;

    assert.deepEqual(element.repoConfig.plugin_config!.test, {
      test: {display_name: 'test plugin', type: 'STRING'},
    } as PluginParameterToConfigParameterInfoMap);
    assert.isTrue(requestUpdateStub.called);
  });

  test('loading displays before repo config is loaded', () => {
    assert.isTrue(
      queryAndAssert<HTMLDivElement>(element, '#loading').classList.contains(
        'loading'
      )
    );
    assert.isFalse(
      getComputedStyle(queryAndAssert<HTMLDivElement>(element, '#loading'))
        .display === 'none'
    );
    assert.isTrue(
      queryAndAssert<HTMLDivElement>(
        element,
        '#loadedContent'
      ).classList.contains('loading')
    );
    assert.isTrue(
      getComputedStyle(
        queryAndAssert<HTMLDivElement>(element, '#loadedContent')
      ).display === 'none'
    );
  });

  test('download commands visibility', async () => {
    element.loading = false;
    await element.updateComplete;
    assert.isTrue(
      queryAndAssert<HTMLDivElement>(
        element,
        '#downloadContent'
      ).classList.contains('hide')
    );
    assert.isTrue(
      getComputedStyle(
        queryAndAssert<HTMLDivElement>(element, '#downloadContent')
      ).display === 'none'
    );
    element.schemesObj = SCHEMES;
    await element.updateComplete;
    assert.isFalse(
      queryAndAssert<HTMLDivElement>(
        element,
        '#downloadContent'
      ).classList.contains('hide')
    );
    assert.isFalse(
      getComputedStyle(
        queryAndAssert<HTMLDivElement>(element, '#downloadContent')
      ).display === 'none'
    );
  });

  test('form defaults to read only', () => {
    assert.isTrue(element.readOnly);
  });

  test('form defaults to read only when not logged in', async () => {
    element.repo = REPO as RepoName;
    await element.loadRepo();
    assert.isTrue(element.readOnly);
  });

  test('form defaults to read only when logged in and not admin', async () => {
    element.repo = REPO as RepoName;

    stubRestApi('getRepoAccess').callsFake(() =>
      Promise.resolve({
        'test-repo': {
          revision: 'xxxx',
          local: {
            'refs/*': {
              permissions: {
                owner: {rules: {xxx: {action: 'ALLOW', force: false}}},
              },
            },
          },
          owner_of: ['refs/*'] as GitRef[],
          groups: {
            xxxx: {
              id: 'xxxx' as GroupId,
              url: 'test',
              name: 'test' as GroupName,
            },
          } as ProjectAccessGroups,
          config_web_links: [{name: 'gitiles', url: 'test'}],
        },
      } as ProjectAccessInfoMap)
    );
    await element.loadRepo();
    assert.isTrue(element.readOnly);
  });

  test('all form elements are disabled when not admin', async () => {
    element.repo = REPO as RepoName;
    await element.loadRepo();
    await element.updateComplete;
    const formFields = getFormFields();
    for (const field of formFields) {
      assert.isTrue(field.hasAttribute('disabled'));
    }
  });

  test('formatBooleanSelect', () => {
    let item: InheritedBooleanInfo = {
      ...createInheritedBoolean(true),
      inherited_value: true,
    };
    assert.deepEqual(element.formatBooleanSelect(item), [
      {
        label: 'Inherit (true)',
        value: 'INHERIT',
      },
      {
        label: 'True',
        value: 'TRUE',
      },
      {
        label: 'False',
        value: 'FALSE',
      },
    ]);

    item = {...createInheritedBoolean(false), inherited_value: false};
    assert.deepEqual(element.formatBooleanSelect(item), [
      {
        label: 'Inherit (false)',
        value: 'INHERIT',
      },
      {
        label: 'True',
        value: 'TRUE',
      },
      {
        label: 'False',
        value: 'FALSE',
      },
    ]);

    // For items without inherited values
    item = createInheritedBoolean(false);
    assert.deepEqual(element.formatBooleanSelect(item), [
      {
        label: 'Inherit',
        value: 'INHERIT',
      },
      {
        label: 'True',
        value: 'TRUE',
      },
      {
        label: 'False',
        value: 'FALSE',
      },
    ]);
  });

  test('fires page-error', async () => {
    repoStub.restore();

    element.repo = 'test' as RepoName;

    const pageErrorFired = mockPromise();
    const response = {...new Response(), status: 404};
    stubRestApi('getProjectConfig').callsFake((_, errFn) => {
      if (errFn !== undefined) {
        errFn(response);
      }
      return Promise.resolve(undefined);
    });
    addListenerForTest(document, 'page-error', e => {
      assert.deepEqual((e as PageErrorEvent).detail.response, response);
      pageErrorFired.resolve();
    });

    element.loadRepo();
    await pageErrorFired;
  });

  suite('admin', () => {
    setup(() => {
      element.repo = REPO as RepoName;
      loggedInStub.returns(Promise.resolve(true));
      stubRestApi('getRepoAccess').callsFake(() =>
        Promise.resolve({
          'test-repo': {
            revision: 'xxxx',
            local: {
              'refs/*': {
                permissions: {
                  owner: {rules: {xxx: {action: 'ALLOW', force: false}}},
                },
              },
            },
            is_owner: true,
            owner_of: ['refs/*'] as GitRef[],
            groups: {
              xxxx: {
                id: 'xxxx' as GroupId,
                url: 'test',
                name: 'test' as GroupName,
              },
            } as ProjectAccessGroups,
            config_web_links: [{name: 'gitiles', url: 'test'}],
          },
        } as ProjectAccessInfoMap)
      );
    });

    test('all form elements are enabled', async () => {
      await element.loadRepo();
      await element.updateComplete;
      const formFields = getFormFields();
      for (const field of formFields) {
        assert.isFalse(field.hasAttribute('disabled'));
      }
      assert.isFalse(element.loading);
    });

    test('state gets set correctly', async () => {
      await element.loadRepo();
      assert.equal(element.repoConfig!.state, ProjectState.ACTIVE);
      assert.equal(
        queryAndAssert<GrSelect>(element, '#stateSelect').bindValue,
        ProjectState.ACTIVE
      );
    });

    test('inherited submit type value is calculated correctly', async () => {
      await element.loadRepo();
      const sel = queryAndAssert<GrSelect>(element, '#submitTypeSelect');
      assert.equal(sel.bindValue, 'INHERIT');
      assert.equal(
        sel.nativeSelect.options[0].text,
        'Inherit (Merge if necessary)'
      );
    });

    test('fields update and save correctly', async () => {
      const configInputObj = {
        description: 'new description',
        use_contributor_agreements: InheritedBooleanInfoConfiguredValue.TRUE,
        use_content_merge: InheritedBooleanInfoConfiguredValue.TRUE,
        use_signed_off_by: InheritedBooleanInfoConfiguredValue.TRUE,
        create_new_change_for_all_not_in_target:
          InheritedBooleanInfoConfiguredValue.TRUE,
        require_change_id: InheritedBooleanInfoConfiguredValue.TRUE,
        enable_signed_push: InheritedBooleanInfoConfiguredValue.TRUE,
        require_signed_push: InheritedBooleanInfoConfiguredValue.TRUE,
        reject_implicit_merges: InheritedBooleanInfoConfiguredValue.TRUE,
        private_by_default: InheritedBooleanInfoConfiguredValue.TRUE,
        work_in_progress_by_default: InheritedBooleanInfoConfiguredValue.TRUE,
        match_author_to_committer_date:
          InheritedBooleanInfoConfiguredValue.TRUE,
        reject_empty_commit: InheritedBooleanInfoConfiguredValue.TRUE,
        max_object_size_limit: '10' as MaxObjectSizeLimitInfo,
        submit_type: SubmitType.FAST_FORWARD_ONLY,
        state: ProjectState.READ_ONLY,
        enable_reviewer_by_email: InheritedBooleanInfoConfiguredValue.TRUE,
      };

      const saveStub = stubRestApi('saveRepoConfig').callsFake(() =>
        Promise.resolve(new Response())
      );

      const button = queryAll<GrButton>(element, 'gr-button')[2];

      await element.loadRepo();
      assert.isTrue(button.hasAttribute('disabled'));
      assert.isFalse(
        queryAndAssert<HTMLHeadingElement>(
          element,
          '#Title'
        ).classList.contains('edited')
      );
      queryAndAssert<GrTextarea>(element, '#descriptionInput').text =
        configInputObj.description;
      queryAndAssert<GrSelect>(element, '#stateSelect').bindValue =
        configInputObj.state;
      queryAndAssert<GrSelect>(element, '#submitTypeSelect').bindValue =
        configInputObj.submit_type;
      queryAndAssert<GrSelect>(element, '#contentMergeSelect').bindValue =
        configInputObj.use_content_merge;
      queryAndAssert<GrSelect>(element, '#newChangeSelect').bindValue =
        configInputObj.create_new_change_for_all_not_in_target;
      queryAndAssert<GrSelect>(element, '#requireChangeIdSelect').bindValue =
        configInputObj.require_change_id;
      queryAndAssert<GrSelect>(element, '#enableSignedPush').bindValue =
        configInputObj.enable_signed_push;
      queryAndAssert<GrSelect>(element, '#requireSignedPush').bindValue =
        configInputObj.require_signed_push;
      queryAndAssert<GrSelect>(
        element,
        '#rejectImplicitMergesSelect'
      ).bindValue = configInputObj.reject_implicit_merges;
      queryAndAssert<GrSelect>(
        element,
        '#setAllnewChangesPrivateByDefaultSelect'
      ).bindValue = configInputObj.private_by_default;
      queryAndAssert<GrSelect>(
        element,
        '#setAllNewChangesWorkInProgressByDefaultSelect'
      ).bindValue = configInputObj.work_in_progress_by_default;
      queryAndAssert<GrSelect>(
        element,
        '#matchAuthoredDateWithCommitterDateSelect'
      ).bindValue = configInputObj.match_author_to_committer_date;
      queryAndAssert<IronInputElement>(
        element,
        '#maxGitObjSizeIronInput'
      ).bindValue = String(configInputObj.max_object_size_limit);
      queryAndAssert<GrSelect>(
        element,
        '#contributorAgreementSelect'
      ).bindValue = configInputObj.use_contributor_agreements;
      queryAndAssert<GrSelect>(element, '#useSignedOffBySelect').bindValue =
        configInputObj.use_signed_off_by;
      queryAndAssert<GrSelect>(element, '#rejectEmptyCommitSelect').bindValue =
        configInputObj.reject_empty_commit;
      queryAndAssert<GrSelect>(element, '#unRegisteredCcSelect').bindValue =
        configInputObj.enable_reviewer_by_email;

      await element.updateComplete;

      assert.isFalse(button.hasAttribute('disabled'));
      assert.isTrue(
        queryAndAssert<HTMLHeadingElement>(
          element,
          '#configurations'
        ).classList.contains('edited')
      );

      const formattedObj = element.formatRepoConfigForSave(element.repoConfig);
      assert.deepEqual(formattedObj, configInputObj);

      await element.handleSaveRepoConfig();
      assert.isTrue(button.hasAttribute('disabled'));
      assert.isFalse(
        queryAndAssert<HTMLHeadingElement>(
          element,
          '#Title'
        ).classList.contains('edited')
      );
      assert.isTrue(
        saveStub.lastCall.calledWithExactly(REPO as RepoName, configInputObj)
      );
    });
  });
});
