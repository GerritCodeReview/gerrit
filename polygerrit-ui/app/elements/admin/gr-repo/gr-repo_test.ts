/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
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
  RepoAccessGroups,
  RepoAccessInfoMap,
  RepoName,
} from '../../../types/common';
import {
  ConfigParameterInfoType,
  InheritedBooleanInfoConfiguredValue,
  RepoState,
  SubmitType,
} from '../../../constants/constants';
import {
  createConfig,
  createDownloadSchemes,
} from '../../../test/test-data-generators';
import {PageErrorEvent} from '../../../types/events';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrSelect} from '../../shared/gr-select/gr-select';
import {GrTextarea} from '../../shared/gr-textarea/gr-textarea';
import {IronInputElement} from '@polymer/iron-input/iron-input';
import {fixture, html, assert} from '@open-wc/testing';

suite('gr-repo tests', () => {
  let element: GrRepo;
  let loggedInStub: sinon.SinonStub;
  let repoStub: sinon.SinonStub;

  const repoConf: ConfigInfo = {
    description: 'Access inherited by all other repositories.',
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
    element = await fixture(html`<gr-repo></gr-repo>`);
  });

  test('render', async () => {
    element.repo = REPO as RepoName;
    await element.loadRepo();
    await element.updateComplete;
    // prettier and shadowDom assert do not agree about span.title wrapping
    assert.shadowDom.equal(
      element,
      /* prettier-ignore */ /* HTML */ `
      <div class="gr-form-styles main read-only">
        <div class="info">
          <h1 class="heading-1" id="Title">test-repo</h1>
          <hr />
          <div>
            <a href="">
              <gr-button
                aria-disabled="true"
                disabled=""
                link=""
                role="button"
                tabindex="-1"
              >
                Browse
              </gr-button>
            </a>
            <a href="/q/project:test-repo">
              <gr-button
                aria-disabled="false"
                link=""
                role="button"
                tabindex="0"
              >
                View Changes
              </gr-button>
            </a>
          </div>
        </div>
        <div id="loadedContent">
          <h2 class="heading-2" id="configurations">Configurations</h2>
          <div id="form">
            <fieldset>
              <h3 class="heading-3" id="Description">Description</h3>
              <fieldset>
                <gr-textarea
                  autocomplete="on"
                  class="description monospace"
                  disabled=""
                  id="descriptionInput"
                  monospace=""
                  placeholder="<Insert repo description here>"
                  rows="4"
                >
                </gr-textarea>
              </fieldset>
              <h3 class="heading-3" id="Options">Repository Options</h3>
              <fieldset id="options">
                <section>
                  <span class="title"> State </span>
                  <span class="value">
                    <gr-select id="stateSelect">
                      <select disabled="">
                        <option value="ACTIVE">Active</option>
                        <option value="READ_ONLY">Read Only</option>
                        <option value="HIDDEN">Hidden</option>
                      </select>
                    </gr-select>
                  </span>
                </section>
                <section>
                  <span class="title"> Submit type </span>
                  <span class="value">
                    <gr-select id="submitTypeSelect">
                      <select disabled=""></select>
                    </gr-select>
                  </span>
                </section>
                <section>
                  <span class="title"> Allow content merges </span>
                  <span class="value">
                    <gr-select id="contentMergeSelect">
                      <select disabled=""></select>
                    </gr-select>
                  </span>
                </section>
                <section>
                  <span class="title">
                    Create a new change for every commit not in the target branch
                  </span>
                  <span class="value">
                    <gr-select id="newChangeSelect">
                      <select disabled=""></select>
                    </gr-select>
                  </span>
                </section>
                <section>
                  <span class="title">
                    Require Change-Id in commit message
                  </span>
                  <span class="value">
                    <gr-select id="requireChangeIdSelect">
                      <select disabled=""></select>
                    </gr-select>
                  </span>
                </section>
                <section
                  class="repositorySettings showConfig"
                  id="enableSignedPushSettings"
                >
                  <span class="title"> Enable signed push </span>
                  <span class="value">
                    <gr-select id="enableSignedPush">
                      <select disabled=""></select>
                    </gr-select>
                  </span>
                </section>
                <section
                  class="repositorySettings showConfig"
                  id="requireSignedPushSettings"
                >
                  <span class="title"> Require signed push </span>
                  <span class="value">
                    <gr-select id="requireSignedPush">
                      <select disabled=""></select>
                    </gr-select>
                  </span>
                </section>
                <section>
                  <span class="title">
                    Reject implicit merges when changes are pushed for review
                  </span>
                  <span class="value">
                    <gr-select id="rejectImplicitMergesSelect">
                      <select disabled=""></select>
                    </gr-select>
                  </span>
                </section>
                <section>
                  <span class="title">
                    Enable adding unregistered users as reviewers and CCs on changes
                  </span>
                  <span class="value">
                    <gr-select id="unRegisteredCcSelect">
                      <select disabled=""></select>
                    </gr-select>
                  </span>
                </section>
                <section>
                  <span class="title">
                    Set all new changes private by default
                  </span>
                  <span class="value">
                    <gr-select id="setAllnewChangesPrivateByDefaultSelect">
                      <select disabled=""></select>
                    </gr-select>
                  </span>
                </section>
                <section>
                  <span class="title">
                    Set new changes to "work in progress" by default
                  </span>
                  <span class="value">
                    <gr-select
                      id="setAllNewChangesWorkInProgressByDefaultSelect"
                    >
                      <select disabled=""></select>
                    </gr-select>
                  </span>
                </section>
                <section>
                  <span class="title"> Maximum Git object size limit </span>
                  <span class="value">
                    <iron-input id="maxGitObjSizeIronInput">
                      <input disabled="" id="maxGitObjSizeInput" type="text" />
                    </iron-input>
                  </span>
                </section>
                <section>
                  <span class="title">
                    Match authored date with committer date upon submit
                  </span>
                  <span class="value">
                    <gr-select id="matchAuthoredDateWithCommitterDateSelect">
                      <select disabled=""></select>
                    </gr-select>
                  </span>
                </section>
                <section>
                  <span class="title"> Reject empty commit upon submit </span>
                  <span class="value">
                    <gr-select id="rejectEmptyCommitSelect">
                      <select disabled=""></select>
                    </gr-select>
                  </span>
                </section>
              </fieldset>
              <h3 class="heading-3" id="Options">Contributor Agreements</h3>
              <fieldset id="agreements">
                <section>
                  <span class="title">
                    Require a valid contributor agreement to upload
                  </span>
                  <span class="value">
                    <gr-select id="contributorAgreementSelect">
                      <select disabled=""></select>
                    </gr-select>
                  </span>
                </section>
                <section>
                  <span class="title">
                    Require Signed-off-by in commit message
                  </span>
                  <span class="value">
                    <gr-select id="useSignedOffBySelect">
                      <select disabled=""></select>
                    </gr-select>
                  </span>
                </section>
              </fieldset>
              <gr-button
                aria-disabled="true"
                disabled=""
                role="button"
                tabindex="-1"
              >
                Save changes
              </gr-button>
            </fieldset>
            <gr-endpoint-decorator name="repo-config">
              <gr-endpoint-param name="repoName"> </gr-endpoint-param>
              <gr-endpoint-param name="readOnly"> </gr-endpoint-param>
            </gr-endpoint-decorator>
          </div>
        </div>
      </div>
    `,
      {ignoreTags: ['option']}
    );
  });

  test('render loading', async () => {
    element.repo = REPO as RepoName;
    element.loading = true;
    await element.updateComplete;
    // prettier and shadowDom assert do not agree about span.title wrapping
    assert.shadowDom.equal(
      element,
      /* prettier-ignore */ /* HTML */ `
      <div class="gr-form-styles main read-only">
        <div class="info">
          <h1 class="heading-1" id="Title">test-repo</h1>
          <hr />
          <div>
            <a href="">
              <gr-button
                aria-disabled="true"
                disabled=""
                link=""
                role="button"
                tabindex="-1"
              >
                Browse
              </gr-button>
            </a>
            <a href="/q/project:test-repo">
              <gr-button
                aria-disabled="false"
                link=""
                role="button"
                tabindex="0"
              >
                View Changes
              </gr-button>
            </a>
          </div>
        </div>
        <div id="loading">Loading...</div>
      </div>
    `,
      {ignoreTags: ['option']}
    );
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

  test('render download commands', async () => {
    element.repo = REPO as RepoName;
    await element.loadRepo();
    element.schemesObj = SCHEMES;
    await element.updateComplete;
    const content = queryAndAssert<HTMLDivElement>(element, '#downloadContent');
    assert.dom.equal(
      content,
      /* HTML */ `
        <div id="downloadContent">
          <h2 class="heading-2" id="download">Download</h2>
          <fieldset>
            <gr-download-commands id="downloadCommands"></gr-download-commands>
          </fieldset>
        </div>
      `
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
          } as RepoAccessGroups,
          config_web_links: [{name: 'gitiles', url: 'test'}],
        },
      } as RepoAccessInfoMap)
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
            } as RepoAccessGroups,
            config_web_links: [{name: 'gitiles', url: 'test'}],
          },
        } as RepoAccessInfoMap)
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
      assert.equal(element.repoConfig!.state, RepoState.ACTIVE);
      assert.equal(
        queryAndAssert<GrSelect>(element, '#stateSelect').bindValue,
        RepoState.ACTIVE
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
        state: RepoState.READ_ONLY,
        enable_reviewer_by_email: InheritedBooleanInfoConfiguredValue.TRUE,
      };

      const saveStub = stubRestApi('saveRepoConfig').callsFake(() =>
        Promise.resolve(new Response())
      );

      await element.loadRepo();

      const button = queryAll<GrButton>(element, 'gr-button')[2];
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
