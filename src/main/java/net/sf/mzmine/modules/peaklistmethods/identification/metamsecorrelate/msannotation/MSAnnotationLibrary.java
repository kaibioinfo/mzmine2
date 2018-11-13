package net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.msannotation;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import net.sf.mzmine.datamodel.Feature;
import net.sf.mzmine.datamodel.PeakList;
import net.sf.mzmine.datamodel.PeakListRow;
import net.sf.mzmine.datamodel.RawDataFile;
import net.sf.mzmine.main.MZmineCore;
import net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.datastructure.identities.AdductType;
import net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.datastructure.identities.ESIAdductIdentity;
import net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.datastructure.identities.IonModificationType;
import net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.datastructure.identities.IonType;
import net.sf.mzmine.parameters.parametertypes.tolerances.MZTolerance;

public class MSAnnotationLibrary {
  private static final Logger LOG = Logger.getLogger(MSAnnotationLibrary.class.getName());

  public enum CheckMode {
    AVGERAGE, ONE_FEATURE, ALL_FEATURES;

    @Override
    public String toString() {
      return super.toString().replaceAll("_", " ");
    }
  }

  private MZTolerance mzTolerance;
  // adducts
  private final AdductType[] selectedAdducts;
  private final AdductType[] selectedMods;
  private List<IonType> allAdducts = new ArrayList<>();
  private final boolean isPositive;
  private final int maxMolecules, maxCharge;

  public MSAnnotationLibrary(MSAnnotationParameters parameterSet) {
    this(parameterSet, parameterSet.getParameter(MSAnnotationParameters.MZ_TOLERANCE).getValue(),
        parameterSet.getParameter(MSAnnotationParameters.MAX_CHARGE).getValue());
  }

  /**
   * For simple setup
   * 
   * @param parameterSet
   */
  public MSAnnotationLibrary(MSAnnotationParameters parameterSet, MZTolerance mzTolerance,
      int maxCharge) {
    this.mzTolerance = mzTolerance;
    this.maxCharge = maxCharge;
    // adducts stuff
    isPositive = parameterSet.getParameter(MSAnnotationParameters.POSITIVE_MODE).getValue()
        .equals("POSITIVE");
    maxMolecules = parameterSet.getParameter(MSAnnotationParameters.MAX_MOLECULES).getValue();

    selectedAdducts = parameterSet.getParameter(MSAnnotationParameters.ADDUCTS).getValue()[0];
    selectedMods = parameterSet.getParameter(MSAnnotationParameters.ADDUCTS).getValue()[1];

    createAllAdducts(isPositive, maxMolecules, maxCharge);
  }

  /**
   * create all possible adducts
   */
  private void createAllAdducts(boolean positive, int maxMolecules, int maxCharge) {
    // normal primary adducts
    allAdducts.clear();
    // add all [M+?]c+ as references to neutral loss
    // [M-H2O+?]c+
    for (int c = 1; c <= maxCharge; c++)
      allAdducts.add(1, new IonType(AdductType.getUndefinedforCharge(positive ? c : -c)));

    for (AdductType a : selectedAdducts) {
      if ((a.getCharge() > 0 && positive) || (a.getCharge() < 0 && !positive)) {
        if (a.getAbsCharge() <= maxCharge) {
          for (int n = 1; n <= maxMolecules; n++)
            allAdducts.add(n, new IonType(a));
        }
      }
    }

    addModification();
    // print them out
    for (IonType a : allAdducts)
      LOG.info(a.toString());
  }

  /**
   * Does find all possible adduct combinations
   * 
   * @param mainRow main peak.
   * @param possibleAdduct candidate adduct peak.
   */
  public @Nonnull List<ESIAdductIdentity[]> findAdducts(final PeakList peakList,
      final PeakListRow row1, final PeakListRow row2, final CheckMode mode,
      final double minHeight) {
    return findAdducts(peakList, row1, row2, row1.getRowCharge(), row2.getRowCharge(), mode,
        minHeight);
  }

  /**
   * Does find all possible adducts
   * 
   * @param peakList
   * @param row1
   * @param row2
   * @param z1 -1 or 0 if not set (charge state always positive)
   * @param z2 -1 or 0 if not set (charge state always positive)
   * @return
   */
  public @Nonnull List<ESIAdductIdentity[]> findAdducts(final PeakList peakList,
      final PeakListRow row1, final PeakListRow row2, int z1, int z2, final CheckMode mode,
      final double minHeight) {
    z1 = Math.abs(z1);
    z2 = Math.abs(z2);
    List<ESIAdductIdentity[]> list = new ArrayList<>();
    // check all combinations of adducts
    for (IonType adduct : allAdducts) {
      for (IonType adduct2 : allAdducts) {
        if (adduct.equals(adduct2))
          continue;

        // do not check if MOL = MOL and MOL>1
        // only one can be modified
        // check charge state if absCharge is not -1 or 0 (no charge detected)
        if (checkMolCount(adduct, adduct2) //
            && checkMaxMod(adduct, adduct2) //
            && checkChargeStates(adduct, adduct2, z1, z2) //
            && checkMultiChargeDifference(adduct, adduct2) //
            && checkSameAdducts(adduct, adduct2)) {
          // checks each raw file - only true if all m/z are in range
          if (checkAdduct(peakList, row1, row2, adduct, adduct2, mode, minHeight)) {
            // is a2 a modification of a1? (same adducts - different mods
            if (adduct2.isModificationOf(adduct)) {
              IonType mod = adduct2.subtractMods(adduct);
              IonType undefined = new IonType(AdductType.getUndefinedforCharge(adduct.getCharge()));
              list.add(ESIAdductIdentity.addAdductIdentityToRow(row1, undefined, row1, mod));
            } else if (adduct.isModificationOf(adduct2)) {
              IonType mod = adduct.subtractMods(adduct2);
              IonType undefined =
                  new IonType(AdductType.getUndefinedforCharge(adduct2.getCharge()));
              list.add(ESIAdductIdentity.addAdductIdentityToRow(row1, mod, row2, undefined));
            } else {
              // Add adduct identity and notify GUI.
              // only if not already present
              list.add(ESIAdductIdentity.addAdductIdentityToRow(row1, adduct, row2, adduct2));
            }
            // update
            MZmineCore.getProjectManager().getCurrentProject().notifyObjectChanged(row1, false);
            MZmineCore.getProjectManager().getCurrentProject().notifyObjectChanged(row2, false);
          }
        }
      }
    }
    // no adduct to be found
    return list;
  }

  /**
   * Do not allow adduct overlap: Only if both are of type undefined ?
   * 
   * @param a
   * @param b
   * @return
   */
  private boolean checkSameAdducts(IonType a, IonType b) {
    // no adduct overlap (with none being undefined) (or both undefined_adduct)
    return (!a.hasAdductOverlap(b)
        && !a.getAdduct().getType().equals(IonModificationType.UNDEFINED_ADDUCT)
        && !b.getAdduct().getType().equals(IonModificationType.UNDEFINED_ADDUCT))
        || (a.getAdduct().getType().equals(IonModificationType.UNDEFINED_ADDUCT)
            && b.getAdduct().getType().equals(IonModificationType.UNDEFINED_ADDUCT));
  }

  /**
   * [yM+X]2+ and [yM+X-H]+ are only different by -H. if any adduct part or modification equals,
   * return false. Charge is different
   * 
   * @param a
   * @param b
   * @return only true if charge is equal or no modification or adduct sub part equals
   */
  private boolean checkMultiChargeDifference(IonType a, IonType b) {
    return a.getCharge() == b.getCharge() || (a.hasModificationOverlap(b) && a.hasAdductOverlap(b));
  }

  /**
   * MOL != MOL or MOL==1
   * 
   * @param a
   * @param b
   * @return
   */
  private boolean checkMolCount(IonType a, IonType b) {
    return a.getMolecules() != b.getMolecules() || (a.getMolecules() == 1 && b.getMolecules() == 1);
  }

  /**
   * True if a charge state was not detected or if it fits to the adduct
   * 
   * @param adduct
   * @param adduct2
   * @param z1
   * @param z2
   * @return
   */
  private boolean checkChargeStates(IonType adduct, IonType adduct2, int z1, int z2) {
    return (z1 <= 0 || adduct.getAbsCharge() == z1) && (z2 <= 0 || adduct2.getAbsCharge() == z2);
  }

  /**
   * Only one adduct can have modifications
   * 
   * @param adduct
   * @param adduct2
   * @return
   */
  private boolean checkMaxMod(IonType adduct, IonType adduct2) {
    return !(adduct.getModCount() > 0 && adduct2.getModCount() > 0);
  }

  /**
   * Check if candidate peak is a given type of adduct of given main peak. is not checking retention
   * time (has to be checked before)
   * 
   * @param peakList
   * @param row1
   * @param row2
   * @param adduct
   * @param adduct2
   * @param minHeight exclude smaller peaks as they can have a higher mz difference
   * @return false if one peak pair with height>=minHeight is outside of mzTolerance
   */
  private boolean checkAdduct(final PeakList peakList, final PeakListRow row1,
      final PeakListRow row2, final IonType adduct, final IonType adduct2, final CheckMode mode,
      double minHeight) {
    // averarge mz
    if (mode.equals(CheckMode.AVGERAGE)) {
      double m1 = adduct.getMass(row1.getAverageMZ());
      double m2 = adduct2.getMass(row2.getAverageMZ());
      return mzTolerance.checkWithinTolerance(m1, m2);
    } else {
      // feature comparison
      // for each peak[rawfile] in row
      boolean hasCommonPeak = false;
      //
      for (RawDataFile raw : peakList.getRawDataFiles()) {
        Feature f1 = row1.getPeak(raw);
        Feature f2 = row2.getPeak(raw);
        // check for minimum height. Small peaks have a higher delta mz
        if (f1 != null && f2 != null && f1.getHeight() >= minHeight
            && f2.getHeight() >= minHeight) {
          hasCommonPeak = true;
          double m1 = adduct.getMass(f1.getMZ());
          double m2 = adduct2.getMass(f2.getMZ());
          boolean sameMZ = mzTolerance.checkWithinTolerance(m1, m2);

          // short cut
          switch (mode) {
            case ONE_FEATURE:
              if (sameMZ)
                return true;
              break;
            case ALL_FEATURES:
              if (!sameMZ)
                return false;
              break;
          }
        }
      }
      // directly returns false if not in range
      // so if has common peak = isAdduct
      return mode.equals(CheckMode.ALL_FEATURES) && hasCommonPeak;
    }
  }

  /**
   * adds modification to the existing adducts
   */
  private void addModification() {
    for (AdductType a : selectedMods)
      for (IonType ion : allAdducts)
        allAdducts.add(ion.createModified(a));
  }

  private boolean isContainedIn(List<AdductType> adducts, AdductType na) {
    for (AdductType a : adducts) {
      if (a.sameMathDifference(na))
        return true;
    }
    return false;
  }

  public void setMzTolerance(MZTolerance mzTolerance) {
    this.mzTolerance = mzTolerance;
  }

  public MZTolerance getMzTolerance() {
    return mzTolerance;
  }

  public AdductType[] getSelectedAdducts() {
    return selectedAdducts;
  }

  public AdductType[] getSelectedMods() {
    return selectedMods;
  }

  public List<IonType> getAllAdducts() {
    return allAdducts;
  }

  public boolean isPositive() {
    return isPositive;
  }

  public int getMaxMolecules() {
    return maxMolecules;
  }

  public int getMaxCharge() {
    return maxCharge;
  }

}
