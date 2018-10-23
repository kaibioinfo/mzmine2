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

package net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.datastructure.identities;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import net.sf.mzmine.datamodel.PeakIdentity;
import net.sf.mzmine.datamodel.PeakListRow;
import net.sf.mzmine.datamodel.impl.SimplePeakIdentity;
import net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.msannotation.AnnotationNetwork;
import net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.msms.identity.MSMSIdentityList;
import net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.msms.identity.MSMSIonRelationIdentity;
import net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.msms.identity.MSMSIonRelationIdentity.Relation;
import net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.msms.identity.MSMSMultimerIdentity;
import net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.msms.identity.interf.AbstractMSMSIdentity;

public class ESIAdductIdentity extends SimplePeakIdentity {

  private NumberFormat netIDForm = new DecimalFormat("000");

  private ESIAdductType a;
  // identifier like [M+H]+
  private String adduct;
  private String massDifference;
  // partner rowIDs
  private String partnerRows;
  // network id (number)
  private AnnotationNetwork network;

  /**
   * List of MSMS identities. e.g., multimers/monomers that were found in MS/MS data
   */
  private MSMSIdentityList msmsIdent;

  /**
   * Create the identity.
   *
   * @param originalPeakListRow adduct of this peak list row.
   * @param adduct type of adduct.
   */
  public ESIAdductIdentity(final PeakListRow originalPeakListRow, final ESIAdductType adduct) {
    super("later");
    a = adduct;
    this.adduct = adduct.toString(false);
    this.massDifference = adduct.getMassDiffString();
    partnerRows = String.valueOf(originalPeakListRow.getID());
    setPropertyValue(PROPERTY_METHOD, "MS annotation");
    setPropertyValue(PROPERTY_NAME, getIDString());
  }

  public ESIAdductType getA() {
    return a;
  }

  public String getAdduct() {
    return adduct;
  }

  public String getPartnerRows() {
    return partnerRows;
  }

  public void addPartnerRow(PeakListRow row) {
    // already a partner?
    // add new partner
    if (!hasPartnerID(row.getID())) {
      if (partnerRows.isEmpty())
        partnerRows = "" + row.getID();
      else
        partnerRows += "," + row.getID();
      setPropertyValue(PROPERTY_NAME, getIDString());
    }
  }

  public void resetLinks() {
    partnerRows = "";
    setPropertyValue(PROPERTY_NAME, getIDString());
  }

  public String getIDString() {
    StringBuilder b = new StringBuilder();
    if (getNetID() != -1) {
      b.append("Net");
      b.append(getNetIDString());
      b.append(" ");
    }
    b.append(adduct);
    b.append(" indentified by ID=");
    b.append(partnerRows);

    // MSMS backed id for multimers
    if (getMSMSMultimerCount() > 0) {
      b.append(" (MS/MS:xmer)");
    }
    // MSMS backed id for insource frag
    if (getA().getModCount() > 0) {
      if (getMSMSModVerify() > 0) {
        b.append(" (MS/MS:insource frag)");
      }
    }
    return b.toString();
  }

  @Override
  public String toString() {
    return getIDString();
  }

  public boolean equalsAdduct(ESIAdductType acompare) {
    return acompare.toString(false).equals(this.adduct);
  }

  public int[] getPartnerRowsID() {
    if (partnerRows.isEmpty())
      return new int[0];

    String[] split = partnerRows.split(",");
    int[] ids = new int[split.length];
    for (int i = 0; i < split.length; i++)
      ids[i] = Integer.valueOf(split[i]);

    return ids;
  }

  /**
   * Network number
   * 
   * @param id
   */
  public void setNetwork(AnnotationNetwork net) {
    network = net;
    setPropertyValue(PROPERTY_NAME, getIDString());
  }

  /**
   * Network number
   * 
   * @return
   */
  public int getNetID() {
    return network == null ? -1 : network.getID();
  }

  public String getNetIDString() {
    return netIDForm.format(getNetID());
  }

  /**
   * 
   * @param row
   * @param link
   * @return The identity of row, determined by link or null if there is no connection
   */
  public static ESIAdductIdentity getIdentityOf(PeakListRow row, PeakListRow link) {
    for (PeakIdentity pi : row.getPeakIdentities()) {
      // identity by ms annotation module
      if (pi instanceof ESIAdductIdentity) {
        ESIAdductIdentity adduct = (ESIAdductIdentity) pi;
        if (adduct.hasPartnerID(link.getID()))
          return adduct;
      }
    }
    return null;
  }

  /**
   * Checks whether partner ids contain a certain id
   * 
   * @param id
   * @return
   */
  public boolean hasPartnerID(int id) {
    return Arrays.stream(getPartnerRowsID()).anyMatch(pid -> pid == id);
  }

  public void setMSMSIdentities(MSMSIdentityList msmsIdent) {
    this.msmsIdent = msmsIdent;
    setPropertyValue(PROPERTY_NAME, getIDString());
  }

  public void addMSMSIdentity(AbstractMSMSIdentity ident) {
    if (this.msmsIdent == null)
      msmsIdent = new MSMSIdentityList();
    msmsIdent.add(ident);
    setPropertyValue(PROPERTY_NAME, getIDString());
  }

  public MSMSIdentityList getMSMSIdentities() {
    return msmsIdent;
  }

  /**
   * Count of signals that verify this multimer identity
   * 
   * @return
   */
  public int getMSMSMultimerCount() {
    if (msmsIdent == null || msmsIdent.isEmpty())
      return 0;

    return (int) msmsIdent.stream().filter(id -> id instanceof MSMSMultimerIdentity).count();
  }

  public int getMSMSModVerify() {
    if (msmsIdent == null || msmsIdent.isEmpty())
      return 0;

    return (int) msmsIdent.stream().filter(id -> id instanceof MSMSIonRelationIdentity
        && ((MSMSIonRelationIdentity) id).getRelation().equals(Relation.NEUTRAL_LOSS)).count();
  }

  public AnnotationNetwork getNetwork() {
    return network;
  }

  /**
   * deletes from network
   */
  public void delete(PeakListRow row) {
    if (network != null) {
      network.remove(row);
    }
    row.removePeakIdentity(this);
  }

}