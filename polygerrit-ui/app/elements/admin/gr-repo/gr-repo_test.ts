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
  PluginNameToPluginParametersMap,
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
import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import {
  createConfig,
  createDownloadSchemes,
} from '../../../test/test-data-generators';
import {PageErrorEvent} from '../../../types/events.js';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrSelect} from '../../shared/gr-select/gr-select';
import {IronAutogrowTextareaElement} from '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
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

  setup(() => {
    loggedInStub = stubRestApi('getLoggedIn').returns(Promise.resolve(false));
    stubRestApi('getConfig').returns(Promise.resolve(createServerInfo()));
    repoStub = stubRestApi('getProjectConfig').returns(
      Promise.resolve(repoConf)
    );
    element = basicFixture.instantiate();
  });

  test('_computePluginData', () => {
    assert.deepEqual(element._computePluginData(), []);
    assert.deepEqual(
      element._computePluginData(
        {} as unknown as PolymerDeepPropertyChange<
          PluginNameToPluginParametersMap,
          PluginNameToPluginParametersMap
        >
      ),
      []
    );
    assert.deepEqual(
      element._computePluginData({base: {}} as PolymerDeepPropertyChange<
        PluginNameToPluginParametersMap,
        PluginNameToPluginParametersMap
      >),
      []
    );
    assert.deepEqual(
      element._computePluginData({
        base: {
          'test-plugin': {test: {display_name: 'test plugin', type: 'STRING'}},
        } as PluginNameToPluginParametersMap,
      } as PolymerDeepPropertyChange<PluginNameToPluginParametersMap, PluginNameToPluginParametersMap>),
      [
        {
          name: 'test-plugin',
          config: {
            test: {
              display_name: 'test plugin',
              type: 'STRING' as ConfigParameterInfoType,
            },
          },
        },
      ]
    );
  });

  test('_handlePluginConfigChanged', async () => {
    const notifyStub = sinon.stub(element, 'notifyPath');
    element._repoConfig = {
      ...createConfig(),
      plugin_config: {},
    };
    element._handlePluginConfigChanged({
      detail: {
        name: 'test',
        config: {
          test: {display_name: 'test plugin', type: 'STRING'},
        } as PluginParameterToConfigParameterInfoMap,
        notifyPath: 'path',
      },
    });
    await flush();

    assert.deepEqual(element._repoConfig!.plugin_config!.test, {
      test: {display_name: 'test plugin', type: 'STRING'},
    } as PluginParameterToConfigParameterInfoMap);
    assert.equal(notifyStub.lastCall.args[0], '_repoConfig.plugin_config.path');
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
    element._loading = false;
    await flush();
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
    element._schemesObj = SCHEMES;
    await flush();
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
    assert.isTrue(element._readOnly);
  });

  test('form defaults to read only when not logged in', async () => {
    element.repo = REPO as RepoName;
    await element._loadRepo();
    assert.isTrue(element._readOnly);
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
    await element._loadRepo();
    assert.isTrue(element._readOnly);
  });

  test('all form elements are disabled when not admin', async () => {
    element.repo = REPO as RepoName;
    await element._loadRepo();
    flush();
    const formFields = getFormFields();
    for (const field of formFields) {
      assert.isTrue(field.hasAttribute('disabled'));
    }
  });

  test('_formatBooleanSelect', () => {
    let item: InheritedBooleanInfo = {
      ...createInheritedBoolean(true),
      inherited_value: true,
    };
    assert.deepEqual(element._formatBooleanSelect(item), [
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
    assert.deepEqual(element._formatBooleanSelect(item), [
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
    assert.deepEqual(element._formatBooleanSelect(item), [
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

    element._loadRepo();
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
      await element._loadRepo();
      await flush();
      const formFields = getFormFields();
      for (const field of formFields) {
        assert.isFalse(field.hasAttribute('disabled'));
      }
      assert.isFalse(element._loading);
    });

    test('state gets set correctly', async () => {
      await element._loadRepo();
      assert.equal(element._repoConfig!.state, ProjectState.ACTIVE);
      assert.equal(
        queryAndAssert<GrSelect>(element, '#stateSelect').bindValue,
        ProjectState.ACTIVE
      );
    });

    test('inherited submit type value is calculated correctly', async () => {
      await element._loadRepo();
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

      await element._loadRepo();
      assert.isTrue(button.hasAttribute('disabled'));
      assert.isFalse(
        queryAndAssert<HTMLHeadingElement>(
          element,
          '#Title'
        ).classList.contains('edited')
      );
      queryAndAssert<IronAutogrowTextareaElement>(
        element,
        '#descriptionInput'
      ).bindValue = configInputObj.description;
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

      assert.isFalse(button.hasAttribute('disabled'));
      assert.isTrue(
        queryAndAssert<HTMLHeadingElement>(
          element,
          '#configurations'
        ).classList.contains('edited')
      );

      const formattedObj = element._formatRepoConfigForSave(
        element._repoConfig
      );
      assert.deepEqual(formattedObj, configInputObj);

      await element._handleSaveRepoConfig();
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
