/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.dmn.engine.impl.el;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.camunda.bpm.dmn.engine.impl.DmnEngineLogger;

/**
 * Detaches the result of a script evaluation from the script engine that
 * produced it, so that the result can still be used after the engine has been
 * closed.
 * <p>
 * GraalJS hands JavaScript objects and arrays to Java as views
 * ({@code com.oracle.truffle.polyglot.PolyglotMap} / {@code PolyglotList}) on
 * the polyglot Context the script ran in. Such a view cannot be read anymore
 * once the Context is closed. This class copies the views into plain Java
 * collections while the Context is still open:
 * </p>
 * <ul>
 * <li>a JavaScript object becomes a {@link LinkedHashMap} of its enumerable
 * properties, a JavaScript {@code Map} one of its entries</li>
 * <li>a JavaScript array (or typed array) becomes an {@link ArrayList}</li>
 * <li>a JavaScript {@code Set} becomes a {@link LinkedHashSet}</li>
 * <li>a JavaScript {@code Date} becomes a {@link Date}</li>
 * <li>nested values are copied recursively; an object that is referenced
 * more than once, also by itself, is copied once</li>
 * </ul>
 * <p>
 * Everything that is not a polyglot view is returned as is: {@code null},
 * numbers, strings, booleans and Java objects, including Java collections
 * (their content is not inspected).
 * </p>
 * <p>
 * Values that have no representation outside of the Context are returned as
 * is as well, and fail with an {@link IllegalStateException} of GraalVM when
 * they are used after the engine has been closed: functions, symbols and
 * objects that expose no properties and are not plain objects (e.g.
 * {@code RegExp}, {@code Promise}). The evaluation itself does not
 * fail because of them, since the result of a script is often not used at all.
 * </p>
 * <p>
 * The GraalVM classes are only accessed by name and reflection, so that this
 * module does not depend on GraalVM.
 * </p>
 */
public class ScriptValueDetacher {

  protected static final DmnEngineLogger LOG = DmnEngineLogger.ENGINE_LOGGER;

  /**
   * The packages of the views and values that GraalVM hands out for objects of
   * a guest language. Java objects passed through a script are never wrapped
   * and keep their own class.
   */
  protected static final String[] POLYGLOT_PACKAGES = { "com.oracle.truffle.polyglot.", "org.graalvm.polyglot." };

  protected static final String POLYGLOT_VALUE_CLASS = "org.graalvm.polyglot.Value";

  /** the name GraalJS reports as type of plain JavaScript objects */
  protected static final String PLAIN_OBJECT_TYPE = "Object";

  /** the name GraalJS reports as type of JavaScript sets */
  protected static final String SET_TYPE = "Set";

  /**
   * @param value the result of a script evaluation; the script engine that
   *   produced it must not be closed yet
   * @return the value itself or a copy of it that does not depend on the
   *   script engine
   */
  public static Object detach(Object value) {
    if (!isPolyglotValue(value)) {
      return value;
    }
    try {
      return detach(value, new HashMap<Object, Object>());
    } catch (RuntimeException | LinkageError | StackOverflowError e) {
      // the result is possibly not used at all, do not fail the evaluation
      LOG.logDetachingScriptValueFailed(e);
      return value;
    }
  }

  protected static Object detach(Object value, Map<Object, Object> detachedValues) {
    if (!isPolyglotValue(value)) {
      return value;
    }
    // polyglot views are equal if they refer to the same guest object
    if (detachedValues.containsKey(value)) {
      return detachedValues.get(value);
    }
    if (value instanceof Function) {
      // a function cannot be executed without its Context
      return value;
    }
    if (value instanceof List) {
      List<?> source = (List<?>) value;
      List<Object> copy = new ArrayList<>(source.size());
      detachedValues.put(value, copy);
      for (Object element : source) {
        copy.add(detach(element, detachedValues));
      }
      return copy;
    }
    if (value instanceof Map) {
      Object polyglotValue = asPolyglotValue(value);
      if (Boolean.TRUE.equals(invoke(polyglotValue, "isInstant"))) {
        return Date.from((Instant) invoke(polyglotValue, "asInstant"));
      }
      Map<?, ?> source = (Map<?, ?>) value;
      if (source.isEmpty() && isSet(polyglotValue)) {
        Set<Object> copy = new LinkedHashSet<>();
        detachedValues.put(value, copy);
        Object iterator = invoke(polyglotValue, "getIterator");
        while (Boolean.TRUE.equals(invoke(iterator, "hasIteratorNextElement"))) {
          Object element = invoke(iterator, "getIteratorNextElement");
          copy.add(detach(asObject(element), detachedValues));
        }
        return copy;
      }
      if (source.isEmpty() && !isPlainObject(polyglotValue)) {
        // an object that does not expose its state as properties
        return value;
      }
      Map<Object, Object> copy = new LinkedHashMap<>();
      detachedValues.put(value, copy);
      for (Map.Entry<?, ?> entry : source.entrySet()) {
        copy.put(detach(entry.getKey(), detachedValues), detach(entry.getValue(), detachedValues));
      }
      return copy;
    }
    return value;
  }

  /**
   * @param value a value that was returned by {@link #detach(Object)}
   * @return true if the value can be used after the script engine has been
   *   closed: neither the value itself nor, in case of a collection or map
   *   that was created by {@link #detach(Object)}, a nested value depends on
   *   the script engine
   */
  public static boolean isDetached(Object value) {
    return isDetached(value, Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>()));
  }

  protected static boolean isDetached(Object value, Set<Object> visitedValues) {
    if (isPolyglotValue(value)) {
      return false;
    }
    if (isCopy(value) && visitedValues.add(value)) {
      if (value instanceof Map) {
        Map<?, ?> map = (Map<?, ?>) value;
        return isDetached(map.keySet(), visitedValues) && isDetached(map.values(), visitedValues);
      }
      return isDetached((Collection<?>) value, visitedValues);
    }
    return true;
  }

  protected static boolean isDetached(Collection<?> values, Set<Object> visitedValues) {
    for (Object value : values) {
      if (!isDetached(value, visitedValues)) {
        return false;
      }
    }
    return true;
  }

  /**
   * @return true if the value is a collection or map of the kind that
   *   {@link #detach(Object)} creates
   */
  protected static boolean isCopy(Object value) {
    if (value != null) {
      Class<?> type = value.getClass();
      return type == ArrayList.class || type == LinkedHashMap.class || type == LinkedHashSet.class;
    }
    return false;
  }

  protected static boolean isPolyglotValue(Object value) {
    if (value != null) {
      String className = value.getClass().getName();
      for (String polyglotPackage : POLYGLOT_PACKAGES) {
        if (className.startsWith(polyglotPackage)) {
          return true;
        }
      }
    }
    return false;
  }

  protected static boolean isPlainObject(Object polyglotValue) {
    Object metaObject = invoke(polyglotValue, "getMetaObject");
    return metaObject == null || PLAIN_OBJECT_TYPE.equals(invoke(metaObject, "getMetaSimpleName"));
  }

  protected static boolean isSet(Object polyglotValue) {
    if (!Boolean.TRUE.equals(invoke(polyglotValue, "hasIterator"))) {
      return false;
    }
    Object metaObject = invoke(polyglotValue, "getMetaObject");
    return metaObject != null && SET_TYPE.equals(invoke(metaObject, "getMetaSimpleName"));
  }

  /**
   * @return the given {@code org.graalvm.polyglot.Value} the way a script
   *   engine returns it: as Java value or as view on a guest object
   */
  protected static Object asObject(Object polyglotValue) {
    try {
      return polyglotValue.getClass().getMethod("as", Class.class).invoke(polyglotValue, Object.class);
    } catch (ReflectiveOperationException e) {
      throw unwrap(e);
    }
  }

  /**
   * @return the {@code org.graalvm.polyglot.Value} behind the given view
   */
  protected static Object asPolyglotValue(Object value) {
    try {
      Class<?> valueClass = Class.forName(POLYGLOT_VALUE_CLASS, true, value.getClass().getClassLoader());
      return valueClass.getMethod("asValue", Object.class).invoke(null, value);
    } catch (ReflectiveOperationException e) {
      throw unwrap(e);
    }
  }

  protected static Object invoke(Object polyglotValue, String methodName) {
    try {
      return polyglotValue.getClass().getMethod(methodName).invoke(polyglotValue);
    } catch (ReflectiveOperationException e) {
      throw unwrap(e);
    }
  }

  protected static RuntimeException unwrap(ReflectiveOperationException e) {
    Throwable cause = e instanceof InvocationTargetException ? e.getCause() : e;
    if (cause instanceof RuntimeException) {
      return (RuntimeException) cause;
    }
    return new IllegalStateException(cause);
  }

}
