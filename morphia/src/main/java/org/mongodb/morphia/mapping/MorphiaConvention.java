package org.mongodb.morphia.mapping;

import org.bson.codecs.pojo.ClassModelBuilder;
import org.bson.codecs.pojo.Convention;
import org.bson.codecs.pojo.FieldModelBuilder;
import org.bson.codecs.pojo.PropertyAccessor;
import org.bson.codecs.pojo.PropertyMetadata;
import org.bson.codecs.pojo.PropertyModelBuilder;
import org.bson.codecs.pojo.TypeData;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Handler;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Property;
import org.mongodb.morphia.annotations.Reference;
import org.mongodb.morphia.annotations.Serialized;
import org.mongodb.morphia.annotations.Transient;
import org.mongodb.morphia.annotations.Version;
import org.mongodb.morphia.mapping.codec.ArrayFieldAccessor;
import org.mongodb.morphia.mapping.codec.FieldAccessor;
import org.mongodb.morphia.mapping.codec.MorphiaPropertySerialization;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;

import static java.lang.reflect.Modifier.isAbstract;
import static java.lang.reflect.Modifier.isStatic;
import static org.bson.codecs.pojo.PojoBuilderHelper.createPropertyModelBuilder;

@SuppressWarnings("unchecked")
public class MorphiaConvention implements Convention {
    private Datastore datastore;
    private MapperOptions options;

    MorphiaConvention(final Datastore datastore, final MapperOptions options) {
        this.datastore = datastore;
        this.options = options;
    }

    @Override
    public void apply(final ClassModelBuilder<?> classModelBuilder) {

        classModelBuilder.discriminator(classModelBuilder.getType().getName())
                         .discriminatorKey("className");

        final Entity entity = getAnnotation(classModelBuilder, Entity.class);
        if(entity != null) {
            classModelBuilder.enableDiscriminator(entity.useDiscriminator());
        } else if(classModelBuilder.hasAnnotation(Embedded.class)) {
            classModelBuilder.enableDiscriminator(getAnnotation(classModelBuilder, Embedded.class).useDiscriminator());
        } else {
            classModelBuilder.enableDiscriminator(true);
        }


        final List<String> names = classModelBuilder.getPropertyModelBuilders().stream()
                                                    .map(PropertyModelBuilder::getName)
                                                    .collect(Collectors.toList());
        for (final String name : names) {
            classModelBuilder.removeProperty(name);
        }

        final InstanceCreatorFactoryImpl creatorFactory = new InstanceCreatorFactoryImpl(datastore, classModelBuilder);
        Iterator<FieldModelBuilder<?>> iterator = classModelBuilder.getFieldModelBuilders().iterator();
        while (iterator.hasNext()) {
            final FieldModelBuilder<?> builder = iterator.next();
            final Field field = builder.getField();

            PropertyModelBuilder<?> property = classModelBuilder.getProperty(builder.getName());

            if (isStatic(field.getModifiers()) || isTransient(builder)) {
                iterator.remove();
                if (property != null) {
                    classModelBuilder.removeProperty(property.getName());
                }
            } else {
                if (property == null) {
                    final PropertyMetadata<?> propertyMetadata = new PropertyMetadata<>(builder.getName(),
                        classModelBuilder.getType().getName(), builder.getTypeData())
                                                                     .field(field);
                    property = createPropertyModelBuilder(propertyMetadata);
                    classModelBuilder.addProperty(property);
                }

                property.typeData((TypeData) builder.getTypeData());

                if (builder.hasAnnotation(Id.class)) {
                    classModelBuilder.idPropertyName(property.getReadName());
                }

                final String mappedName = getMappedFieldName(builder);
                property.readName(mappedName)
                        .writeName(mappedName)
                        .propertySerialization(new MorphiaPropertySerialization(options, builder))
                        .readAnnotations(builder.getAnnotations())
                        .writeAnnotations(builder.getAnnotations())
                        .propertyAccessor(getPropertyAccessor(creatorFactory, field, property));

                if (isNotConcrete(property.getTypeData())) {
                    property.discriminatorEnabled(true);
                }
            }
        }

        classModelBuilder.instanceCreatorFactory(creatorFactory);
    }

    private boolean hasHandler(final PropertyModelBuilder builder) {
        final List<Annotation> readAnnotations = builder.getReadAnnotations();
        for (Annotation annotation : readAnnotations) {
            if(annotation.annotationType().getAnnotation(Handler.class) != null) {
                return true;
            }
        }
        return false;
    }

    private PropertyHandler getHandler(final PropertyModelBuilder builder, final Field field) {
        final List<Annotation> readAnnotations = builder.getReadAnnotations();
        for (Annotation annotation : readAnnotations) {
            final Handler handler = annotation.annotationType().getAnnotation(Handler.class);
            if(handler != null) {
                try {
                    return handler.value()
                                  .getDeclaredConstructor(Datastore.class, Field.class, String.class, TypeData.class)
                                  .newInstance(datastore, field, builder.getName(), builder.getTypeData());
                } catch (ReflectiveOperationException e) {
                    throw new MappingException(e.getMessage(), e);
                }
            }
        }
        return null;
    }


    private PropertyAccessor getPropertyAccessor(final InstanceCreatorFactoryImpl creatorFactory,
                                                 final Field field,
                                                 final PropertyModelBuilder<?> property) {

        if(hasHandler(property)) {
            creatorFactory.register(getHandler(property, field));
        }

        return field.getType().isArray() && !field.getType().getComponentType().equals(byte.class)
                          ? new ArrayFieldAccessor(property.getTypeData(), field)
                          : new FieldAccessor(field);
    }

    private <T extends Annotation> T getAnnotation(final ClassModelBuilder<?> classModelBuilder, final Class<T> klass) {
        final List<Annotation> annotations = classModelBuilder.getAnnotations();
        if (!annotations.isEmpty()) {
            final ListIterator<Annotation> iterator = annotations.listIterator(annotations.size());
            while (iterator.hasPrevious()) {
                final Annotation annotation = iterator.previous();
                if (klass.equals(annotation.annotationType())) {
                    return klass.cast(annotation);
                }
            }
        }

        return null;
    }

    private boolean isNotConcrete(final TypeData<?> typeData) {
        Class type;
        if(!typeData.getTypeParameters().isEmpty()) {
            type = typeData.getTypeParameters().get(typeData.getTypeParameters().size() - 1).getType();
        } else {
            type = typeData.getType();
        }

        return isNotConcrete(type);
    }

    private boolean isNotConcrete(final Class type) {
        Class componentType = type;
        if(type.isArray()) {
            componentType = type.getComponentType();
        }
        return componentType.isInterface() || isAbstract(componentType.getModifiers());
    }

    private static boolean isTransient(final FieldModelBuilder<?> field) {
        return field.hasAnnotation(Transient.class)
               || field.hasAnnotation(java.beans.Transient.class)
               || Modifier.isTransient(field.getTypeData().getType().getModifiers());
    }

    private static String getMappedFieldName(FieldModelBuilder<?> field) {
        if (field.hasAnnotation(Id.class)) {
            return Mapper.ID_KEY;
        } else if (field.hasAnnotation(Property.class)) {
            final Property mv = field.getAnnotation(Property.class);
            if (!mv.value().equals(Mapper.IGNORED_FIELDNAME)) {
                return mv.value();
            }
        } else if (field.hasAnnotation(Reference.class)) {
            final Reference mr = field.getAnnotation(Reference.class);
            if (!mr.value().equals(Mapper.IGNORED_FIELDNAME)) {
                return mr.value();
            }
        } else if (field.hasAnnotation(Embedded.class)) {
            final Embedded me = field.getAnnotation(Embedded.class);
            if (!me.value().equals(Mapper.IGNORED_FIELDNAME)) {
                return me.value();
            }
        } else if (field.hasAnnotation(Serialized.class)) {
            final Serialized me = field.getAnnotation(Serialized.class);
            if (!me.value().equals(Mapper.IGNORED_FIELDNAME)) {
                return me.value();
            }
        } else if (field.hasAnnotation(Version.class)) {
            final Version me = field.getAnnotation(Version.class);
            if (!me.value().equals(Mapper.IGNORED_FIELDNAME)) {
                return me.value();
            }
        }

        return field.getName();
    }

}
