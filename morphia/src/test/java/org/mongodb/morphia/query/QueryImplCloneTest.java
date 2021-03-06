package org.mongodb.morphia.query;


import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import org.mongodb.morphia.TestBase;
import org.mongodb.morphia.annotations.Id;

public class QueryImplCloneTest extends TestBase {

    @Test
    public void testQueryClone() {
        final Query q = getDatastore().find(E1.class)
                                      .field("i")
                                      .equal(5)
                                      .filter("a", "value_a")
                                      .filter("b", "value_b")
                                      .order("a");
        q.disableValidation().filter("foo", "bar");
        Assert.assertEquals(q, q.cloneQuery());
    }

    private static class E1 {
        @Id
        private ObjectId id;

        private String a;
        private String b;
        private int i;
        private E2 e2 = new E2();
    }

    private static class E2 {
        private String foo;
    }
}
