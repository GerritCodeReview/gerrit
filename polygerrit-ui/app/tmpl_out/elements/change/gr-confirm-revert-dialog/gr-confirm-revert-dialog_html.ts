import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrConfirmRevertDialog} from '../../../../elements/change/gr-confirm-revert-dialog/gr-confirm-revert-dialog';

export interface PolymerDomRepeatEventModel<T> {
  /**
   * The item corresponding to the element in the dom-repeat.
   */
  item: T;

  /**
   * The index of the element in the dom-repeat.
   */
  index: number;
  get: (name: string) => T;
  set: (name: string, val: T) => void;
}

declare function wrapInPolymerDomRepeatEvent<T, U>(event: T, item: U): T & {model: PolymerDomRepeatEventModel<U>};
declare function setTextContent(content: unknown): void;
declare function useVars(...args: unknown[]): void;

type UnionToIntersection<T> = (
  T extends any ? (v: T) => void : never
  ) extends (v: infer K) => void
  ? K
  : never;

type AddNonDefinedProperties<T, P> = {
  [K in keyof P]: K extends keyof T ? T[K] : undefined;
};

type FlatUnion<T, TIntersect> = T extends any
  ? AddNonDefinedProperties<T, TIntersect>
  : never;

type AllUndefined<T> = {
  [P in keyof T]: undefined;
}

type UnionToAllUndefined<T> = T extends any ? AllUndefined<T> : any

type Flat<T> = FlatUnion<T, UnionToIntersection<UnionToAllUndefined<T>>>;

declare function __f<T>(obj: T): Flat<NonNullable<T>>;

declare function pc<T>(obj: T): PolymerDeepPropertyChange<T, T>;

declare function convert<T, U extends T>(obj: T): U;

export class GrConfirmRevertDialogCheck extends GrConfirmRevertDialog
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['gr-dialog'] = null!;
      useVars(el);
      el.confirmLabel = `Revert`;
      el.addEventListener('confirm', this._handleConfirmTap.bind(this));
      el.addEventListener('cancel', this._handleCancelTap.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `header`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `main`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `error`);
      el.setAttribute('hidden', `${!this._showErrorMessage}`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['dom-if'] = null!;
      useVars(el);
    }
    if (this._showRevertSubmission)
    {
      {
        const el: HTMLElementTagNameMap['div'] = null!;
        useVars(el);
        el.setAttribute('class', `revertSubmissionLayout`);
      }
      {
        const el: HTMLElementTagNameMap['input'] = null!;
        useVars(el);
        el.setAttribute('id', `revertSingleChange`);
        el.addEventListener('change', this._handleRevertSingleChangeClicked.bind(this));
        el.checked = this._computeIfSingleRevert(this._revertType);
      }
      {
        const el: HTMLElementTagNameMap['label'] = null!;
        useVars(el);
        el.setAttribute('class', `label revertSingleChange`);
      }
      {
        const el: HTMLElementTagNameMap['div'] = null!;
        useVars(el);
        el.setAttribute('class', `revertSubmissionLayout`);
      }
      {
        const el: HTMLElementTagNameMap['input'] = null!;
        useVars(el);
        el.setAttribute('id', `revertSubmission`);
        el.addEventListener('change', this._handleRevertSubmissionClicked.bind(this));
        el.checked = this._computeIfRevertSubmission(this._revertType);
      }
      {
        const el: HTMLElementTagNameMap['label'] = null!;
        useVars(el);
        el.setAttribute('class', `label revertSubmission`);
      }
      setTextContent(`
            Revert entire submission (${this._changesCount} Changes)
          `);

    }
    {
      const el: HTMLElementTagNameMap['gr-endpoint-decorator'] = null!;
      useVars(el);
      el.name = `confirm-revert-change`;
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['iron-autogrow-textarea'] = null!;
      useVars(el);
      el.setAttribute('id', `messageInput`);
      el.setAttribute('class', `message`);
      el.bindValue = this._message;
      this._message = el.bindValue;
    }
  }
}

