import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrConfirmRebaseDialog} from '../../../../elements/change/gr-confirm-rebase-dialog/gr-confirm-rebase-dialog';

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

export class GrConfirmRebaseDialogCheck extends GrConfirmRebaseDialog
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['gr-dialog'] = null!;
      useVars(el);
      el.setAttribute('id', `confirmDialog`);
      el.confirmLabel = `Rebase`;
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
      el.setAttribute('id', `rebaseOnParent`);
      el.setAttribute('class', `rebaseOption`);
      el.setAttribute('hidden', `${!this._displayParentOption(this.rebaseOnCurrent, this.hasParent)}`);
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
      el.setAttribute('id', `rebaseOnParentInput`);
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
      el.setAttribute('id', `rebaseOnParentLabel`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('id', `parentUpToDateMsg`);
      el.setAttribute('class', `message`);
      el.setAttribute('hidden', `${!this._displayParentUpToDateMsg(this.rebaseOnCurrent, this.hasParent)}`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('id', `rebaseOnTip`);
      el.setAttribute('class', `rebaseOption`);
      el.setAttribute('hidden', `${!this._displayTipOption(this.rebaseOnCurrent, this.hasParent)}`);
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
      el.setAttribute('id', `rebaseOnTipInput`);
      el.setAttribute('disabled', `${!this._displayTipOption(this.rebaseOnCurrent, this.hasParent)}`);
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
      el.setAttribute('id', `rebaseOnTipLabel`);
    }
    setTextContent(`
          Rebase on top of the ${this.branch} branch`);

    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${!this.hasParent}`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('id', `tipUpToDateMsg`);
      el.setAttribute('class', `message`);
      el.setAttribute('hidden', `${this._displayTipOption(this.rebaseOnCurrent, this.hasParent)}`);
    }
    setTextContent(`
        Change is up to date with the target branch already (${this.branch})
      `);

    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('id', `rebaseOnOther`);
      el.setAttribute('class', `rebaseOption`);
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
      el.setAttribute('id', `rebaseOnOtherInput`);
      el.addEventListener('click', this._handleRebaseOnOther.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
      el.setAttribute('id', `rebaseOnOtherLabel`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('hidden', `${!this.hasParent}`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `parentRevisionContainer`);
    }
    {
      const el: HTMLElementTagNameMap['gr-autocomplete'] = null!;
      useVars(el);
      el.setAttribute('id', `parentInput`);
      el.query = this._query;
      el.noDebounce = true;
      el.text = this._text;
      this._text = el.text;
      el.addEventListener('click', this._handleEnterChangeNumberClick.bind(this));
      el.allowNonSuggestedValues = true;
      el.placeholder = `Change number, ref, or commit hash`;
    }
  }
}

