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

package net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.msannotation.refinement;

import java.text.MessageFormat;
import java.util.List;
import java.util.logging.Logger;
import io.github.msdk.MSDKRuntimeException;
import net.sf.mzmine.datamodel.DataPoint;
import net.sf.mzmine.datamodel.Feature;
import net.sf.mzmine.datamodel.MZmineProject;
import net.sf.mzmine.datamodel.MassList;
import net.sf.mzmine.datamodel.PeakList;
import net.sf.mzmine.datamodel.PeakListRow;
import net.sf.mzmine.datamodel.Scan;
import net.sf.mzmine.datamodel.identities.iontype.IonIdentity;
import net.sf.mzmine.datamodel.identities.iontype.IonType;
import net.sf.mzmine.datamodel.identities.ms2.MSMSIdentityList;
import net.sf.mzmine.datamodel.identities.ms2.MSMSIonRelationIdentity;
import net.sf.mzmine.datamodel.identities.ms2.interf.AbstractMSMSIdentity;
import net.sf.mzmine.datamodel.impl.RowGroup;
import net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.msannotation.MSAnnotationNetworkLogic;
import net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.msms.MSMSLogic;
import net.sf.mzmine.parameters.ParameterSet;
import net.sf.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import net.sf.mzmine.taskcontrol.AbstractTask;

public class MSAnnMSMSCheckTask extends AbstractTask {
  // Logger.
  private static final Logger LOG = Logger.getLogger(MSAnnMSMSCheckTask.class.getName());

  // mode
  public enum NeutralLossCheck {
    PRECURSOR, ANY_SIGNAL;

    @Override
    public String toString() {
      return super.toString().replaceAll("_", " ");
    }
  }


  private int finishedRows;
  private int totalRows;
  private final PeakList peakList;
  private final MZTolerance mzTolerance;

  private final ParameterSet parameters;
  private final MZmineProject project;

  private double minHeight;
  private boolean checkMultimers;
  private String massList;
  private boolean checkNeutralLosses;
  private NeutralLossCheck neutralLossCheck;

  /**
   * Create the task.
   *
   * @param parameterSet the parameters.
   * @param list peak list.
   */
  public MSAnnMSMSCheckTask(final MZmineProject project, final ParameterSet parameterSet,
      final PeakList peakLists) {
    this.project = project;
    this.peakList = peakLists;
    parameters = parameterSet;

    finishedRows = 0;
    totalRows = 0;

    // tolerances
    massList = parameterSet.getParameter(MSAnnMSMSCheckParameters.MASS_LIST).getValue();
    mzTolerance = parameterSet.getParameter(MSAnnMSMSCheckParameters.MZ_TOLERANCE).getValue();
    minHeight = parameterSet.getParameter(MSAnnMSMSCheckParameters.MIN_HEIGHT).getValue();
    checkMultimers = parameterSet.getParameter(MSAnnMSMSCheckParameters.CHECK_MULTIMERS).getValue();
    checkNeutralLosses =
        parameterSet.getParameter(MSAnnMSMSCheckParameters.CHECK_NEUTRALLOSSES).getValue();
    neutralLossCheck = parameterSet.getParameter(MSAnnMSMSCheckParameters.CHECK_NEUTRALLOSSES)
        .getEmbeddedParameter().getValue();
  }

  @Override
  public double getFinishedPercentage() {
    return totalRows == 0 ? 0 : finishedRows / totalRows;
  }

  @Override
  public String getTaskDescription() {
    return "MSMS-check refinement of annotations " + peakList.getName() + " ";
  }

  @Override
  public void run() {
    doCheck();
  }

  public void doCheck() {
    doCheck(true, peakList, massList, mzTolerance, minHeight, checkMultimers, checkNeutralLosses,
        neutralLossCheck);
  }

  public static void doCheck(boolean parallel, PeakList pkl, String massList,
      MZTolerance mzTolerance, double minHeight, boolean checkMultimers, boolean checkNeutralLosses,
      NeutralLossCheck neutralLossCheck) {
    // do parallel or not
    pkl.stream(parallel).forEach(row -> {
      doCheck(pkl, row, massList, mzTolerance, minHeight, checkMultimers, checkNeutralLosses,
          neutralLossCheck);
    });
  }

  public static void doCheck(PeakList pkl, PeakListRow row, String massList,
      MZTolerance mzTolerance, double minHeight, boolean checkMultimers, boolean checkNeutralLosses,
      NeutralLossCheck neutralLossCheck) {

    // has annotations?
    List<IonIdentity> ident = MSAnnotationNetworkLogic.getAllAnnotations(row);
    if (ident == null || ident.isEmpty())
      return;

    // has MS/MS
    try {
      if (checkMultimers) {
        checkMultimers(row, massList, ident, mzTolerance, minHeight);
      }

      if (checkNeutralLosses) {
        checkNeutralLosses(pkl, neutralLossCheck, row, massList, ident, mzTolerance, minHeight);
      }
    } catch (Exception e) {
      throw new MSDKRuntimeException(e);
    }
  }

  /**
   * Check for neutral loss in MSMS of all other rows in annotation or correlation group
   * 
   * @param neutralLossCheck
   * @param row
   * @param massList
   * @param msmsScan
   * @param ident
   * @param mzTolerance
   * @param minHeight
   */
  public static void checkNeutralLosses(PeakList pkl, NeutralLossCheck neutralLossCheck,
      PeakListRow row, String massList, List<IonIdentity> ident, MZTolerance mzTolerance,
      double minHeight) {
    if (ident == null || ident.isEmpty())
      return;

    int c = 0;
    for (IonIdentity ad : ident) {
      // do not test the unmodified
      if (!ad.getIonType().isModifiedUndefinedAdduct())
        continue;

      IonType mod = ad.getIonType();

      // is in group?
      RowGroup group = row.getGroup();
      if (group != null && !group.isEmpty()) {
        for (PeakListRow parent : group) {
          // has MS/MS
          Scan msmsScan = parent.getBestFragmentation();
          if (msmsScan == null)
            continue;
          MassList masses = msmsScan.getMassList(massList);
          if (masses == null)
            continue;

          DataPoint[] dps = masses.getDataPoints();
          Feature f = parent.getPeak(msmsScan.getDataFile());
          double precursorMZ = f.getMZ();
          boolean result = checkParentForNeutralLoss(neutralLossCheck, dps, ad, mod, mzTolerance,
              minHeight, precursorMZ);
          if (result)
            c++;
        }
      } else {
        // find all annotation edges
        int[] rows = ad.getPartnerRowsID();
        for (int parentid : rows) {
          PeakListRow parent = pkl.findRowByID(parentid);
          if (parent == null)
            continue;
          // has MS/MS
          Scan msmsScan = parent.getBestFragmentation();
          if (msmsScan == null)
            continue;
          MassList masses = msmsScan.getMassList(massList);
          if (masses == null)
            continue;

          DataPoint[] dps = masses.getDataPoints();
          Feature f = parent.getPeak(msmsScan.getDataFile());
          double precursorMZ = f.getMZ();
          boolean result = checkParentForNeutralLoss(neutralLossCheck, dps, ad, mod, mzTolerance,
              minHeight, precursorMZ);
          if (result)
            c++;
        }
      }
    }

    IonIdentity best = MSAnnotationNetworkLogic.getMostLikelyAnnotation(row, true);
    if (c > 0)
      LOG.info(MessageFormat.format(
          "Found {0} MS/MS fragments for neutral loss identifiers of rowID=[1} m/z={2} RT={3} best:{4}",
          c, row.getID(), row.getAverageMZ(), row.getAverageRT(),
          best == null ? "" : best.toString()));
  }

  /**
   * 
   * @param neutralLossCheck
   * @param row
   * @param dps
   * @param adduct
   * @param mod the modification to search for
   * @param mzTolerance
   * @param minHeight
   * @param precursorMZ
   */
  public static boolean checkParentForNeutralLoss(NeutralLossCheck neutralLossCheck,
      DataPoint[] dps, IonIdentity identity, IonType mod, MZTolerance mzTolerance, double minHeight,
      double precursorMZ) {
    boolean result = false;
    // loss for precursor mz
    DataPoint loss = MSMSLogic.findDPAt(dps, precursorMZ, mzTolerance, minHeight);
    if (loss != null) {
      MSMSIonRelationIdentity relation =
          new MSMSIonRelationIdentity(mzTolerance, loss, mod, precursorMZ);
      identity.addMSMSIdentity(relation);
      result = true;
    }


    if (neutralLossCheck.equals(NeutralLossCheck.ANY_SIGNAL)) {
      MSMSIdentityList msmsIdent = MSMSLogic.checkNeutralLoss(dps, mod, mzTolerance, minHeight);

      // found?
      for (AbstractMSMSIdentity id : msmsIdent) {
        identity.addMSMSIdentity(id);
        result = true;
      }
    }
    return result;
  }

  /**
   * Check all best fragment scans of all features for precursor - M
   * 
   * @param row
   * @param massList
   * @param ident
   * @param mzTolerance
   * @param minHeight
   */
  public static void checkMultimers(PeakListRow row, String massList, List<IonIdentity> ident,
      MZTolerance mzTolerance, double minHeight) {
    for (int i = 0; i < ident.size(); i++) {
      IonIdentity adduct = ident.get(i);
      for (Feature f : row.getPeaks()) {
        int sn = f.getMostIntenseFragmentScanNumber();
        if (sn != -1) {
          Scan msmsScan = f.getDataFile().getScan(sn);
          boolean isMultimer = checkMultimers(row, massList, adduct, msmsScan, ident, mzTolerance,
              minHeight, f.getMZ());
          if (isMultimer)
            break;
        }
      }
    }
  }

  public static boolean checkMultimers(PeakListRow row, String massList, IonIdentity adduct,
      Scan msmsScan, List<IonIdentity> ident, MZTolerance mzTolerance, double minHeight,
      double precursorMZ) {
    Feature f = row.getPeak(msmsScan.getDataFile());
    // only for M>1
    if (adduct.getIonType().getMolecules() > 1) {
      MSMSIdentityList msmsIdent = MSMSLogic.checkMultiMolCluster(msmsScan, massList, precursorMZ,
          adduct.getIonType(), mzTolerance, minHeight);

      // found?
      if (msmsIdent != null && msmsIdent.size() > 0) {
        // add all
        msmsIdent.stream().forEach(msms -> adduct.addMSMSIdentity(msms));
        return true;
      }
    }
    return false;
  }
}
