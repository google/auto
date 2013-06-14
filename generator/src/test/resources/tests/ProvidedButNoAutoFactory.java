package tests;

import com.google.autofactory.Provided;

final class ProvidedButNoAutoFactory {
  ProvidedButNoAutoFactory(Object a, @Provided Object b) {}
}
