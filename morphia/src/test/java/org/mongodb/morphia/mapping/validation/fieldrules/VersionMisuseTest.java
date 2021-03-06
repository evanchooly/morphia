package org.mongodb.morphia.mapping.validation.fieldrules;


import org.junit.Test;
import org.mongodb.morphia.TestBase;
import org.mongodb.morphia.annotations.Version;
import org.mongodb.morphia.mapping.validation.ConstraintViolationException;
import org.mongodb.morphia.testutil.TestEntity;

public class VersionMisuseTest extends TestBase {

    @Test(expected = ConstraintViolationException.class)
    public void testInitedPrimitive() {
        getMapper().map(Fail1.class);
    }

    @Test(expected = ConstraintViolationException.class)
    public void testInitedWrapper() {
        getMapper().map(Fail2.class);
    }

    @Test
    public void testPrimitive() {
        getMapper().map(OK1.class);
    }

    @Test
    public void testWrapper() {
        getMapper().map(OK2.class);
    }

    public static class Fail1 extends TestEntity {
        @Version
        private long hubba = 1;
    }

    public static class Fail2 extends TestEntity {
        @Version
        private Long hubba = 1L;
    }

    public static class OK1 extends TestEntity {
        @Version
        private long hubba;
    }

    public static class OK2 extends TestEntity {
        @Version
        private Long hubba;
    }
}
