package org.mongodb.morphia.issue172;


import org.bson.types.ObjectId;
import org.junit.Ignore;
import org.junit.Test;
import org.mongodb.morphia.TestBase;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.entities.SimpleEnum;


public class NameValuePairTest extends TestBase {

    @Test
    @Ignore("add back when TypeLiteral support is in; issue 175")
    public void testNameValuePairWithDoubleIn() {
        getMapper().map(NameValuePairContainer.class);
        final NameValuePairContainer container = new NameValuePairContainer();
        container.pair = new NameValuePair<>(SimpleEnum.FOO, 1.2d);
        getDatastore().save(container);

        getDatastore().get(container);
    }

    @Entity
    private static class NameValuePairContainer {
        @Id
        private ObjectId id;
        private NameValuePair<SimpleEnum, Double> pair;
    }

    private static class NameValuePair<T1 extends Enum<?>, T2> {
        private final T2 value;
        private final T1 name;

        NameValuePair(final T1 name, final T2 value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            result = prime * result + ((value == null) ? 0 : value.hashCode());
            return result;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final NameValuePair other = (NameValuePair) obj;
            if (name == null) {
                if (other.name != null) {
                    return false;
                }
            } else if (!name.equals(other.name)) {
                return false;
            }
            if (value == null) {
                if (other.value != null) {
                    return false;
                }
            } else if (!value.equals(other.value)) {
                return false;
            }
            return true;
        }

    }

}
