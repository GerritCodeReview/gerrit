import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrIdentities} from '../../../../elements/settings/gr-identities/gr-identities';

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

export class GrIdentitiesCheck extends GrIdentities
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `gr-form-styles`);
    }
    {
      const el: HTMLElementTagNameMap['fieldset'] = null!;
      useVars(el);
      el.setAttribute('class', `space`);
    }
    {
      const el: HTMLElementTagNameMap['table'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['thead'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['tr'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `statusHeader`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `emailAddressHeader`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `identityHeader`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `deleteHeader`);
    }
    {
      const el: HTMLElementTagNameMap['tbody'] = null!;
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
      for(const item of this._identities!.filter(this.filterIdentities.bind(this)))
      {
        {
          const el: HTMLElementTagNameMap['tr'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `statusColumn`);
        }
        setTextContent(`${this._computeIsTrusted(__f(item)!.trusted)}`);

        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `emailAddressColumn`);
        }
        setTextContent(`${__f(item)!.email_address}`);

        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `identityColumn`);
        }
        setTextContent(`
                ${this._computeIdentity(__f(item)!.identity)}
              `);

        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `deleteColumn`);
        }
        {
          const el: HTMLElementTagNameMap['gr-button'] = null!;
          useVars(el);
          el.setAttribute('class', `deleteButton ${this._computeHideDeleteClass(__f(item)!.can_delete)}`);
          el.addEventListener('click', e => this._handleDeleteItem.bind(this, wrapInPolymerDomRepeatEvent(e, item))());
        }
      }
    }
    {
      const el: HTMLElementTagNameMap['dom-if'] = null!;
      useVars(el);
    }
    if (this._showLinkAnotherIdentity)
    {
      {
        const el: HTMLElementTagNameMap['fieldset'] = null!;
        useVars(el);
      }
      {
        const el: HTMLElementTagNameMap['a'] = null!;
        useVars(el);
        el.setAttribute('href', `${this._computeLinkAnotherIdentity()}`);
      }
      {
        const el: HTMLElementTagNameMap['gr-button'] = null!;
        useVars(el);
        el.setAttribute('id', `linkAnotherIdentity`);
        el.link = true;
      }
    }
    {
      const el: HTMLElementTagNameMap['gr-overlay'] = null!;
      useVars(el);
      el.setAttribute('id', `overlay`);
    }
    {
      const el: HTMLElementTagNameMap['gr-confirm-delete-item-dialog'] = null!;
      useVars(el);
      el.setAttribute('class', `confirmDialog`);
      el.addEventListener('confirm', this._handleDeleteItemConfirm.bind(this));
      el.addEventListener('cancel', this._handleConfirmDialogCancel.bind(this));
      el.item = this._idName;
      el.itemTypeName = `ID`;
    }
  }
}

