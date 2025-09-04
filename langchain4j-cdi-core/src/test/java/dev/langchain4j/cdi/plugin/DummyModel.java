package dev.langchain4j.cdi.plugin;

import java.util.List;

public class DummyModel extends DummyBaseModel {
    final String param1;
    final DummyEnum dummyEnum;
    final List<DummyEnum> dummyEnumList;

    DummyModel(String apiKey, Integer timeout, DummyInjected dummyInjected, String param1, DummyEnum dummyEnum,
            List<DummyEnum> dummyEnumList) {
        super(apiKey, timeout, dummyInjected);
        this.param1 = param1;
        this.dummyEnum = dummyEnum;
        this.dummyEnumList = dummyEnumList;
    }

    public static DummyModelBuilder builder() {
        return new DummyModelBuilder();
    }

    public static class DummyModelBuilder extends Builder<DummyModel, DummyModel.DummyModelBuilder> {
        public String param1;
        public DummyEnum dummyEnum;
        public List<DummyEnum> dummyEnumList;

        public DummyModelBuilder param1(String v) {
            this.param1 = v;
            return this;
        }

        public DummyModelBuilder dummyEnum(DummyEnum v) {
            this.dummyEnum = v;
            return this;
        }

        public DummyModelBuilder dummyEnumList(java.util.List<DummyEnum> v) {
            this.dummyEnumList = v;
            return this;
        }

        public DummyModel build() {
            return new DummyModel(apiKey, timeout, dummyInjected, param1, dummyEnum, dummyEnumList);
        }
    }
}
