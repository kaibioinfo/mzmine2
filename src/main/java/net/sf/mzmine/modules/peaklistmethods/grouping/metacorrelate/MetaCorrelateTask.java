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

package net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
import net.sf.mzmine.datamodel.impl.SimplePeakListRow;
import net.sf.mzmine.desktop.Desktop;
import net.sf.mzmine.desktop.impl.HeadLessDesktop;
import net.sf.mzmine.main.MZmineCore;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.correlation.FeatureCorrelationUtil;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.correlation.FeatureShapeCorrelationParameters;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.correlation.InterSampleHeightCorrParameters;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.corrgrouping.CorrelateGroupingParameters;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.corrgrouping.CorrelateGroupingTask;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.datastructure.CorrelationData.SimilarityMeasure;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.datastructure.R2RCorrMap;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.datastructure.R2RCorrelationData;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.datastructure.R2RFullCorrelationData;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.minfeaturefilter.MinimumFeatureFilter;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.minfeaturefilter.MinimumFeatureFilter.OverlapResult;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.minfeaturefilter.MinimumFeaturesFilterParameters;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.msms.similarity.MS2SimilarityParameters;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.msms.similarity.MS2SimilarityTask;
import net.sf.mzmine.modules.peaklistmethods.identification.ionidentity.ionidnetworking.IonNetworkLibrary;
import net.sf.mzmine.modules.peaklistmethods.identification.ionidentity.ionidnetworking.IonNetworkingParameters;
import net.sf.mzmine.modules.peaklistmethods.identification.ionidentity.ionidnetworking.IonNetworkingTask;
import net.sf.mzmine.parameters.ParameterSet;
import net.sf.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import net.sf.mzmine.taskcontrol.AbstractTask;
import net.sf.mzmine.taskcontrol.Task;
import net.sf.mzmine.taskcontrol.TaskStatus;
import net.sf.mzmine.taskcontrol.TaskStatusListener;
import net.sf.mzmine.util.PeakListRowSorter;
import net.sf.mzmine.util.PeakUtils;
import net.sf.mzmine.util.SortingDirection;
import net.sf.mzmine.util.SortingProperty;

public class MetaCorrelateTask extends AbstractTask {

  // Logger.
  private static final Logger LOG = Logger.getLogger(MetaCorrelateTask.class.getName());

  public enum Stage {
    CORRELATION_ANNOTATION(0.5), GROUPING(0.6), MS2_SIMILARITY(0.8), ANNOTATION(0.95), REFINEMENT(
        1d);
    private double finalProgress;

    Stage(double finalProgress) {
      this.finalProgress = finalProgress;
    }

    public double getFinalProgress() {
      return finalProgress;
    }
  }


  private AtomicDouble stageProgress = new AtomicDouble(0);
  private int totalRows;

  protected ParameterSet parameters;
  protected MZmineProject project;
  // GENERAL
  protected PeakList peakList;
  protected RTTolerance rtTolerance;
  protected boolean autoSuffix;
  protected String suffix;

  // ADDUCTS
  protected IonNetworkingParameters annotationParameters;
  protected IonNetworkLibrary library;
  protected boolean searchAdducts;

  // MS2 similarity
  protected MS2SimilarityParameters ms2SimilarityCheckParam;


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

  // perform MS2Similarity check
  protected boolean checkMS2Similarity;

  // stage of processing
  private Stage stage;

  // output
  protected PeakList groupedPKL;
  protected boolean performAnnotationRefinement;
  protected CorrelateGroupingParameters groupParam;
  protected MinimumFeaturesFilterParameters minFeatureFilter;
  protected FeatureShapeCorrelationParameters corrParam;
  protected InterSampleHeightCorrParameters heightCorrParam;



  /**
   * Create the task.
   *
   * @param parameterSet the parameters.
   * @param list peak list.
   */
  public MetaCorrelateTask(final MZmineProject project, final ParameterSet parameterSet,
      final PeakList peakList) {
    this.project = project;
    this.peakList = peakList;
    parameters = parameterSet;

    totalRows = 0;

    // sample groups parameter
    useGroups = parameters.getParameter(MetaCorrelateParameters.GROUPSPARAMETER).getValue();
    groupingParameter = (String) parameters.getParameter(MetaCorrelateParameters.GROUPSPARAMETER)
        .getEmbeddedParameter().getValue();

    // height and noise
    noiseLevelCorr = parameters.getParameter(MetaCorrelateParameters.NOISE_LEVEL).getValue();
    minHeight = parameters.getParameter(MetaCorrelateParameters.MIN_HEIGHT).getValue();

    minFeatureFilter = (MinimumFeaturesFilterParameters) parameterSet
        .getParameter(MetaCorrelateParameters.MIN_SAMPLES_FILTER).getEmbeddedParameters();
    minFFilter = minFeatureFilter.createFilterWithGroups(project, peakList.getRawDataFiles(),
        groupingParameter, minHeight);

    // tolerances
    rtTolerance = parameterSet.getParameter(MetaCorrelateParameters.RT_TOLERANCE).getValue();

    // FEATURE SHAPE CORRELATION
    groupByFShapeCorr =
        parameterSet.getParameter(MetaCorrelateParameters.FSHAPE_CORRELATION).getValue();
    corrParam = parameterSet.getParameter(MetaCorrelateParameters.FSHAPE_CORRELATION)
        .getEmbeddedParameters();
    // filter
    // start with high abundant features >= mainPeakIntensity
    // In this way we directly filter out groups with no abundant features
    // fill in smaller features after
    minShapeCorrR =
        corrParam.getParameter(FeatureShapeCorrelationParameters.MIN_R_SHAPE_INTRA).getValue();
    shapeSimMeasure = corrParam.getParameter(FeatureShapeCorrelationParameters.MEASURE).getValue();
    minCorrelatedDataPoints =
        corrParam.getParameter(FeatureShapeCorrelationParameters.MIN_DP_CORR_PEAK_SHAPE).getValue();
    minCorrDPOnFeatureEdge =
        corrParam.getParameter(FeatureShapeCorrelationParameters.MIN_DP_FEATURE_EDGE).getValue();

    // total corr
    useTotalShapeCorrFilter =
        corrParam.getParameter(FeatureShapeCorrelationParameters.MIN_TOTAL_CORR).getValue();
    minTotalShapeCorrR = corrParam.getParameter(FeatureShapeCorrelationParameters.MIN_TOTAL_CORR)
        .getEmbeddedParameter().getValue();
    // ADDUCTS
    searchAdducts = parameterSet.getParameter(MetaCorrelateParameters.ADDUCT_LIBRARY).getValue();
    annotationParameters =
        parameterSet.getParameter(MetaCorrelateParameters.ADDUCT_LIBRARY).getEmbeddedParameters();
    annotationParameters =
        IonNetworkingParameters.createFullParamSet(annotationParameters, minHeight);
    library = new IonNetworkLibrary(
        annotationParameters.getParameter(IonNetworkingParameters.LIBRARY).getEmbeddedParameters(),
        annotationParameters.getParameter(IonNetworkingParameters.MZ_TOLERANCE).getValue());
    // END OF ADDUCTS AND REFINEMENT

    checkMS2Similarity =
        parameterSet.getParameter(MetaCorrelateParameters.MS2_SIMILARITY).getValue();
    ms2SimilarityCheckParam =
        parameterSet.getParameter(MetaCorrelateParameters.MS2_SIMILARITY).getEmbeddedParameters();

    // intensity correlation across samples
    useHeightCorrFilter =
        parameterSet.getParameter(MetaCorrelateParameters.IMAX_CORRELATION).getValue();
    heightCorrParam =
        parameterSet.getParameter(MetaCorrelateParameters.IMAX_CORRELATION).getEmbeddedParameters();
    minHeightCorr =
        parameterSet.getParameter(MetaCorrelateParameters.IMAX_CORRELATION).getEmbeddedParameters()
            .getParameter(InterSampleHeightCorrParameters.MIN_CORRELATION).getValue();
    minDPHeightCorr = parameterSet.getParameter(MetaCorrelateParameters.IMAX_CORRELATION)
        .getEmbeddedParameters().getParameter(InterSampleHeightCorrParameters.MIN_DP).getValue();

    heightSimMeasure = parameterSet.getParameter(MetaCorrelateParameters.IMAX_CORRELATION)
        .getEmbeddedParameters().getParameter(InterSampleHeightCorrParameters.MEASURE).getValue();


    // suffix
    autoSuffix = !parameters.getParameter(MetaCorrelateParameters.SUFFIX).getValue();

    if (autoSuffix)
      suffix = MessageFormat.format("corr {2} r>={0} dp>={1}, {3}", minShapeCorrR,
          minCorrelatedDataPoints, shapeSimMeasure, searchAdducts ? "MS annot" : "");
    else
      suffix =
          parameters.getParameter(MetaCorrelateParameters.SUFFIX).getEmbeddedParameter().getValue();

    // create grouping param
    groupParam = new CorrelateGroupingParameters(rtTolerance, useGroups, groupingParameter,
        minHeight, noiseLevelCorr, autoSuffix, suffix, minFeatureFilter, groupByFShapeCorr,
        useHeightCorrFilter, corrParam, heightCorrParam);

  }

  public MetaCorrelateTask() {}


  @Override
  public double getFinishedPercentage() {
    if (stage == null)
      return 0;
    else {
      double prevProgress =
          stage.ordinal() == 0 ? 0 : Stage.values()[stage.ordinal() - 1].getFinalProgress();
      return prevProgress + (stage.getFinalProgress() - prevProgress) * stageProgress.get();
    }
  }

  @Override
  public String getTaskDescription() {
    return "Identification of groups in " + peakList.getName() + " scan events (lists)";
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    LOG.info("Starting MSE correlation search in " + peakList.getName() + " peaklists");

    if (isCanceled())
      return;

    // grouping
    CorrelateGroupingTask groupTask = new CorrelateGroupingTask(project, groupParam, peakList);
    groupTask.addTaskStatusListener(new TaskStatusListener() {
      @Override
      public void taskStatusChanged(Task task, TaskStatus newStatus, TaskStatus oldStatus) {
        switch (newStatus) {
          case CANCELED:
            cancel();
            break;
          case ERROR:
            cancel();
            break;
          case FINISHED:
            groupedPKL = groupTask.getGroupedPKL();
            RowGroupList groups = groupTask.getGroups();
            analyseGroups(groupedPKL, groups);
            break;
        }
      }
    });
    MZmineCore.getTaskController().addTask(groupTask);

    while (getStatus().equals(TaskStatus.PROCESSING)) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
      }
    }

    // // create new PKL for grouping
    // groupedPKL = copyPeakList(peakList, suffix);
    //
    // // MAIN STEP
    // // create correlation map
    // setStage(Stage.CORRELATION_ANNOTATION);
    //
    // // do R2R comparison correlation
    // // might also do annotation if selected
    // R2RCorrMap corrMap = new R2RCorrMap(rtTolerance, minFFilter);
    // doR2RComparison(groupedPKL, corrMap);
    // if (isCanceled())
    // return;
    //
    // LOG.info("Corr: Starting to group by correlation");
    // setStage(Stage.GROUPING);
    // RowGroupList groups = corrMap.createCorrGroups(groupedPKL, stageProgress);
    //
    // if (isCanceled())
    // return;
    // // refinement:
    // // filter by avg correlation in group
    // // delete single connections between sub networks
    // if (groups != null) {
    // // set groups to pkl
    // groups.stream().map(g -> (CorrelationRowGroup) g)
    // .forEach(g -> g.recalcGroupCorrelation(corrMap));
    // groupedPKL.setGroups(groups);
    // groups.setGroupsToAllRows();
    //
    // // do MSMS comparison of group
    // setStage(Stage.MS2_SIMILARITY);
  }

  public void analyseGroups(PeakList groupedPKL, RowGroupList groups) {
    try {
      List<AbstractTask> steps = new ArrayList<>();

      if (checkMS2Similarity) {
        // calc MS2 similarity for later visualisation
        MS2SimilarityTask ms2Sim =
            new MS2SimilarityTask(ms2SimilarityCheckParam, groupedPKL, groups);
        steps.add(ms2Sim);
      }

      // annotation at groups stage
      if (searchAdducts) {
        LOG.info("Corr: Annotation of groups only");
        setStage(Stage.ANNOTATION);
        IonNetworkingTask annTask =
            new IonNetworkingTask(project, annotationParameters, groupedPKL);
        steps.add(annTask);
      }

      for (AbstractTask task : steps) {
        AtomicBoolean state = new AtomicBoolean(true);
        task.addTaskStatusListener(new TaskStatusListener() {
          @Override
          public void taskStatusChanged(Task task, TaskStatus newStatus, TaskStatus oldStatus) {
            switch (newStatus) {
              case FINISHED:
                state.set(false);
                break;
              case CANCELED:
              case ERROR:
                cancel();
                break;
            }
          }
        });
        MZmineCore.getTaskController().addTask(task);
        while (state.get() && getStatus().equals(TaskStatus.PROCESSING)) {
          try {
            Thread.sleep(300);
          } catch (Exception e) {
          }
        }
      }

      // // add to project
      // project.addPeakList(groupedPKL);
      //
      // // do adduct search
      // // searchAdducts();
      // // Add task description to peakList.
      // groupedPKL.addDescriptionOfAppliedTask(new SimplePeakListAppliedMethod(
      // "Correlation grouping and identification of adducts", parameters));

      // Repaint the window to reflect the change in the peak list
      Desktop desktop = MZmineCore.getDesktop();
      if (!(desktop instanceof HeadLessDesktop))
        desktop.getMainWindow().repaint();

      // Done.
      setStatus(TaskStatus.FINISHED);
      LOG.info("Finished correlation grouping and adducts search in " + peakList);
    } catch (Exception t) {
      LOG.log(Level.SEVERE, "Correlation and adduct search error", t);
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

  private void setStage(Stage grouping) {
    stage = grouping;
    stageProgress.set(0d);
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
    AtomicInteger annotPairs = new AtomicInteger(0);
    AtomicInteger compared = new AtomicInteger(0);

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
