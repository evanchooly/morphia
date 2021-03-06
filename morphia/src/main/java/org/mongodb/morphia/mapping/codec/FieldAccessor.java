package org.mongodb.morphia.mapping.codec;

import org.bson.codecs.pojo.PropertyAccessor;
import org.mongodb.morphia.mapping.MappingException;

import java.lang.reflect.Field;

public class FieldAccessor implements PropertyAccessor {
    private final Field field;

    public FieldAccessor(final Field field) {
        this.field = field;
    }

    @Override
    public Object get(final Object instance) {
        try {
            return field.get(instance);
        } catch (IllegalAccessException e) {
            throw new MappingException(e.getMessage(), e);
        }
    }

    @Override
    public void set(final Object instance, final Object value) {
        try {
            field.set(instance, value);
        } catch (IllegalAccessException e) {
            throw new MappingException(e.getMessage(), e);
        }
    }
}
