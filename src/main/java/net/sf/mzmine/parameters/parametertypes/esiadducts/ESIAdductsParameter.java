/*
 * Copyright 2006-2015 The MZmine 2 Development Team
 *
 * This file is part of MZmine 2.
 *
 * MZmine 2 is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine 2; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */

/*
 * Code created was by or on behalf of Syngenta and is released under the open source license in use
 * for the pre-existing code or project. Syngenta does not assert ownership or copyright any over
 * pre-existing work.
 */

package net.sf.mzmine.parameters.parametertypes.esiadducts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.datastructure.identities.AdductType;
import net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.datastructure.identities.CombinedAdductType;
import net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.datastructure.identities.IonModificationType;
import net.sf.mzmine.parameters.UserParameter;
import net.sf.mzmine.parameters.parametertypes.MultiChoiceParameter;

/**
 * Adducts parameter.
 *
 * @author $Author$
 * @version $Revision$
 */
public class ESIAdductsParameter implements UserParameter<AdductType[][], ESIAdductsComponent> {

  // Logger.
  private static final Logger LOG = Logger.getLogger(ESIAdductsParameter.class.getName());

  // XML tags.
  private static final String MODIFICTAION_TAG = "ESImodification";
  private static final String ADDUCTS_TAG = "ESIadduct";
  private static final String ADDUCTS_ITEM_TAG = "ESIadductItem";
  private static final String NAME_ATTRIBUTE = "name";
  private static final String MASS_ATTRIBUTE = "mass_difference";
  private static final String CHARGE_ATTRIBUTE = "charge";
  private static final String MOL_FORMULA_ATTRIBUTE = "mol_formula";
  private static final String TYPE_ATTRIBUTE = "type";
  private static final String SELECTED_ATTRIBUTE = "selected";

  private MultiChoiceParameter<AdductType> adducts, modification;

  private ESIAdductsComponent comp;

  /**
   * Create the parameter.
   *
   * @param name name of the parameter.
   * @param description description of the parameter.
   */
  public ESIAdductsParameter(final String name, final String description) {
    super();
    adducts = new MultiChoiceParameter<AdductType>(name, description, new AdductType[0]);
    modification = new MultiChoiceParameter<AdductType>("Modifications", "Modifications on adducts",
        new AdductType[0]);
  }

  @Override
  public ESIAdductsComponent createEditingComponent() {
    comp = new ESIAdductsComponent(adducts.getChoices(), modification.getChoices());
    return comp;
  }

  @Override
  public void loadValueFromXML(final Element xmlElement) {
    // Start with current choices and empty selections.
    final ArrayList<AdductType> newChoices =
        new ArrayList<AdductType>(Arrays.asList(adducts.getChoices()));
    final ArrayList<AdductType> selections = new ArrayList<>();
    // load all adducts
    loadAdducts(xmlElement, ADDUCTS_TAG, newChoices, selections);
    // Set choices and selections (value).
    adducts.setChoices(newChoices.toArray(new AdductType[newChoices.size()]));
    adducts.setValue(selections.toArray(new AdductType[selections.size()]));

    // Start with current choices and empty selections.
    final ArrayList<AdductType> newChoicesMod =
        new ArrayList<AdductType>(Arrays.asList(modification.getChoices()));
    final ArrayList<AdductType> selectionsMod = new ArrayList<AdductType>();
    // load all modification
    loadAdducts(xmlElement, MODIFICTAION_TAG, newChoicesMod, selectionsMod);
    // Set choices and selections (value).
    modification.setChoices(newChoicesMod.toArray(new AdductType[newChoicesMod.size()]));
    modification.setValue(selectionsMod.toArray(new AdductType[selectionsMod.size()]));
  }

  private void loadAdducts(final Element xmlElement, String TAG, ArrayList<AdductType> newChoices,
      ArrayList<AdductType> selections) {
    NodeList adductElements = xmlElement.getChildNodes();
    for (int i = 0; i < adductElements.getLength(); i++) {
      Node a = adductElements.item(i);

      // adduct or modification
      if (a.getNodeName().equals(TAG)) {
        // is selected?
        boolean selectedNode =
            Boolean.parseBoolean(a.getAttributes().getNamedItem(SELECTED_ATTRIBUTE).getNodeValue());

        // sub adduct types that define the total adducttype
        NodeList childs = a.getChildNodes();

        List<AdductType> adducts = new ArrayList<>();

        // composite types have multiple child nodes
        for (int c = 0; c < childs.getLength(); c++) {
          Node childAdduct = childs.item(c);
          if (childAdduct.getNodeName().equals(ADDUCTS_ITEM_TAG)) {
            // Get attributes.
            final NamedNodeMap attributes = childAdduct.getAttributes();
            final Node nameNode = attributes.getNamedItem(NAME_ATTRIBUTE);
            final Node massNode = attributes.getNamedItem(MASS_ATTRIBUTE);
            final Node chargeNode = attributes.getNamedItem(CHARGE_ATTRIBUTE);
            final Node molFormulaNode = attributes.getNamedItem(MOL_FORMULA_ATTRIBUTE);
            final Node typeNode = attributes.getNamedItem(TYPE_ATTRIBUTE);

            // Valid attributes?
            if (nameNode != null && massNode != null && chargeNode != null && molFormulaNode != null
                && typeNode != null) {

              try {
                // Create new adduct.
                AdductType add = new AdductType(
                    IonModificationType.valueOf(typeNode.getNodeValue()), nameNode.getNodeValue(),
                    molFormulaNode.getNodeValue(), Double.parseDouble(massNode.getNodeValue()),
                    Integer.parseInt(chargeNode.getNodeValue()));
                adducts.add(add);
              } catch (NumberFormatException ex) {
                // Ignore.
                LOG.warning("Illegal mass difference attribute in " + childAdduct.getNodeValue());
              }
            }
          }
        }
        // create adduct as combination of all childs
        AdductType adduct = null;
        if (adducts.size() == 1) {
          adduct = new AdductType(adducts.get(0));
        } else
          adduct = new CombinedAdductType(adducts.toArray(new AdductType[adducts.size()]));


        // A new choice?
        if (!newChoices.contains(adduct)) {
          newChoices.add(adduct);
        }

        // Selected?
        if (!selections.contains(adduct) && selectedNode) {
          selections.add(adduct);
        }
      }
    }
  }

  /*
   * TODO old private boolean isContainedIn(ArrayList<ESIAdductType> adducts, ESIAdductType na) {
   * for(ESIAdductType a : adducts) { if(a.equals(na)) return true; } return false; }
   */

  @Override
  public void saveValueToXML(final Element xmlElement) {

    // Get choices and selections.
    for (int i = 0; i < 2; i++) {
      final AdductType[] choices = i == 0 ? adducts.getChoices() : modification.getChoices();
      final AdductType[] value = i == 0 ? adducts.getValue() : modification.getValue();
      final List<AdductType> selections =
          Arrays.asList(value == null ? new AdductType[] {} : value);

      if (choices != null) {
        final Document parent = xmlElement.getOwnerDocument();
        for (final AdductType item : choices) {
          final Element element = parent.createElement(i == 0 ? ADDUCTS_TAG : MODIFICTAION_TAG);
          saveTypeToXML(parent, element, item, selections);
          xmlElement.appendChild(element);
        }
      }
    }
  }

  /**
   * Save all
   * 
   * @param parent
   * @param parentElement
   * @param item
   * @param selections
   */
  private void saveTypeToXML(Document parent, Element parentElement, AdductType type,
      List<AdductType> selections) {
    parentElement.setAttribute(SELECTED_ATTRIBUTE, Boolean.toString(selections.contains(type)));
    // all adducts
    for (AdductType item : type.getAdducts()) {
      final Element element = parent.createElement(ADDUCTS_ITEM_TAG);
      element.setAttribute(NAME_ATTRIBUTE, item.getName());
      element.setAttribute(MASS_ATTRIBUTE, Double.toString(item.getMass()));
      element.setAttribute(CHARGE_ATTRIBUTE, Integer.toString(item.getCharge()));
      element.setAttribute(MOL_FORMULA_ATTRIBUTE, item.getMolFormula());
      element.setAttribute(TYPE_ATTRIBUTE, item.getType().toString());
      parentElement.appendChild(element);
    }
  }

  @Override
  public ESIAdductsParameter cloneParameter() {
    final ESIAdductsParameter copy = new ESIAdductsParameter(getName(), getDescription());
    copy.setChoices(adducts.getChoices(), modification.getChoices());
    copy.setValue(getValue());
    return copy;
  }

  private void setChoices(AdductType[] ad, AdductType[] mods) {
    adducts.setChoices(ad);
    modification.setChoices(mods);
  }

  @Override
  public String getName() {
    return "Adducts";
  }

  @Override
  public boolean checkValue(Collection<String> errorMessages) {
    if (getValue() == null) {
      errorMessages.add("Adducts is not set properly");
      return false;
    }
    return true;
  }

  @Override
  public String getDescription() {
    return "Adducts and modifications";
  }

  @Override
  public void setValueFromComponent(ESIAdductsComponent component) {
    adducts.setValueFromComponent(component.getAdducts());
    modification.setValueFromComponent(component.getMods());
    AdductType[][] choices = component.getChoices();
    adducts.setChoices(choices[0]);
    modification.setChoices(choices[1]);
    choices = component.getValue();
    adducts.setValue(choices[0]);
    modification.setValue(choices[1]);
  }

  @Override
  public AdductType[][] getValue() {
    AdductType[][] ad = {adducts.getValue(), modification.getValue()};
    return ad;
  }

  @Override
  public void setValue(AdductType[][] newValue) {
    adducts.setValue(newValue[0]);
    modification.setValue(newValue[1]);
  }

  @Override
  public void setValueToComponent(ESIAdductsComponent component, AdductType[][] newValue) {
    component.setValue(newValue);
  }
}
