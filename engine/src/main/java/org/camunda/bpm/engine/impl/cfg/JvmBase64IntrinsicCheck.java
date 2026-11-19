/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.engine.impl.cfg;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * <p>Warns on startup when the process engine runs on a Java runtime that is
 * exposed to CVE-2026-114938 ("Java4Shell"): the x86-64 Base64 intrinsic of
 * HotSpot writes out of bounds for certain padded inputs on CPUs with AVX-512
 * VBMI.</p>
 *
 * <p>The runtime counts as exposed when all of the following hold:</p>
 * <ul>
 *   <li>its version is older than the fixed build of its release line
 *   (17.0.21.1, 21.0.13.1, 25.0.5.1 or 27.0.1.1); other release lines are not
 *   judged;</li>
 *   <li>it runs on x86-64;</li>
 *   <li>the Base64 intrinsic is not switched off with
 *   <code>-XX:-UseBASE64Intrinsics</code>, as far as the runtime reports it.</li>
 * </ul>
 *
 * <p>The check only logs a warning and never stops the engine: whether the CPU
 * supports AVX-512 VBMI is not visible from Java, and vendors number their
 * builds differently. It can be switched off by setting the system property
 * {@value #CHECK_ENABLED_SYSTEM_PROPERTY} to <code>false</code>.</p>
 */
public class JvmBase64IntrinsicCheck {

  /** system property to switch the check off */
  public static final String CHECK_ENABLED_SYSTEM_PROPERTY = "fasti.engine.jvm-check.enabled";

  public static final String CVE_ID = "CVE-2026-114938";

  protected static final EngineLibraryLogger LOG = EngineLibraryLogger.ENGINE_LIBRARY_LOGGER;

  /** fixed builds: feature, update, patch */
  protected static final int[][] FIXED_BUILDS = {
      {17, 21, 1},
      {21, 13, 1},
      {25, 5, 1},
      {27, 1, 1}
  };

  public static void check() {
    if ("false".equalsIgnoreCase(System.getProperty(CHECK_ENABLED_SYSTEM_PROPERTY))) {
      return;
    }
    try {
      Runtime.Version version = Runtime.version();
      String arch = System.getProperty("os.arch", "");
      Boolean intrinsic = readBase64IntrinsicFlag();
      if (isExposed(version, arch, intrinsic)) {
        LOG.warnJvmExposedToBase64Flaw(CVE_ID, version.toString(), fixedBuildFor(version.feature()));
      }
    } catch (RuntimeException e) {
      // the check must never keep the engine from starting
      LOG.debugCouldNotCheckJvm(e);
    }
  }

  /**
   * @param intrinsic whether the Base64 intrinsic is on, or <code>null</code> if unknown
   */
  public static boolean isExposed(Runtime.Version version, String arch, Boolean intrinsic) {
    if (!isX86_64(arch)) {
      return false;
    }
    if (Boolean.FALSE.equals(intrinsic)) {
      return false;
    }
    return isOlderThanFixedBuild(version);
  }

  public static boolean isOlderThanFixedBuild(Runtime.Version version) {
    for (int[] fixed : FIXED_BUILDS) {
      if (version.feature() == fixed[0]) {
        if (version.update() != fixed[1]) {
          return version.update() < fixed[1];
        }
        return version.patch() < fixed[2];
      }
    }
    return false;
  }

  protected static String fixedBuildFor(int feature) {
    for (int[] fixed : FIXED_BUILDS) {
      if (fixed[0] == feature) {
        return fixed[0] + ".0." + fixed[1] + "." + fixed[2];
      }
    }
    return "?";
  }

  protected static boolean isX86_64(String arch) {
    String a = arch.toLowerCase(Locale.ROOT);
    return a.equals("amd64") || a.equals("x86_64");
  }

  /**
   * Reads the VM option through the HotSpot diagnostic bean by reflection, so
   * that runtimes without the <code>jdk.management</code> module still work.
   *
   * @return the value of <code>UseBASE64Intrinsics</code>, or <code>null</code> if unknown
   */
  protected static Boolean readBase64IntrinsicFlag() {
    try {
      Class<?> beanClass = Class.forName("com.sun.management.HotSpotDiagnosticMXBean");
      Object bean = ManagementFactory.getPlatformMXBean(beanClass.asSubclass(java.lang.management.PlatformManagedObject.class));
      if (bean == null) {
        return null;
      }
      Method getVmOption = beanClass.getMethod("getVMOption", String.class);
      Object option = getVmOption.invoke(bean, "UseBASE64Intrinsics");
      Object value = option.getClass().getMethod("getValue").invoke(option);
      return Boolean.valueOf(String.valueOf(value));
    } catch (Exception | LinkageError e) {
      return null;
    }
  }

}
