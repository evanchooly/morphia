/*
 * Copyright (C) 2010 Olafur Gauti Gudmundsson
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package org.mongodb.morphia;

import com.mongodb.WriteConcern;
import com.mongodb.client.model.UpdateOptions;
import org.junit.Assert;
import org.junit.Test;
import org.mongodb.morphia.entities.version.AbstractVersionedBase;
import org.mongodb.morphia.entities.version.Versioned;
import org.mongodb.morphia.entities.version.VersionedChildEntity;
import org.mongodb.morphia.mapping.MappedClass;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.List;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class TestVersionAnnotation extends TestBase {

    @Test
    public void testBulkUpdate() {
        final Datastore datastore = getDatastore();

        Versioned entity = new Versioned();
        entity.setName("Value 1");

        datastore.save(entity);

        entity = datastore.get(Versioned.class, entity.getId());
        Assert.assertEquals("Value 1", entity.getName());
        Assert.assertEquals(1, entity.getVersion().longValue());

        entity.setName("Value 2");
        datastore.save(entity);

        entity = datastore.get(Versioned.class, entity.getId());
        Assert.assertEquals("Value 2", entity.getName());
        Assert.assertEquals(2, entity.getVersion().longValue());

        Query<Versioned> query = datastore.find(Versioned.class);
        query.filter("id", entity.getId());
        UpdateOperations<Versioned> ops = datastore.createUpdateOperations(Versioned.class);
        ops.set("name", "Value 3");
        datastore.updateMany(query, ops);

        entity = datastore.get(Versioned.class, entity.getId());
        Assert.assertEquals("Value 3", entity.getName());
        Assert.assertEquals(3, entity.getVersion().longValue());
    }

    @Test
    public void testCanMapAPackageContainingAVersionedAbstractBaseClass() {
        getMapper().mapPackage("org.mongodb.morphia.entities.version");

        Collection<MappedClass> mappedClasses = getMapper().getMappedClasses();
        assertThat(mappedClasses.size(), is(3));
        List<Class<?>> list = new ArrayList<>();
        for (MappedClass mappedClass : mappedClasses) {
            list.add(mappedClass.getClazz());
        }
        assertTrue(list.contains(VersionedChildEntity.class));
        assertTrue(list.contains(AbstractVersionedBase.class));
        assertTrue(list.contains(Versioned.class));
    }

    @Test
    public void testCanMapAnEntityWithAnAbstractVersionedParent() {
        getMapper().map(VersionedChildEntity.class);

        Collection<MappedClass> mappedClasses = getMapper().getMappedClasses();
        assertThat(mappedClasses.size(), is(2));
        List<Class<?>> list = new ArrayList<>();
        for (MappedClass mappedClass : mappedClasses) {
            list.add(mappedClass.getClazz());
        }
        assertTrue(list.contains(VersionedChildEntity.class));
        assertTrue(list.contains(AbstractVersionedBase.class));
    }

    @Test
    public void testEntityUpdate() {
        final Datastore datastore = getDatastore();

        Versioned entity = new Versioned();
        entity.setName("Value 1");

        datastore.save(entity);

        entity = datastore.get(Versioned.class, entity.getId());
        Assert.assertEquals("Value 1", entity.getName());
        Assert.assertEquals(1, entity.getVersion().longValue());

        entity.setName("Value 2");
        datastore.save(entity);

        entity = datastore.get(Versioned.class, entity.getId());
        Assert.assertEquals("Value 2", entity.getName());
        Assert.assertEquals(2, entity.getVersion().longValue());

        UpdateOperations<Versioned> ops = datastore.createUpdateOperations(Versioned.class);
        ops.set("name", "Value 3");
        Assert.assertEquals(1, datastore.update(entity, ops).getModifiedCount());
        Assert.assertEquals(0, datastore.update(entity, ops).getModifiedCount());

        entity = datastore.get(Versioned.class, entity.getId());
        Assert.assertEquals("Value 3", entity.getName());
        Assert.assertEquals(3, entity.getVersion().longValue());

        ops = datastore.createUpdateOperations(Versioned.class);
        ops.set("name", "Value 4");
        datastore.update(datastore.getKey(entity), ops, new UpdateOptions(), WriteConcern.ACKNOWLEDGED);

        entity = datastore.get(Versioned.class, entity.getId());
        Assert.assertEquals("Value 4", entity.getName());
        Assert.assertEquals(4, entity.getVersion().longValue());
    }

    @Test
    public void testIncVersionNotOverridingOtherInc() {
        final Versioned version1 = new Versioned();
        version1.setCount(0);
        getDatastore().save(version1);

        assertEquals(new Long(1), version1.getVersion());
        assertEquals(0, version1.getCount());

        Query<Versioned> query = getDatastore().find(Versioned.class);
        query.field("_id").equal(version1.getId());
        UpdateOperations<Versioned> up = getDatastore().createUpdateOperations(Versioned.class).inc("count");

        getDatastore().updateOne(query, up, new UpdateOptions().upsert(true), getDatastore().getDefaultWriteConcern());

        final Versioned version2 = getDatastore().get(Versioned.class, version1.getId());

        assertEquals(new Long(2), version2.getVersion());
        assertEquals(1, version2.getCount());
    }

    @Test(expected = ConcurrentModificationException.class)
    public void testThrowsExceptionWhenTryingToSaveAnOldVersion() {
        final Versioned version1 = new Versioned();
        getDatastore().save(version1);
        getDatastore().save(getDatastore().get(Versioned.class, version1.getId()));

        getDatastore().save(version1);
    }

    @Test
    public void testUpdatesToVersionedFileAreReflectedInTheDatastore() {
        final Versioned version1 = new Versioned();
        version1.setName("foo");

        this.getDatastore().save(version1);

        final Versioned version1Updated = getDatastore().get(Versioned.class, version1.getId());
        version1Updated.setName("bar");

        this.getDatastore().merge(version1Updated);

        final Versioned versionedEntityFromDs = this.getDatastore().get(Versioned.class, version1.getId());
        assertEquals(version1Updated.getName(), versionedEntityFromDs.getName());
    }

    @Test
    public void testVersionNumbersIncrementWithEachSave() {
        final Versioned version1 = new Versioned();
        getDatastore().save(version1);
        assertEquals(new Long(1), version1.getVersion());

        final Versioned version2 = getDatastore().get(Versioned.class, version1.getId());
        getDatastore().save(version2);
        assertEquals(new Long(2), version2.getVersion());
    }

    @Test
    public void testVersionedInserts() {
        List<Versioned> list = asList(new Versioned(), new Versioned(), new Versioned(), new Versioned(), new Versioned());
        getAds().insertMany(list);
        for (Versioned versioned : list) {
            assertNotNull(versioned.getVersion());
        }
    }

    @Test
    public void testVersionedUpsert() {
        final Datastore datastore = getDatastore();

        Versioned entity = new Versioned();
        entity.setName("Value 1");

        Query<Versioned> query = datastore.find(Versioned.class);
        query.filter("name", "Value 1");
        UpdateOperations<Versioned> ops = datastore.createUpdateOperations(Versioned.class);
        ops.set("name", "Value 3");
        datastore.updateOne(query, ops, new UpdateOptions().upsert(true), getDatastore().getDefaultWriteConcern());

        entity = datastore.find(Versioned.class).get();
        Assert.assertEquals("Value 3", entity.getName());
        Assert.assertEquals(1, entity.getVersion().longValue());
    }

}
