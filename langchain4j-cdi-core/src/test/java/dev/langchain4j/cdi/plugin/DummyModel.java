package dev.langchain4j.cdi.plugin;

public class DummyModel extends DummyBaseModel {
    final String param1;

    DummyModel(String apiKey, Integer timeout, DummyInjected dummyInjected, String param1) {
        super(apiKey, timeout, dummyInjected);
        this.param1 = param1;
    }

    public static DummyModelBuilder builder() {
        return new DummyModelBuilder();
    }

    public static class DummyModelBuilder extends Builder<DummyModel, DummyModel.DummyModelBuilder> {
        public String param1;

        public DummyModelBuilder param1(String v) {
            this.param1 = v;
            return this;
        }

        public DummyModel build() {
            return new DummyModel(apiKey, timeout, dummyInjected, param1);
        }
    }
}
