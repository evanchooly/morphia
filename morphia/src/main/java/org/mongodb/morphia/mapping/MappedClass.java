/*
 * Copyright 2008-2016 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mongodb.morphia.mapping;


import org.bson.Document;
import org.bson.codecs.pojo.ClassModel;
import org.mongodb.morphia.EntityInterceptor;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.EntityListeners;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.PostLoad;
import org.mongodb.morphia.annotations.PostPersist;
import org.mongodb.morphia.annotations.PreLoad;
import org.mongodb.morphia.annotations.PrePersist;
import org.mongodb.morphia.annotations.Version;
import org.mongodb.morphia.logging.Logger;
import org.mongodb.morphia.logging.MorphiaLoggerFactory;
import org.mongodb.morphia.mapping.validation.MappingValidator;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.groupingBy;


/**
 * Represents a mapped class between the MongoDB document and the java POJO.
 * <p/>
 * This class will validate classes to make sure they meet the requirement for persistence.
 */
public class MappedClass {
    private static final Logger LOG = MorphiaLoggerFactory.get(MappedClass.class);
    /**
     * Annotations interesting for life-cycle events
     */
    private static final List<Class<? extends Annotation>> LIFECYCLE_ANNOTATIONS = asList(PrePersist.class,
        PreLoad.class,
        PostPersist.class,
        PostLoad.class);
    /**
     * Methods which are life-cycle events
     */
    private final Map<Class<? extends Annotation>, List<ClassMethodPair>> lifecycleMethods = new HashMap<>();
    /**
     * a list of the fields to map
     */
    private final List<MappedField> persistenceFields = new ArrayList<>();
    /**
     * the type we are mapping to/from
     */
    private final ClassModel<?> classModel;
    private final Class<?> type;
    private Map<Class<? extends Annotation>, List<Annotation>> annotations;
    /**
     * special fields representing the Key of the object
     */
    private MappedField idField;
    /**
     * special annotations representing the type the object
     */
    private Entity entityAn;
    private Embedded embeddedAn;
    private MapperOptions mapperOptions;
    private MappedClass superClass;
    private List<MappedClass> interfaces = new ArrayList<>();

    /**
     * Creates a MappedClass instance
     *
     * @param classModel the ClassModel
     * @param mapper     the Mapper to use
     */
    public MappedClass(final ClassModel classModel, final Mapper mapper) {
        this.classModel = classModel;
        type = classModel.getType();
        mapperOptions = mapper.getOptions();

        if (LOG.isTraceEnabled()) {
            LOG.trace("Creating MappedClass for " + type);
        }

        if (!Modifier.isStatic(type.getModifiers()) && type.isMemberClass()) {
            throw new MappingException(format("Cannot use non-static inner class: %s. Please make static.", type));
        }
        discover(mapper);

        if (LOG.isDebugEnabled()) {
            LOG.debug("MappedClass done: " + toString());
        }
    }

    private static List<Method> getDeclaredAndInheritedMethods(final Class type) {
        if ((type == null) || (type == Object.class)) {
            return new ArrayList<>();
        }

        final List<Method> list = getDeclaredAndInheritedMethods(type.getSuperclass());
        for (final Method m : type.getDeclaredMethods()) {
            if (!Modifier.isStatic(m.getModifiers())) {
                list.add(m);
            }
        }

        return list;
    }

    /**
     * This is an internal method subject to change without notice.
     *
     * @return the parent class of this type if there is one null otherwise
     * @since 1.3
     */
    public MappedClass getSuperClass() {
        return superClass;
    }

    /**
     * @return true if the MappedClass is an interface
     */
    public boolean isInterface() {
        return type.isInterface();
    }

    /**
     * This is an internal method subject to change without notice.
     *
     * @return true if the MappedClass is abstract
     * @since 1.3
     */
    public boolean isAbstract() {
        return Modifier.isAbstract(type.getModifiers());
    }

    /**
     * Call the lifecycle methods
     *
     * @param event    the lifecycle annotation
     * @param entity   the entity to process
     * @param document the document to use
     * @param mapper   the Mapper to use
     */
    @SuppressWarnings("unchecked")
    public void callLifecycleMethods(final Class<? extends Annotation> event, final Object entity, final Document document,
                                     final Mapper mapper) {
        if (hasLifecycle(event) || mapper.hasInterceptors()) {
            final List<ClassMethodPair> methodPairs = lifecycleMethods.get(event);
            try {
                if (methodPairs != null) {
                    final HashMap<Class<?>, Object> toCall = new HashMap<>((int) (methodPairs.size() * 1.3));
                    for (final ClassMethodPair cm : methodPairs) {
                        toCall.put(cm.clazz, null);
                    }
                    for (final Class<?> c : toCall.keySet()) {
                        if (c != null) {
                            toCall.put(c, getOrCreateInstance(c, mapper));
                        }
                    }

                    for (final ClassMethodPair cm : methodPairs) {
                        final Method method = cm.method;
                        final Object inst = toCall.get(cm.clazz);
                        method.setAccessible(true);

                        if (LOG.isDebugEnabled()) {
                            LOG.debug(format("Calling lifecycle method(@%s %s) on %s", event.getSimpleName(), method, inst));
                        }

                        if (inst == null) {
                            if (method.getParameterTypes().length == 0) {
                                method.invoke(entity);
                            } else {
                                method.invoke(entity, document);
                            }
                        } else if (method.getParameterTypes().length == 0) {
                            method.invoke(inst);
                        } else if (method.getParameterTypes().length == 1) {
                            method.invoke(inst, entity);
                        } else {
                            method.invoke(inst, entity, document);
                        }
                    }
                }

            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
            callGlobalInterceptors(event, entity, document, mapper);
        }
    }

    public boolean hasLifecycle(Class<? extends Annotation> klass) {
        return lifecycleMethods.containsKey(klass);
    }

    private Object getOrCreateInstance(final Class<?> clazz, final Mapper mapper) {
        if (mapper.getInstanceCache().containsKey(clazz)) {
            return mapper.getInstanceCache().get(clazz);
        }

        final Object o = mapper.getOptions().getObjectFactory().createInstance(clazz);
        final Object nullO = mapper.getInstanceCache().put(clazz, o);
        if (nullO != null) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Race-condition, created duplicate class: " + clazz);
            }
        }

        return o;

    }

    private void callGlobalInterceptors(final Class<? extends Annotation> event, final Object entity, final Document document,
                                        final Mapper mapper) {
        for (final EntityInterceptor ei : mapper.getInterceptors()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Calling interceptor method " + event.getSimpleName() + " on " + ei);
            }

            if (event.equals(PreLoad.class)) {
                ei.preLoad(entity, document, mapper);
            } else if (event.equals(PostLoad.class)) {
                ei.postLoad(entity, document, mapper);
            } else if (event.equals(PrePersist.class)) {
                ei.prePersist(entity, document, mapper);
            } else if (event.equals(PostPersist.class)) {
                ei.postPersist(entity, document, mapper);
            }
        }
    }

    /**
     * Looks for an annotation of the type given
     *
     * @param clazz the type to search for
     * @return the instance if it was found, if more than one was found, the last one added
     */
    public <T> T getAnnotation(final Class<? extends Annotation> clazz) {
        final List<Annotation> found = annotations.get(clazz);
        return found == null || found.isEmpty() ? null : (T) found.get(found.size() - 1);
    }

    /**
     * Looks for an annotation in the annotations found on a class while mapping
     *
     * @param clazz the class to search for
     * @param <T>   the type of annotation to find
     * @return the instance if it was found, if more than one was found, the last one added
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getAnnotations(final Class<? extends Annotation> clazz) {
        return (List<T>) annotations.get(clazz);
    }

    /**
     * @return the clazz
     */
    public Class<?> getClazz() {
        return type;
    }

    /**
     * @return the collName
     */
    public String getCollectionName() {
        if (entityAn == null || entityAn.value().equals(Mapper.IGNORED_FIELDNAME)) {
            return mapperOptions.isUseLowerCaseCollectionNames() ? type.getSimpleName().toLowerCase() : type.getSimpleName();
        }
        return entityAn.value();
    }

    /**
     * @return the embeddedAn
     */
    public Embedded getEmbeddedAnnotation() {
        return embeddedAn;
    }

    /**
     * @return the entityAn
     */
    public Entity getEntityAnnotation() {
        return entityAn;
    }

    /**
     * @return the idField
     */
    public MappedField getIdField() {
        return idField;
    }

    /**
     * Returns the MappedField by the name that it will stored in mongodb as
     *
     * @param storedName the name to search for
     * @return true if that mapped field name is found
     */
    public MappedField getMappedField(final String storedName) {
        return persistenceFields.stream()
                                .filter(mappedField -> mappedField.getNameToStore().equals(storedName))
                                .findFirst()
                                .orElse(null);
    }

    /**
     * Returns MappedField for a given java field name on the this MappedClass
     *
     * @param name the Java field name to search for
     * @return the MappedField for the named Java field
     */
    public MappedField getMappedFieldByJavaField(final String name) {
        for (final MappedField mf : persistenceFields) {
            if (name.equals(mf.getJavaFieldName())) {
                return mf;
            }
        }

        return null;
    }

    /**
     * @return the ID field for the class
     */
    public MappedField getMappedIdField() {
        List<MappedField> fields = getFieldsAnnotatedWith(Id.class);
        if(fields.isEmpty()) {
            throw new MappingException(format("%s does not have an annotated ID field", classModel.getName()));
        }
        return fields.get(0);
    }

    /**
     * Returns fields annotated with the clazz
     *
     * @param clazz The Annotation to find.
     * @return the list of fields
     */
    public List<MappedField> getFieldsAnnotatedWith(final Class<? extends Annotation> clazz) {
        final List<MappedField> results = new ArrayList<>();
        for (final MappedField mf : persistenceFields) {
            if (mf.hasAnnotation(clazz)) {
                results.add(mf);
            }
        }
        return results;
    }

    /**
     * @return the ID field for the class
     */
    public MappedField getMappedVersionField() {
        List<MappedField> fields = getFieldsAnnotatedWith(Version.class);
        return fields.isEmpty() ? null : fields.get(0);
    }

    /**
     * @return the persistenceFields
     */
    public List<MappedField> getPersistenceFields() {
        return persistenceFields;
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final MappedClass that = (MappedClass) o;

        return type.equals(that.type);

    }

    @Override
    public String toString() {
        return format("%s[%s] : %s", getClazz().getSimpleName(), getCollectionName(), persistenceFields);
    }

    boolean isSubType(final MappedClass mc) {
        return mc.equals(superClass) || interfaces.contains(mc);
    }

    /**
     * Update mappings based on fields/annotations.
     */
    public void update() {
        embeddedAn = getAnnotation(Embedded.class);
        entityAn = getAnnotation(Entity.class);
        final List<MappedField> fields = getFieldsAnnotatedWith(Id.class);
        if (fields != null && !fields.isEmpty()) {
            idField = fields.get(0);
        }
    }

    /**
     * Validates this MappedClass
     *
     * @param mapper the Mapper to use for validation
     */
    @SuppressWarnings("deprecation")
    public void validate(final Mapper mapper) {
        new MappingValidator(mapper.getOptions().getObjectFactory()).validate(mapper, this);
    }

    /**
     * Discovers interesting (that we care about) things about the class.
     */
    private void discover(final Mapper mapper) {
        this.annotations = classModel.getAnnotations().stream()
                                     .collect(groupingBy(
                                         annotation -> (Class<? extends Annotation>) annotation.annotationType()));


        Class<?> superclass = type.getSuperclass();
        if (superclass != null && !superclass.equals(Object.class)) {
            superClass = mapper.getMappedClass(superclass);
        }

        for (Class<?> aClass : type.getInterfaces()) {
                final MappedClass mappedClass = mapper.getMappedClass(aClass);
                if (mappedClass != null) {
                    interfaces.add(mappedClass);
                }
        }

        final List<Class<?>> lifecycleClasses = new ArrayList<>();
        lifecycleClasses.add(type);

        final EntityListeners entityLisAnn = getAnnotation(EntityListeners.class);
        if (entityLisAnn != null && entityLisAnn.value().length != 0) {
            Collections.addAll(lifecycleClasses, entityLisAnn.value());
        }

        for (final Class<?> cls : lifecycleClasses) {
            for (final Method method : getDeclaredAndInheritedMethods(cls)) {
                for (final Class<? extends Annotation> annotationClass : LIFECYCLE_ANNOTATIONS) {
                    if (method.isAnnotationPresent(annotationClass)) {
                        lifecycleMethods.computeIfAbsent(annotationClass, c -> new ArrayList<>())
                                        .add(new ClassMethodPair(cls.equals(type) ? null : cls, method));
                    }
                }
            }
        }

        discoverFields(this);

        update();
    }

    private void discoverFields(final MappedClass mappedClass) {
        if (mappedClass == null) {
            return;
        }

        mappedClass.classModel.getFieldModels().forEach(model -> {
            final MappedField field = new MappedField(this, model);
            if (!field.isTransient()) {
                persistenceFields.add(field);
            } else {
                LOG.warning(format("Ignoring (will not persist) field: %s.%s [type:%s]", type.getName(),
                    field.getJavaFieldName(), field.getType().getName()));
            }
        });
    }

    public ClassModel<?> getClassModel() {
        return classModel;
    }

    private static class ClassMethodPair {
        private final Class<?> clazz;
        private final Method method;

        ClassMethodPair(final Class<?> c, final Method m) {
            clazz = c;
            method = m;
        }
    }

}
