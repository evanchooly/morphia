package org.mongodb.morphia.callbacks;


import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import org.mongodb.morphia.EntityInterceptor;
import org.mongodb.morphia.TestBase;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.PrePersist;
import org.mongodb.morphia.mapping.Mapper;

public class TestEntityInterceptorMoment extends TestBase {

    @Test
    public void testGlobalEntityInterceptorWorksAfterEntityCallback() {
        getMapper().map(E.class);
        getMapper().addInterceptor(new Interceptor());

        getDatastore().save(new E());
    }

    static class E {
        @Id
        private final ObjectId id = new ObjectId();

        private boolean called;

        @PrePersist
        void entityCallback() {
            called = true;
        }
    }

    public static class Interceptor implements EntityInterceptor {
        @Override
        public void prePersist(final Object ent, final Document document, final Mapper mapper) {
            Assert.assertTrue(((E) ent).called);
        }
    }
}
