import * as ts from 'typescript';
import {LegacyLifecycleMethodsArray, LegacyPolymerComponent} from './polymerComponentParser';
import {LifecycleMethodsBuilder} from './lifecycleMethodsBuilder';
import {ClassBasedPolymerElement, PolymerElementBuilder} from './polymerElementBuilder';

export class PolymerClassFromLegacyComponentGenerator {
  public static generatePolymerV2ClassFromLegacyComponent(component: LegacyPolymerComponent): ClassBasedPolymerElement {
    const legacySettings = component.componentSettings;
    const reservedDeclarations = legacySettings.reservedDeclarations;

    if(!reservedDeclarations.is) {
      throw new Error("Legacy component doesn't have 'is' property");
    }
    const className = this.generateClassNameFromTagName(reservedDeclarations.is);
    const updater = new PolymerElementBuilder(component, className);
    updater.addIsAccessor(reservedDeclarations.is);

    if(reservedDeclarations.properties) {
      updater.addPolymerPropertiesAccessor(reservedDeclarations.properties);
    }

    updater.addMixin("Polymer.Element");
    updater.addMixin("Polymer.LegacyElementMixin");
    updater.addMixin("Polymer.GestureEventListeners");

    if(reservedDeclarations._legacyUndefinedCheck) {
      updater.addMixin("Polymer.LegacyDataMixin");
    }

    if(reservedDeclarations.behaviors) {
      updater.addMixin("Polymer.mixinBehaviors", [reservedDeclarations.behaviors]);
    }

    if(reservedDeclarations.observers) {
      updater.addPolymerPropertiesObservers(reservedDeclarations.observers);
    }


    const lifecycleBuilder = new LifecycleMethodsBuilder();
    if (reservedDeclarations.listeners) {
      lifecycleBuilder.addListeners(reservedDeclarations.listeners, legacySettings.ordinaryMethods);
    }

    if (reservedDeclarations.hostAttributes) {
      lifecycleBuilder.addHostAttributes(reservedDeclarations.hostAttributes);
    }

    for(const name of LegacyLifecycleMethodsArray) {
      const existingMethod = legacySettings.lifecycleMethods.get(name);
      if(existingMethod) {
        lifecycleBuilder.addLegacyLifecycleMethod(name, existingMethod)
      }
    }

    const newLifecycleMethods = lifecycleBuilder.buildNewMethods();
    updater.addLifecycleMethods(newLifecycleMethods);

    updater.addOrdinaryMethods(legacySettings.ordinaryMethods);
    updater.addOrdinaryGetAccessors(legacySettings.ordinaryGetAccessors);
    updater.addOrdinaryShorthandProperties(legacySettings.ordinaryShorthandProperties);
    updater.addOrdinaryPropertyAssignments(legacySettings.ordinaryPropertyAssignments);

    return updater.build();
  }

  private static generateClassNameFromTagName(tagName: string) {
    let result = "";
    let nextUppercase = true;
    for(const ch of tagName) {
      if (ch === '-') {
        nextUppercase = true;
        continue;
      }
      result += nextUppercase ? ch.toUpperCase() : ch;
      nextUppercase = false;
    }
    return result;
  }

}