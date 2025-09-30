package dev.langchain4j.cdi.plugin;

import java.util.List;
import java.util.Set;

public class DummyAll {

    final List<ToInjectAll> toInjectAll;
    final Set<ToInjectAllParameterized<String>> toInjectAllParameterized;

    public DummyAll(List<ToInjectAll> toInjectAll, Set<ToInjectAllParameterized<String>> toInjectAllParameterized) {
        this.toInjectAll = toInjectAll;
        this.toInjectAllParameterized = toInjectAllParameterized;
    }

    public static Builder builder() {
        return new Builder();
    }

    protected static class Builder {
        // fields that mirror expected property names (after dashToCamel conversion)
        public List<ToInjectAll> toInjectAll;
        public Set<ToInjectAllParameterized<String>> toInjectAllParameterized;

        public Builder toInjectAll(List<ToInjectAll> v) {
            this.toInjectAll = v;
            return this;
        }

        public Builder toInjectAllParameterized(Set<ToInjectAllParameterized<String>> v) {
            this.toInjectAllParameterized = v;
            return this;
        }

        public DummyAll build() {
            return new DummyAll(
                    toInjectAll,
                    toInjectAllParameterized
            );
        }
    }


    interface ToInjectAll {

    }

    interface ToInjectAllParameterized<T> {
    }


    public static class ToInjectAllBeanA implements ToInjectAll {
    }

    public static class ToInjectAllBeanB implements ToInjectAll {
    }

    public static class ToInjectAllParameterizedBeanA implements ToInjectAllParameterized<String> {
    }

    public static class ToInjectAllParameterizedBeanB implements ToInjectAllParameterized<String> {
    }

}
