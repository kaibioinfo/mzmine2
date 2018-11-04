package net.sf.mzmine.util.maths.similarity;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import net.sf.mzmine.util.maths.Transform;

public abstract class Similarity {

  // Measures
  public static final Similarity COSINE = new Similarity() {
    @Override
    public double calc(double[][] data) {
      return dot(data) / (Math.sqrt(norm(data, 0)) * Math.sqrt(norm(data, 1)));
    }
  };

  /**
   * Log ratio proportionality
   * https://journals.plos.org/ploscompbiol/article?id=10.1371/journal.pcbi.1004075
   * 
   * 
   */
  public static final Similarity LOG_VAR_PROPORTIONALITY = new Similarity() {
    @Override
    public double calc(double[][] data) {
      double[] logratioXY = transform(ratio(data, 0, 1), Transform.LOG);
      double[] logx = transform(col(data, 0), Transform.LOG);
      return var(logratioXY) / var(logx);
    }
  };

  /**
   * Log ratio proportionality -1 to 1
   * https://journals.plos.org/ploscompbiol/article?id=10.1371/journal.pcbi.1004075
   * 
   * 
   */
  public static final Similarity LOG_VAR_CONCORDANCE = new Similarity() {
    @Override
    public double calc(double[][] data) {
      double[] logx = transform(col(data, 0), Transform.LOG);
      double[] logy = transform(col(data, 1), Transform.LOG);
      return 2 * covar(logx, logy) / (var(logx) + var(logy));
    }
  };
  /**
   * Spearmans correlation:
   */
  public static final Similarity SPEARMANS_CORR = new Similarity() {
    @Override
    public double calc(double[][] data) {
      SpearmansCorrelation corr = new SpearmansCorrelation();
      return corr.correlation(col(data, 0), col(data, 1));
    }
  };
  /**
   * Spearmans correlation:
   */
  public static final Similarity PEARSONS_CORR = new Similarity() {
    @Override
    public double calc(double[][] data) {
      PearsonsCorrelation corr = new PearsonsCorrelation();
      return corr.correlation(col(data, 0), col(data, 1));
    }
  };



  // #############################################
  // abstract methods
  /**
   * 
   * @param data data[dp][0,1]
   * @return
   */
  public abstract double calc(double[][] data);

  public double[] col(double[][] data, int i) {
    double[] v = new double[data.length];
    for (int d = 0; d < data.length; d++) {
      v[d] = data[d][i];
    }
    return v;
  }

  /**
   * ratio of a/b
   * 
   * @param data
   * @param x
   * @param y
   * @return
   */
  public double[] ratio(double[][] data, int a, int b) {
    double[] v = new double[data.length];
    for (int d = 0; d < data.length; d++) {
      v[d] = data[d][a] / data[d][b];
    }
    return v;
  }

  public double[] transform(double[] data, Transform transform) {
    double[] v = new double[data.length];
    for (int d = 0; d < data.length; d++) {
      v[d] = transform.transform(data[d]);
    }
    return v;
  }


  // ############################################
  // COMMON METHODS
  /**
   * sum(x*y)
   * 
   * @param data data[dp][x,y]
   * @return
   */
  public double dot(double[][] data) {
    double sum = 0;
    for (double[] val : data)
      sum += val[0] * val[1];
    return sum;
  }

  public double mean(double[][] data, int i) {
    double m = 0;
    for (int d = 0; d < data.length; d++) {
      m = data[d][i];
    }
    return m / data.length;
  }

  public double var(double[][] data, int i) {
    double mean = mean(data, i);
    double var = 0;
    for (int d = 0; d < data.length; d++) {
      var += Math.pow(mean - data[d][i], 2);
    }
    return var / data.length;
  }

  public double mean(double[] data) {
    double m = 0;
    for (int d = 0; d < data.length; d++) {
      m = data[d];
    }
    return m / data.length;
  }

  public double var(double[] data) {
    double mean = mean(data);
    double var = 0;
    for (int d = 0; d < data.length; d++) {
      var += Math.pow(mean - data[d], 2);
    }
    return var / (data.length - 1);
  }

  public double covar(double[] a, double[] b) {
    double meanA = mean(a);
    double meanB = mean(b);
    double covar = 0;
    for (int d = 0; d < a.length; d++) {
      covar += (a[d] - meanA) * (b[d] - meanB);
    }
    return covar / (a.length - 1);
  }

  /**
   * Sample variance n-1
   * 
   * @param data
   * @param i
   * @return
   */
  public double varN(double[][] data, int i) {
    double mean = mean(data, i);
    double var = 0;
    for (int d = 0; d < data.length; d++) {
      var += Math.pow(mean - data[d][i], 2);
    }
    return var / (data.length);
  }


  public double stdev(double[][] data, int i) {
    return Math.sqrt(var(data, i));
  }

  public double stdevN(double[][] data, int i) {
    return Math.sqrt(varN(data, i));
  }

  /**
   * Euclidean norm (self dot product). sum(x*x)
   * 
   * @param data data[dp][indexOfX]
   * @param index
   * @return
   */
  public double norm(double[][] data, int index) {
    double sum = 0;
    for (double[] val : data)
      sum += val[index] * val[index];
    return sum;
  }
}
