import java.util.Map;

import com.google.auto.value.AutoValue;

/**
 * @author emcmanus@google.com (Éamonn McManus)
 */
public class PackagelessNestedValueType {
  @AutoValue
  public abstract static class Nested {
    abstract Map<Integer, String> numberNames();

    public static Nested create(Map<Integer, String> numberNames) {
      return new AutoValue_PackagelessNestedValueType_Nested(numberNames);
    }
  }
}
