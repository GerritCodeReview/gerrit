/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Constructor} from '../../utils/common-util';
import {LitElement, PropertyValues} from 'lit';
import {property, query, state} from 'lit/decorators.js';
import {GrSuggestionDiffPreview} from '../../elements/shared/gr-suggestion-diff-preview/gr-suggestion-diff-preview';
import {Comment, PatchSetNumber} from '../../types/common';
import {waitUntil} from '../../utils/async-util';
import {css} from 'lit';
import {resolve} from '../../models/dependency';
import {changeModelToken} from '../../models/change/change-model';
import {configModelToken} from '../../models/config/config-model';
import {subscribe} from '../../elements/lit/subscription-controller';

/**
 * The mixin for suggestion behavior.
 * @example
 * class YourComponent extends SuggestionMixin(
 *  LitElement)
 *
 * @lit
 * @mixinFunction
 */
export const SuggestionMixin = <T extends Constructor<LitElement>>(
  superClass: T
) => {
  /**
   * @lit
   * @mixinClass
   */
  class Mixin extends superClass {
    @query('gr-suggestion-diff-preview')
    suggestionDiffPreview?: GrSuggestionDiffPreview;

    @property({type: Object})
    comment?: Comment;

    @state() public applyingFix = false;

    @state() public previewLoaded = false;

    @state() private docsBaseUrl = '';

    @state() latestPatchNum?: PatchSetNumber;

    private readonly getConfigModel = resolve(this, configModelToken);

    private readonly getChangeModel = resolve(this, changeModelToken);

    static get styles() {
      return [
        css`
          .header {
            background-color: var(--background-color-primary);
            border: 1px solid var(--border-color);
            padding: var(--spacing-xs) var(--spacing-xl);
            display: flex;
            align-items: center;
            border-top-left-radius: var(--border-radius);
            border-top-right-radius: var(--border-radius);
          }
          .header .title {
            flex: 1;
          }
          .copyButton {
            margin-right: var(--spacing-l);
          }
        `,
      ];
    }

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    constructor(...args: any[]) {
      super(...args);
      subscribe(
        this,
        () => this.getConfigModel().docsBaseUrl$,
        docsBaseUrl => (this.docsBaseUrl = docsBaseUrl)
      );
      subscribe(
        this,
        () => this.getChangeModel().latestPatchNum$,
        x => (this.latestPatchNum = x)
      );
    }

    override updated(changedProperties: PropertyValues) {
      super.updated(changedProperties);
      if (changedProperties.has('comment') && this.comment?.fix_suggestions) {
        this.waitForPreviewToLoad();
      }
    }

    public isApplyEditDisabled() {
      if (this.comment?.patch_set === undefined) return true;
      return !this.previewLoaded;
    }

    public computeApplyEditTooltip() {
      if (this.comment?.patch_set === undefined) return '';
      if (!this.previewLoaded) return 'Fix is still loading ...';
      return '';
    }

    public async waitForPreviewToLoad() {
      this.previewLoaded = false;
      try {
        await waitUntil(() => !!this.suggestionDiffPreview?.preview);
        this.previewLoaded = true;
      } catch (error) {
        console.error('Error waiting for preview to load:', error);
      }
    }
  }

  return Mixin as T & Constructor<SuggestionMixinInterface>;
};

export interface SuggestionMixinInterface {
  comment?: Comment;
  suggestionDiffPreview?: GrSuggestionDiffPreview;
  applyingFix: boolean;
  previewLoaded: boolean;
  latestPatchNum?: PatchSetNumber;
  isApplyEditDisabled(): boolean;
  computeApplyEditTooltip(): string;
  waitForPreviewToLoad(): Promise<void>;
}
