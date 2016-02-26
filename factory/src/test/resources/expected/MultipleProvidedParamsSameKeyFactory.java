package tests;

import com.google.auto.factory.internal.Preconditions;

import javax.annotation.Generated;
import javax.inject.Inject;
import javax.inject.Provider;

@Generated(
  value = "com.google.auto.factory.processor.AutoFactoryProcessor",
  comments = "https://github.com/google/auto/tree/master/factory"
)
final class MultipleProvidedParamsSameKeyFactory {
  private final Provider<String> java_lang_StringProvider;

  @Inject
  MultipleProvidedParamsSameKeyFactory(Provider<String> java_lang_StringProvider) {
    this.java_lang_StringProvider = java_lang_StringProvider;
  }

  MultipleProvidedParamsSameKey create() {
    return new MultipleProvidedParamsSameKey(
        Preconditions.checkNotNull(java_lang_StringProvider.get()),
        Preconditions.checkNotNull(java_lang_StringProvider.get()),
        java_lang_StringProvider.get());
  }
}
