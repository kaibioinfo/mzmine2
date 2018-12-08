package net.sf.mzmine.datamodel.identities.iontype;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import net.sf.mzmine.datamodel.PeakIdentity;
import net.sf.mzmine.datamodel.PeakListRow;
import net.sf.mzmine.datamodel.identities.MolecularFormulaIdentity;
import net.sf.mzmine.datamodel.identities.iontype.networks.IonNetRelationPeakIdentity;
import net.sf.mzmine.datamodel.identities.iontype.networks.IonNetworkRelationInterf;
import net.sf.mzmine.parameters.parametertypes.tolerances.MZTolerance;

/**
 * An annotation network full of ions that point to the same neutral molecule (neutral mass)
 * 
 * @author Robin Schmid (robinschmid@uni-muenster.de)
 *
 */
public class IonNetwork extends HashMap<PeakListRow, IonIdentity>
    implements Comparable<IonNetwork> {
  private static final long serialVersionUID = 1L;

  // MZtolerance on MS1 to generate this network
  private MZTolerance mzTolerance;
  // network id
  private int id;
  // neutral mass of central molecule which is described by all members of this network
  private Double neutralMass = null;
  // maximum absolute deviation from neutral mass average
  private Double maxDev = null;
  // average retention time of network
  private double avgRT;
  // summed height
  private double heightSum = 0;

  // can be used to stream all networks only once
  // lowest row id
  private int lowestID = -1;

  // relationship to other IonNetworks (neutral molecules)
  // marks as modification of:
  private Map<IonNetwork, IonNetworkRelationInterf> relations;

  // possible formulas for this neutral mass
  private List<MolecularFormulaIdentity> molFormulas;

  public IonNetwork(MZTolerance mzTolerance, int id) {
    super();
    this.mzTolerance = mzTolerance;
    this.id = id;
  }

  public void setMzTolerance(MZTolerance mzTolerance) {
    this.mzTolerance = mzTolerance;
  }

  /**
   * Network ID
   * 
   * @return
   */
  public int getID() {
    return id;
  }

  public List<MolecularFormulaIdentity> getMolFormulas() {
    return molFormulas;
  }

  public Map<IonNetwork, IonNetworkRelationInterf> getRelations() {
    return relations;
  }

  /**
   * Add a relation to another ion network. This relation could be a modification
   * 
   * @param net
   * @param rel
   */
  public void addRelation(IonNetwork net, IonNetworkRelationInterf rel) {
    if (relations == null)
      relations = new HashMap<>();
    relations.put(net, rel);
    createRelationIdentity();
  }

  /**
   * Remove a relation to another ion network. This relation could be a modification
   * 
   * @param net
   * @param rel
   */
  public void removeRelation(IonNetwork net) {
    if (relations == null)
      return;
    relations.remove(net);
    createRelationIdentity();
  }

  /**
   * Clear
   */
  public void clearRelation() {
    relations = null;
    createRelationIdentity();
  }

  /**
   * Create relations identity
   */
  private IonNetRelationPeakIdentity createRelationIdentity() {
    String name = "";
    if (relations != null)
      name = relations.values().stream().filter(Objects::nonNull).map(rel -> rel.getName(this))
          .collect(Collectors.joining(", "));

    return new IonNetRelationPeakIdentity(this, name);
  }

  /**
   * The first formula should be the best
   * 
   * @param molFormulas
   */
  public void setMolFormulas(List<MolecularFormulaIdentity> molFormulas) {
    this.molFormulas = molFormulas;
  }

  public void addMolFormula(MolecularFormulaIdentity formula) {
    addMolFormula(formula, false);
  }

  public void addMolFormula(MolecularFormulaIdentity formula, boolean asBest) {
    if (molFormulas == null)
      molFormulas = new ArrayList<>();

    if (!molFormulas.isEmpty())
      molFormulas.remove(formula);

    if (asBest)
      this.molFormulas.add(0, formula);
    else
      this.molFormulas.add(formula);
  }

  public void setBestMolFormula(MolecularFormulaIdentity formula) {
    addMolFormula(formula, true);
  }

  public void removeMolFormula(MolecularFormulaIdentity formula) {
    if (molFormulas != null && !molFormulas.isEmpty())
      molFormulas.remove(formula);
  }

  /**
   * Best molecular formula (first in list)
   * 
   * @return
   */
  public MolecularFormulaIdentity getBestMolFormula() {
    return molFormulas == null || molFormulas.isEmpty() ? null : molFormulas.get(0);
  }

  /**
   * Neutral mass of center molecule which is described by all members of this network
   * 
   * @param neutralMass
   */
  public double getNeutralMass() {
    return neutralMass == null ? calcNeutralMass() : neutralMass;
  }

  @Override
  public IonIdentity put(PeakListRow key, IonIdentity value) {
    IonIdentity e = super.put(key, value);
    if (key.getID() < lowestID || lowestID == -1)
      lowestID = key.getID();

    value.setNetwork(this);

    fireChanged();
    return e;
  }

  @Override
  public IonIdentity remove(Object key) {
    IonIdentity e = super.remove(key);
    if (e != null && key instanceof PeakListRow && ((PeakListRow) key).getID() <= lowestID)
      recalcMinID();

    if (e != null) {
      e.setNetwork(null);
      fireChanged();
    }
    return e;
  }

  /**
   * Finds the minimum row id
   */
  public int recalcMinID() {
    lowestID = keySet().stream().mapToInt(PeakListRow::getID).min().orElse(-1);
    return lowestID;
  }

  @Override
  public void clear() {
    super.clear();
    lowestID = -1;
    fireChanged();
  }

  @Override
  public IonIdentity replace(PeakListRow key, IonIdentity value) {
    IonIdentity e = super.replace(key, value);
    if (key.getID() < lowestID || lowestID == -1)
      lowestID = key.getID();

    value.setNetwork(this);
    fireChanged();
    return e;
  }

  public void fireChanged() {
    resetNeutralMass();
    resetMaxDev();
  }

  public void resetNeutralMass() {
    neutralMass = null;
  }

  /**
   * Maximum absolute deviation from central neutral mass
   */
  public void resetMaxDev() {
    maxDev = null;
  }

  /**
   * Calculates and sets the neutral mass average and average rt
   * 
   * @return
   */
  public double calcNeutralMass() {
    neutralMass = null;
    if (size() == 0)
      return 0;

    double mass = 0;
    avgRT = 0;
    heightSum = 0;
    for (Entry<PeakListRow, IonIdentity> e : entrySet()) {
      mass += e.getValue().getIonType().getMass(e.getKey().getAverageMZ());
      avgRT += e.getKey().getAverageRT();
      // sum of heighest peaks heights
      heightSum += e.getKey().getBestPeak().getHeight();
    }
    avgRT = avgRT / size();
    neutralMass = mass / size();
    return neutralMass;
  }

  public double getAvgRT() {
    if (neutralMass == null)
      calcNeutralMass();
    return avgRT;
  }

  public double getHeightSum() {
    if (neutralMass == null)
      calcNeutralMass();
    return heightSum;
  }

  /**
   * calculates the maximum deviation from the average mass
   * 
   * @return
   */
  public double calcMaxDev() {
    maxDev = null;
    if (size() == 0)
      return 0;

    neutralMass = getNeutralMass();
    if (neutralMass == null || neutralMass == 0)
      return 0;

    double max = 0;
    for (Entry<PeakListRow, IonIdentity> e : entrySet()) {
      double mass = getMass(e);
      max = Math.max(Math.abs(neutralMass - mass), max);
    }
    maxDev = max;
    return maxDev;
  }

  /**
   * Neutral mass of entry
   * 
   * @param e
   * @return
   */
  public double getMass(Entry<PeakListRow, IonIdentity> e) {
    return e.getValue().getIonType().getMass(e.getKey().getAverageMZ());
  }

  public double getMaxDev() {
    return maxDev == null ? calcMaxDev() : maxDev;
  }

  /**
   * All rows point to the same neutral mass
   * 
   * @param mzTol
   * @return
   */
  public boolean checkAllWithinMZTol(MZTolerance mzTol) {
    double neutralMass = getNeutralMass();
    double maxDev = getMaxDev();
    return mzTol.checkWithinTolerance(neutralMass, neutralMass + maxDev);
  }

  public int[] getAllIDs() {
    return keySet().stream().mapToInt(e -> e.getID()).toArray();
  }

  public void setNetworkToAllRows() {
    values().stream().forEach(id -> id.setNetwork(this));
  }

  /**
   * Checks the calculated neutral mass of the ion annotation against the avg neutral mass
   * 
   * @param row
   * @param pid
   * @return
   */
  public boolean checkForAnnotation(PeakListRow row, IonType pid) {
    return mzTolerance.checkWithinTolerance(calcNeutralMass(), pid.getMass(row.getAverageMZ()));
  }

  /**
   * Checks for links and adds those as partner rows
   * 
   * @param row
   * @param pid
   */
  public void addAllLinksTo(PeakListRow row, IonIdentity pid) {
    double nmass = pid.getIonType().getMass(row.getAverageMZ());
    this.entrySet().stream().forEach(e -> {
      if (e.getKey().getID() != row.getID()) {
        double pmass = getMass(e);
        if (mzTolerance.checkWithinTolerance(pmass, nmass)) {
          // add to both
          pid.addPartnerRow(e.getKey(), e.getValue());
          e.getValue().addPartnerRow(row, pid);
        }
      }
    });
  }

  public void delete() {
    entrySet().stream().forEach(e -> {
      e.getKey().removeIonIdentity(e.getValue());
    });
    clear();
  }

  /**
   * row has smallest id?
   * 
   * @param row
   * @return
   */
  public boolean hasSmallestID(PeakListRow row) {
    return row.getID() == lowestID;
  }

  /**
   * Correlation group id (if existing) is always the one of the first entry
   * 
   * @return correlation group id or -1
   */
  public int getCorrID() {
    if (isEmpty())
      return -1;
    return keySet().iterator().next().getGroupID();
  }

  /**
   * Checks if all entries are in the same correlation group
   * 
   * @return correlation group id or -1
   */
  public boolean allSameCorrGroup() {
    if (isEmpty())
      return true;
    int cid = getCorrID();
    for (PeakListRow r : keySet()) {
      if (r.getGroupID() != cid)
        return false;
    }
    return true;
  }

  public MZTolerance getMZTolerance() {
    return mzTolerance;
  }

  public void setID(int i) {
    id = i;
    setNetworkToAllRows();
  }

  public void recalcConnections() {
    // Do not need to do this?
    // for (Entry<PeakListRow, ESIAdductIdentity> a : entrySet()) {
    // ESIAdductIdentity adduct = a.getValue();
    // if (adduct.getA().getAbsCharge() > 0)
    // adduct.resetLinks();
    // }

    // add all links
    for (Entry<PeakListRow, IonIdentity> a : entrySet()) {
      IonIdentity adduct = a.getValue();
      if (adduct.getIonType().getAbsCharge() > 0)
        addAllLinksTo(a.getKey(), adduct);
    }
  }

  @Override
  public int compareTo(IonNetwork net) {
    // -1 if this is better
    return Integer.compare(net.size(), this.size());
  }

  /**
   * Add identity of all relations to rows
   */
  public void addRelationsIdentityToRows() {
    for (PeakListRow r : keySet()) {
      // delete old relation identity
      for (int i = 0; i < r.getPeakIdentities().length;) {
        PeakIdentity id = r.getPeakIdentities()[i];
        if (id instanceof IonNetRelationPeakIdentity
            && ((IonNetRelationPeakIdentity) id).getNetwork() == this)
          r.removePeakIdentity(id);
        else
          i++;
      }

      r.addPeakIdentity(createRelationIdentity(), true);
    }
  }

}
