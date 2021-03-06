package org.mongodb.morphia.issue50;

import org.bson.codecs.configuration.CodecConfigurationException;
import org.junit.Test;
import org.mongodb.morphia.TestBase;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.testutil.TestEntity;

public class TestIdTwice extends TestBase {

    @Test(expected = CodecConfigurationException.class)
    public final void shouldThrowExceptionIfThereIsMoreThanOneId() {
        getMapper().map(A.class);
    }

    public static class A extends TestEntity {
        @Id
        private String extraId;
        @Id
        private String broken;
    }

}
