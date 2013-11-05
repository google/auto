import java.util.Map;

import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValues;

/**
 * @author emcmanus@google.com (Éamonn McManus)
 */
public class PackagelessNestedValueType {
  @AutoValue
  public abstract static class Nested {
    abstract Map<Integer, String> numberNames();

    public static Nested create(Map<Integer, String> numberNames) {
      return AutoValues.using(Factory.class).create(numberNames);
    }

    interface Factory {
      Nested create(Map<Integer, String> numberNames);
    }
  }
}
