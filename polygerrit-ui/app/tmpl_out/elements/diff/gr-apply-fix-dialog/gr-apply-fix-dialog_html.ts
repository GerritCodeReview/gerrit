import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrApplyFixDialog} from '../../../../elements/diff/gr-apply-fix-dialog/gr-apply-fix-dialog';

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

export class GrApplyFixDialogCheck extends GrApplyFixDialog
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['gr-overlay'] = null!;
      useVars(el);
      el.setAttribute('id', `applyFixOverlay`);
    }
    {
      const el: HTMLElementTagNameMap['gr-dialog'] = null!;
      useVars(el);
      el.setAttribute('id', `applyFixDialog`);
      el.addEventListener('confirm', this._handleApplyFix.bind(this));
      el.confirmLabel = this._getApplyFixButtonLabel(this._isApplyFixLoading);
      el.disabled = this._disableApplyFixButton;
      el.confirmTooltip = this._computeTooltip(this.change, this._patchNum);
      el.addEventListener('cancel', this.onCancel.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
    }
    setTextContent(`${this._robotId} - ${this.getFixDescription(this._currentFix)}`);

    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['dom-repeat'] = null!;
      useVars(el);
    }
    {
      const index = 0;
      const itemsIndexAs = 0;
      useVars(index, itemsIndexAs);
      for(const item of this._currentPreviews!)
      {
        {
          const el: HTMLElementTagNameMap['div'] = null!;
          useVars(el);
          el.setAttribute('class', `file-name`);
        }
        {
          const el: HTMLElementTagNameMap['span'] = null!;
          useVars(el);
        }
        setTextContent(`${__f(item)!.filepath}`);

        {
          const el: HTMLElementTagNameMap['div'] = null!;
          useVars(el);
          el.setAttribute('class', `diffContainer`);
        }
        {
          const el: HTMLElementTagNameMap['gr-diff'] = null!;
          useVars(el);
          el.prefs = this.overridePartialPrefs(this.prefs);
          el.changeNum = this.changeNum;
          el.path = __f(item)!.filepath;
          el.diff = __f(item)!.preview;
          el.layers = this.layers;
        }
      }
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `fix-picker`);
      el.setAttribute('hidden', `${this.hasSingleFix(this._fixSuggestions)}`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
    }
    setTextContent(`Suggested fix ${this.addOneTo(this._selectedFixIdx)} of
          ${__f(this._fixSuggestions)!.length}`);

    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.setAttribute('id', `prevFix`);
      el.addEventListener('click', this._onPrevFixClick.bind(this));
      el.setAttribute('disabled', `${this._noPrevFix(this._selectedFixIdx)}`);
    }
    {
      const el: HTMLElementTagNameMap['iron-icon'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.setAttribute('id', `nextFix`);
      el.addEventListener('click', this._onNextFixClick.bind(this));
      el.setAttribute('disabled', `${this._noNextFix(this._selectedFixIdx, this._fixSuggestions)}`);
    }
    {
      const el: HTMLElementTagNameMap['iron-icon'] = null!;
      useVars(el);
    }
  }
}

