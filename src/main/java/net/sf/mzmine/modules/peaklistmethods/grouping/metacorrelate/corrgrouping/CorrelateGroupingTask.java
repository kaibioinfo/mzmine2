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

package net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.corrgrouping;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import com.google.common.util.concurrent.AtomicDouble;
import io.github.msdk.MSDKRuntimeException;
import net.sf.mzmine.datamodel.Feature;
import net.sf.mzmine.datamodel.MZmineProject;
import net.sf.mzmine.datamodel.PeakList;
import net.sf.mzmine.datamodel.PeakListRow;
import net.sf.mzmine.datamodel.RawDataFile;
import net.sf.mzmine.datamodel.impl.RowGroupList;
import net.sf.mzmine.datamodel.impl.SimpleFeature;
import net.sf.mzmine.datamodel.impl.SimplePeakList;
import net.sf.mzmine.datamodel.impl.SimplePeakListAppliedMethod;
import net.sf.mzmine.datamodel.impl.SimplePeakListRow;
import net.sf.mzmine.desktop.Desktop;
import net.sf.mzmine.desktop.impl.HeadLessDesktop;
import net.sf.mzmine.main.MZmineCore;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.correlation.FeatureCorrelationUtil;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.correlation.FeatureShapeCorrelationParameters;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.correlation.InterSampleHeightCorrParameters;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.datastructure.CorrelationData.SimilarityMeasure;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.datastructure.CorrelationRowGroup;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.datastructure.R2RCorrMap;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.datastructure.R2RCorrelationData;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.datastructure.R2RFullCorrelationData;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.minfeaturefilter.MinimumFeatureFilter;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.minfeaturefilter.MinimumFeatureFilter.OverlapResult;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.minfeaturefilter.MinimumFeaturesFilterParameters;
import net.sf.mzmine.parameters.ParameterSet;
import net.sf.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import net.sf.mzmine.taskcontrol.AbstractTask;
import net.sf.mzmine.taskcontrol.TaskStatus;
import net.sf.mzmine.util.PeakListRowSorter;
import net.sf.mzmine.util.PeakUtils;
import net.sf.mzmine.util.SortingDirection;
import net.sf.mzmine.util.SortingProperty;

public class CorrelateGroupingTask extends AbstractTask {

  // Logger.
  private static final Logger LOG = Logger.getLogger(CorrelateGroupingTask.class.getName());

  private AtomicDouble stageProgress = new AtomicDouble(0);
  private int totalRows;

  protected ParameterSet parameters;
  protected MZmineProject project;
  // GENERAL
  protected PeakList peakList;
  protected RTTolerance rtTolerance;
  protected boolean autoSuffix;
  protected String suffix;

  // GROUP and MIN SAMPLES FILTER
  protected boolean useGroups;
  protected String groupingParameter;
  /**
   * Minimum percentage of samples (in group if useGroup) that have to contain a feature
   */
  protected MinimumFeatureFilter minFFilter;
  // min adduct height and feature height for minFFilter
  protected double minHeight;

  // FEATURE SHAPE CORRELATION
  // correlation r to identify negative correlation
  protected boolean groupByFShapeCorr;
  protected SimilarityMeasure shapeSimMeasure;
  protected boolean useTotalShapeCorrFilter;
  protected double minTotalShapeCorrR;
  protected double minShapeCorrR;
  protected double noiseLevelCorr;
  protected int minCorrelatedDataPoints;
  protected int minCorrDPOnFeatureEdge;

  // MAX INTENSITY PROFILE CORRELATION ACROSS SAMPLES
  protected SimilarityMeasure heightSimMeasure;
  protected boolean useHeightCorrFilter;
  protected double minHeightCorr;
  protected int minDPHeightCorr;

  // output
  protected PeakList groupedPKL;

  private RowGroupList groups;



  /**
   * Create the task.
   *
   * @param parameterSet the parameters.
   * @param list peak list.
   */
  public CorrelateGroupingTask(final MZmineProject project, final ParameterSet parameterSet,
      final PeakList peakList) {
    this.project = project;
    this.peakList = peakList;
    parameters = parameterSet;

    totalRows = 0;

    // sample groups parameter
    useGroups = parameters.getParameter(CorrelateGroupingParameters.GROUPSPARAMETER).getValue();
    groupingParameter =
        (String) parameters.getParameter(CorrelateGroupingParameters.GROUPSPARAMETER)
            .getEmbeddedParameter().getValue();

    // height and noise
    noiseLevelCorr = parameters.getParameter(CorrelateGroupingParameters.NOISE_LEVEL).getValue();
    minHeight = parameters.getParameter(CorrelateGroupingParameters.MIN_HEIGHT).getValue();

    // by min percentage of samples in a sample set that contain this feature MIN_SAMPLES
    MinimumFeaturesFilterParameters minS = parameterSet
        .getParameter(CorrelateGroupingParameters.MIN_SAMPLES_FILTER).getEmbeddedParameters();
    minFFilter = minS.createFilterWithGroups(project, peakList.getRawDataFiles(), groupingParameter,
        minHeight);

    // tolerances
    rtTolerance = parameterSet.getParameter(CorrelateGroupingParameters.RT_TOLERANCE).getValue();

    // FEATURE SHAPE CORRELATION
    groupByFShapeCorr =
        parameterSet.getParameter(CorrelateGroupingParameters.FSHAPE_CORRELATION).getValue();
    FeatureShapeCorrelationParameters corrp = parameterSet
        .getParameter(CorrelateGroupingParameters.FSHAPE_CORRELATION).getEmbeddedParameters();
    // filter
    // start with high abundant features >= mainPeakIntensity
    // In this way we directly filter out groups with no abundant features
    // fill in smaller features after
    minShapeCorrR =
        corrp.getParameter(FeatureShapeCorrelationParameters.MIN_R_SHAPE_INTRA).getValue();
    shapeSimMeasure = corrp.getParameter(FeatureShapeCorrelationParameters.MEASURE).getValue();
    minCorrelatedDataPoints =
        corrp.getParameter(FeatureShapeCorrelationParameters.MIN_DP_CORR_PEAK_SHAPE).getValue();
    minCorrDPOnFeatureEdge =
        corrp.getParameter(FeatureShapeCorrelationParameters.MIN_DP_FEATURE_EDGE).getValue();

    // total corr
    useTotalShapeCorrFilter =
        corrp.getParameter(FeatureShapeCorrelationParameters.MIN_TOTAL_CORR).getValue();
    minTotalShapeCorrR = corrp.getParameter(FeatureShapeCorrelationParameters.MIN_TOTAL_CORR)
        .getEmbeddedParameter().getValue();

    // intensity correlation across samples
    useHeightCorrFilter =
        parameterSet.getParameter(CorrelateGroupingParameters.IMAX_CORRELATION).getValue();
    minHeightCorr = parameterSet.getParameter(CorrelateGroupingParameters.IMAX_CORRELATION)
        .getEmbeddedParameters().getParameter(InterSampleHeightCorrParameters.MIN_CORRELATION)
        .getValue();
    minDPHeightCorr = parameterSet.getParameter(CorrelateGroupingParameters.IMAX_CORRELATION)
        .getEmbeddedParameters().getParameter(InterSampleHeightCorrParameters.MIN_DP).getValue();

    heightSimMeasure = parameterSet.getParameter(CorrelateGroupingParameters.IMAX_CORRELATION)
        .getEmbeddedParameters().getParameter(InterSampleHeightCorrParameters.MEASURE).getValue();


    // suffix
    autoSuffix = !parameters.getParameter(CorrelateGroupingParameters.SUFFIX).getValue();

    if (autoSuffix)
      suffix = MessageFormat.format("corr {2} r>={0} dp>={1}", minShapeCorrR,
          minCorrelatedDataPoints, shapeSimMeasure);
    else
      suffix = parameters.getParameter(CorrelateGroupingParameters.SUFFIX).getEmbeddedParameter()
          .getValue();
  }

  public PeakList getGroupedPKL() {
    return groupedPKL;
  }

  public RowGroupList getGroups() {
    return groups;
  }

  public CorrelateGroupingTask() {}



  @Override
  public double getFinishedPercentage() {
    return stageProgress.get();
  }

  @Override
  public String getTaskDescription() {
    return "Identification of groups in " + peakList.getName() + " scan events (lists)";
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    LOG.info("Starting metaCorrelation search in " + peakList.getName() + " peaklists");
    try {
      if (isCanceled())
        return;

      // create new PKL for grouping
      groupedPKL = copyPeakList(peakList, suffix);

      // create correlation map
      // do R2R comparison correlation
      // might also do annotation if selected
      R2RCorrMap corrMap = new R2RCorrMap(rtTolerance, minFFilter);
      doR2RComparison(groupedPKL, corrMap);
      if (isCanceled())
        return;

      LOG.info("Corr: Starting to group by correlation");
      groups = corrMap.createCorrGroups(groupedPKL, stageProgress);

      if (isCanceled())
        return;
      // refinement:
      // filter by avg correlation in group
      // delete single connections between sub networks
      if (groups != null) {
        // set groups to pkl
        groups.stream().map(g -> (CorrelationRowGroup) g)
            .forEach(g -> g.recalcGroupCorrelation(corrMap));
        groupedPKL.setGroups(groups);
        groups.setGroupsToAllRows();

        if (isCanceled())
          return;

        // add to project
        project.addPeakList(groupedPKL);

        // Add task description to peakList.
        groupedPKL.addDescriptionOfAppliedTask(
            new SimplePeakListAppliedMethod("Correlation grouping", parameters));

        // Repaint the window to reflect the change in the peak list
        Desktop desktop = MZmineCore.getDesktop();
        if (!(desktop instanceof HeadLessDesktop))
          desktop.getMainWindow().repaint();

        // Done.
        setStatus(TaskStatus.FINISHED);
        LOG.info("Finished correlation grouping in " + peakList);
      }
    } catch (

    Exception t) {
      LOG.log(Level.SEVERE, "Correlation error", t);
      setStatus(TaskStatus.ERROR);
      setErrorMessage(t.getMessage());
      throw new MSDKRuntimeException(t);
    }
  }

  private PeakList copyPeakList(PeakList peakList, String suffix) {
    SimplePeakList pkl = new SimplePeakList(peakList + " " + suffix, peakList.getRawDataFiles());
    for (PeakListRow row : peakList.getRows()) {
      pkl.addRow(copyPeakRow(row));
    }
    return pkl;
  }

  /**
   * Create a copy of a peak list row.
   *
   * @param row the row to copy.
   * @return the newly created copy.
   */
  private static PeakListRow copyPeakRow(final PeakListRow row) {
    // Copy the peak list row.
    final PeakListRow newRow = new SimplePeakListRow(row.getID());
    PeakUtils.copyPeakListRowProperties(row, newRow);

    // Copy the peaks.
    for (final Feature peak : row.getPeaks()) {
      final Feature newPeak = new SimpleFeature(peak);
      PeakUtils.copyPeakProperties(peak, newPeak);
      newRow.addPeak(peak.getDataFile(), newPeak);
    }

    return newRow;
  }

  /**
   * Correlation and adduct network creation
   * 
   * @param peakList
   * @return
   */
  private void doR2RComparison(PeakList peakList, R2RCorrMap map) throws Exception {
    LOG.info("Corr: Creating row2row correlation map");
    PeakListRow rows[] = peakList.getRows();
    totalRows = rows.length;
    final RawDataFile raw[] = peakList.getRawDataFiles();

    // sort by avgRT
    Arrays.sort(rows, new PeakListRowSorter(SortingProperty.RT, SortingDirection.Ascending));

    // for all rows
    IntStream.range(0, rows.length - 1).parallel().forEach(i -> {
      if (!isCanceled()) {
        try {
          PeakListRow row = rows[i];
          // has a minimum number/% of features in all samples / in at least one groups
          if (minFFilter.filterMinFeatures(raw, row)) {
            for (int x = i + 1; x < totalRows; x++) {
              if (isCanceled())
                break;

              PeakListRow row2 = rows[x];

              // has a minimum number/% of overlapping features in all samples / in at least one
              // groups
              OverlapResult overlap =
                  minFFilter.filterMinFeaturesOverlap(raw, row, row2, rtTolerance);
              if (overlap.equals(OverlapResult.TRUE)) {
                // correlate if in rt range
                R2RFullCorrelationData corr =
                    FeatureCorrelationUtil.corrR2R(raw, row, row2, groupByFShapeCorr,
                        minCorrelatedDataPoints, minCorrDPOnFeatureEdge, minDPHeightCorr, minHeight,
                        noiseLevelCorr, useHeightCorrFilter, heightSimMeasure, minHeightCorr);

                // corr is even present if only grouping by retention time
                // corr is only null if heightCorrelation was not met
                if (corr != null && //
                (!groupByFShapeCorr || FeatureCorrelationUtil.checkFShapeCorr(groupedPKL,
                    minFFilter, corr, useTotalShapeCorrFilter, minTotalShapeCorrR, minShapeCorrR,
                    shapeSimMeasure))) {
                  // add to map
                  // can be because of any combination of
                  // retention time, shape correlation, non-negative height correlation
                  map.add(row, row2, corr);
                }
              }
            }
          }
          stageProgress.addAndGet(1d / totalRows);
        } catch (Exception e) {
          LOG.log(Level.SEVERE, "Error in parallel R2Rcomparison", e);
          throw new MSDKRuntimeException(e);
        }
      }
    });

    // number of f2f correlations
    int nR2Rcorr = 0;
    int nF2F = 0;
    for (R2RCorrelationData r2r : map.values()) {
      if (r2r instanceof R2RFullCorrelationData) {
        R2RFullCorrelationData data = (R2RFullCorrelationData) r2r;
        if (data.hasFeatureShapeCorrelation()) {
          nR2Rcorr++;
          nF2F += data.getCorrPeakShape().size();
        }
      }
    }

    LOG.info(MessageFormat.format(
        "Corr: Correlations done with {0} R2R correlations and {1} F2F correlations", nR2Rcorr,
        nF2F));
  }

  /**
   * direct exclusion for high level filtering check rt of all peaks of all raw files
   * 
   * @param row
   * @param row2
   * @param minHeight minimum feature height to check for RT
   * @return true only if there was at least one RawDataFile with features in both rows with
   *         height>minHeight and within rtTolerance
   */
  public boolean checkRTRange(RawDataFile[] raw, PeakListRow row, PeakListRow row2,
      double minHeight, RTTolerance rtTolerance) {
    for (int r = 0; r < raw.length; r++) {
      Feature f = row.getPeak(raw[r]);
      Feature f2 = row2.getPeak(raw[r]);
      if (f != null && f2 != null && f.getHeight() >= minHeight && f2.getHeight() >= minHeight
          && rtTolerance.checkWithinTolerance(f.getRT(), f2.getRT())) {
        return true;
      }
    }
    return false;
  }

}
