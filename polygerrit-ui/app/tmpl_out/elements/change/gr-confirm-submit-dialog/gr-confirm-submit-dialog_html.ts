import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrConfirmSubmitDialog} from '../../../../elements/change/gr-confirm-submit-dialog/gr-confirm-submit-dialog';

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

export class GrConfirmSubmitDialogCheck extends GrConfirmSubmitDialog
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['gr-dialog'] = null!;
      useVars(el);
      el.setAttribute('id', `dialog`);
      el.confirmLabel = `Continue`;
      el.confirmOnEnter = true;
      el.addEventListener('cancel', this._handleCancelTap.bind(this));
      el.addEventListener('confirm', this._handleConfirmTap.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['dom-if'] = null!;
      useVars(el);
    }
    if (this._initialised)
    {
      {
        const el: HTMLElementTagNameMap['div'] = null!;
        useVars(el);
        el.setAttribute('class', `header`);
      }
      setTextContent(`${__f(this.action)!.label}`);

      {
        const el: HTMLElementTagNameMap['div'] = null!;
        useVars(el);
        el.setAttribute('class', `main`);
      }
      {
        const el: HTMLElementTagNameMap['gr-endpoint-decorator'] = null!;
        useVars(el);
        el.name = `confirm-submit-change`;
      }
      {
        const el: HTMLElementTagNameMap['p'] = null!;
        useVars(el);
      }
      {
        const el: HTMLElementTagNameMap['strong'] = null!;
        useVars(el);
      }
      setTextContent(`${__f(this.change)!.subject}`);

      {
        const el: HTMLElementTagNameMap['dom-if'] = null!;
        useVars(el);
      }
      if (__f(this.change)!.is_private)
      {
        {
          const el: HTMLElementTagNameMap['p'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['iron-icon'] = null!;
          useVars(el);
          el.setAttribute('class', `warningBeforeSubmit`);
        }
        {
          const el: HTMLElementTagNameMap['strong'] = null!;
          useVars(el);
        }
      }
      {
        const el: HTMLElementTagNameMap['dom-if'] = null!;
        useVars(el);
      }
      if (__f(this.change)!.unresolved_comment_count)
      {
        {
          const el: HTMLElementTagNameMap['p'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['iron-icon'] = null!;
          useVars(el);
          el.setAttribute('class', `warningBeforeSubmit`);
        }
        setTextContent(`
              ${this._computeUnresolvedCommentsWarning(this.change)}
            `);

        {
          const el: HTMLElementTagNameMap['gr-thread-list'] = null!;
          useVars(el);
          el.setAttribute('id', `commentList`);
          el.threads = this._computeUnresolvedThreads(this.commentThreads);
          el.change = this.change;
          el.changeNum = __f(this.change)!._number;
          el.loggedIn = true;
          el.hideDropdown = true;
        }
      }
      {
        const el: HTMLElementTagNameMap['dom-if'] = null!;
        useVars(el);
      }
      if (this._computeHasChangeEdit(this.change))
      {
        {
          const el: HTMLElementTagNameMap['iron-icon'] = null!;
          useVars(el);
          el.setAttribute('class', `warningBeforeSubmit`);
        }
        {
          const el: HTMLElementTagNameMap['b'] = null!;
          useVars(el);
        }
      }
      {
        const el: HTMLElementTagNameMap['gr-endpoint-param'] = null!;
        useVars(el);
        el.name = `change`;
        el.value = this.change;
      }
      {
        const el: HTMLElementTagNameMap['gr-endpoint-param'] = null!;
        useVars(el);
        el.name = `action`;
        el.value = this.action;
      }
    }
  }
}

