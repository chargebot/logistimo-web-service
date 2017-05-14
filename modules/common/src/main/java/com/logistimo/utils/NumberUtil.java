/**
 *
 */
package com.logistimo.utils;

import com.logistimo.logger.XLog;

import java.util.Random;

/**
 * @author Arun
 */
public class NumberUtil {

  private static final XLog xLogger = XLog.getLog(NumberUtil.class);


  public static float getFloatValue(Float f) {
    if (f == null || Float.isNaN(f.floatValue())) {
      return 0F;
    }
    return f.floatValue();
  }

  // Round to 2 decimal places
  public static float round2(float f) {
    return (Math.round(f * 100.0)) / 100.0F;
  }

  public static double getDoubleValue(Double d) {
    if (d == null || Double.isNaN(d.doubleValue())) {
      return 0D;
    }
    return d.doubleValue();
  }

  public static int getIntegerValue(Integer i) {
    if (i == null) {
      return 0;
    }
    return i.intValue();
  }

  // Returns an integer (if the float does not have decimal places), or a float rounded to 2nd decimal
  public static String getFormattedValue(float f) {
    // Check if this is integer
    if (Math.ceil((double) f) == f) {
      return String.valueOf((int) f);
    } else {
      float f1 = ((float) Math.round(f * 100F)) / 100F;
      return String.format("%.2f", f1);
    }
  }

  // Returns a five digit random number
  public static int generateFiveDigitRandomNumber() {
    Random r = new Random(System.currentTimeMillis());
    return (1 + r.nextInt(2)) * 10000 + r.nextInt(10000);
  }

  /**
   * Ret
   */
  public static String getFormattedValue(String min) {
    if (min != null) {
      try {
        return getFormattedValue(Float.valueOf(min));
      } catch (Exception e) {
        xLogger.warn("Cannot format a non number {0}", min);
      }
    }
    return null;
  }
}
