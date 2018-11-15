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

package net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate;

import java.text.MessageFormat;
import net.sf.mzmine.datamodel.MZmineProject;
import net.sf.mzmine.datamodel.PeakList;
import net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.datastructure.CorrelationData.SimilarityMeasure;
import net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.filter.MinimumFeaturesFilterParameters;
import net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.msannotation.MSAnnotationLibrary;
import net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.msannotation.MSAnnotationParameters;
import net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.msannotation.refinement.AnnotationRefinementParameters;
import net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.msannotation.refinement.MSAnnMSMSCheckParameters;
import net.sf.mzmine.parameters.ParameterSet;
import net.sf.mzmine.parameters.parametertypes.tolerances.MZTolerance;

public class SimpleMetaMSEcorrelateTask extends MetaMSEcorrelateTask {

  /**
   * Create the task.
   *
   * @param parameterSet the parameters.
   * @param list peak list.
   */
  public SimpleMetaMSEcorrelateTask(final MZmineProject project, final ParameterSet parameterSet,
      final PeakList peakList) {
    super();
    this.project = project;
    this.peakList = peakList;
    parameters = parameterSet;

    // sample groups parameter
    useGroups =
        parameters.getParameter(SimpleMetaMSEcorrelateParameters.GROUPSPARAMETER).getValue();
    groupingParameter =
        (String) parameters.getParameter(SimpleMetaMSEcorrelateParameters.GROUPSPARAMETER)
            .getEmbeddedParameter().getValue();

    // height and noise
    noiseLevelCorr =
        parameters.getParameter(SimpleMetaMSEcorrelateParameters.NOISE_LEVEL).getValue();
    minHeight = parameters.getParameter(SimpleMetaMSEcorrelateParameters.MIN_HEIGHT).getValue();

    // by min percentage of samples in a sample set that contain this feature MIN_SAMPLES
    MinimumFeaturesFilterParameters minS = (MinimumFeaturesFilterParameters) parameterSet
        .getParameter(SimpleMetaMSEcorrelateParameters.MIN_SAMPLES_FILTER).getEmbeddedParameters();
    minFFilter = minS.createFilterWithGroups(project, peakList.getRawDataFiles(), groupingParameter,
        minHeight);

    // tolerances
    rtTolerance =
        parameterSet.getParameter(SimpleMetaMSEcorrelateParameters.RT_TOLERANCE).getValue();

    // FEATURE SHAPE CORRELATION
    minShapeCorrR =
        parameterSet.getParameter(SimpleMetaMSEcorrelateParameters.MIN_FSHAPE_CORR).getValue();
    shapeSimMeasure = SimilarityMeasure.PEARSON;
    minCorrelatedDataPoints = 5;
    minCorrDPOnFeatureEdge = 2;

    // total corr
    useTotalShapeCorrFilter = true;
    minTotalShapeCorrR = 0.5;
    // ADDUCTS
    MZTolerance mzTolerance =
        parameterSet.getParameter(SimpleMetaMSEcorrelateParameters.MZ_TOLERANCE).getValue();

    searchAdducts =
        parameterSet.getParameter(SimpleMetaMSEcorrelateParameters.ADDUCT_LIBRARY).getValue();
    MSAnnotationParameters annParam = parameterSet
        .getParameter(SimpleMetaMSEcorrelateParameters.ADDUCT_LIBRARY).getEmbeddedParameters();
    // simple parameter setup: provide mzTol and charge
    library = new MSAnnotationLibrary(annParam, mzTolerance, 100);

    adductCheckMode = annParam.getParameter(MSAnnotationParameters.CHECK_MODE).getValue();

    annotateOnlyCorrelated = true;

    // MSMS refinement
    doMSMSchecks = true;
    msmsChecks = new MSAnnMSMSCheckParameters();
    msmsChecks.getParameter(MSAnnMSMSCheckParameters.CHECK_MULTIMERS).setValue(true);
    msmsChecks.getParameter(MSAnnMSMSCheckParameters.CHECK_NEUTRALLOSSES).setValue(true);
    // set mass list MS2
    msmsChecks.getParameter(MSAnnMSMSCheckParameters.MASS_LIST).setValue(
        parameterSet.getParameter(SimpleMetaMSEcorrelateParameters.MS2_MASSLISTS).getValue());
    msmsChecks.getParameter(MSAnnMSMSCheckParameters.MIN_HEIGHT).setValue(
        parameterSet.getParameter(SimpleMetaMSEcorrelateParameters.NOISE_LEVEL_MS2).getValue());
    msmsChecks.getParameter(MSAnnMSMSCheckParameters.MZ_TOLERANCE).setValue(
        parameterSet.getParameter(SimpleMetaMSEcorrelateParameters.MZ_TOLERANCE_MS2).getValue());


    performAnnotationRefinement = true;
    refineParam = new AnnotationRefinementParameters();
    refineParam.getParameter(AnnotationRefinementParameters.DELETE_XMERS_ON_MSMS).setValue(true);
    refineParam.getParameter(AnnotationRefinementParameters.TRUE_THRESHOLD).setValue(4);

    // END OF ADDUCTS AND REFINEMENT
    // intensity correlation across samples
    useHeightCorrFilter = parameterSet
        .getParameter(SimpleMetaMSEcorrelateParameters.FILTER_FEATURE_HEIGHT_CORR).getValue();

    minHeightCorr = 0.5;
    minDPHeightCorr = 3;
    heightSimMeasure = SimilarityMeasure.PEARSON;

    // suffix
    autoSuffix = !parameters.getParameter(SimpleMetaMSEcorrelateParameters.SUFFIX).getValue();

    if (autoSuffix)
      suffix = MessageFormat.format("corr {2} r>={0} dp>={1}, {3}", minShapeCorrR,
          minCorrelatedDataPoints, shapeSimMeasure, searchAdducts ? "MS annot" : "");
    else
      suffix = parameters.getParameter(SimpleMetaMSEcorrelateParameters.SUFFIX)
          .getEmbeddedParameter().getValue();
  }

}