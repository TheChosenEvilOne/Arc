package io.anuke.arc.util.reflect;

import io.anuke.gwtref.client.ReflectionCache;
import io.anuke.gwtref.client.Type;

import java.lang.annotation.Inherited;

/**
 * Utilities for Class reflection.
 * @author nexsoftware
 */
@SuppressWarnings("unchecked")
public final class ClassReflection{

    /** Returns the Class object associated with the class or interface with the supplied string name. */
    public static Class forName(String name) throws ReflectionException{
        try{
            return ReflectionCache.forName(name).getClassOfType();
        }catch(ClassNotFoundException e){
            throw new ReflectionException("Class not found: " + name);
        }
    }

    /** Returns the simple name of the underlying class as supplied in the source code. */
    public static String getSimpleName(Class c){
        return c.getSimpleName();
    }

    /** Determines if the supplied Object is assignment-compatible with the object represented by supplied Class. */
    public static boolean isInstance(Class c, Object obj){
        return obj != null && isAssignableFrom(c, obj.getClass());
    }

    /**
     * Determines if the class or interface represented by first Class parameter is either the same as, or is a superclass or
     * superinterface of, the class or interface represented by the second Class parameter.
     */
    public static boolean isAssignableFrom(Class c1, Class c2){
        Type c1Type = ReflectionCache.getType(c1);
        Type c2Type = ReflectionCache.getType(c2);
        return c1Type.isAssignableFrom(c2Type);
    }

    /** Returns true if the class or interface represented by the supplied Class is a member class. */
    public static boolean isMemberClass(Class c){
        return ReflectionCache.getType(c).isMemberClass();
    }

    /** Returns true if the class or interface represented by the supplied Class is a static class. */
    public static boolean isStaticClass(Class c){
        return ReflectionCache.getType(c).isStatic();
    }

    /** Determines if the supplied Class object represents an array class. */
    public static boolean isArray(Class c){
        return ReflectionCache.getType(c).isArray();
    }

    /** Determines if the supplied Class object represents a primitive type. */
    public static boolean isPrimitive(Class c){
        return ReflectionCache.getType(c).isPrimitive();
    }

    /** Determines if the supplied Class object represents an enum type. */
    public static boolean isEnum(Class c){
        return ReflectionCache.getType(c).isEnum();
    }

    /** Determines if the supplied Class object represents an annotation type. */
    public static boolean isAnnotation(Class c){
        return ReflectionCache.getType(c).isAnnotation();
    }

    /** Determines if the supplied Class object represents an interface type. */
    public static boolean isInterface(Class c){
        return ReflectionCache.getType(c).isInterface();
    }

    /** Determines if the supplied Class object represents an abstract type. */
    public static boolean isAbstract(Class c){
        return ReflectionCache.getType(c).isAbstract();
    }

    /** Creates a new instance of the class represented by the supplied Class. */
    public static <T> T newInstance(Class<T> c) throws ReflectionException{
        try{
            return (T)ReflectionCache.getType(c).newInstance();
        }catch(NoSuchMethodException e){
            throw new ReflectionException("Could not use default constructor of " + c.getName(), e);
        }
    }

    /** Returns the Class representing the component type of an array. If this class does not represent an array class this method returns null. */
    public static Class getComponentType(Class c){
        return ReflectionCache.getType(c).getComponentType();
    }

    /** Returns an array of {@link Constructor} containing the public constructors of the class represented by the supplied Class. */
    public static Constructor[] getConstructors(Class c){
        io.anuke.gwtref.client.Constructor[] constructors = ReflectionCache.getType(c).getConstructors();
        Constructor[] result = new Constructor[constructors.length];
        for(int i = 0, j = constructors.length; i < j; i++){
            result[i] = new Constructor(constructors[i]);
        }
        return result;
    }

    /**
     * Returns a {@link Constructor} that represents the public constructor for the supplied class which takes the supplied
     * parameter types.
     */
    public static Constructor getConstructor(Class c, Class... parameterTypes) throws ReflectionException{
        try{
            return new Constructor(ReflectionCache.getType(c).getConstructor(parameterTypes));
        }catch(SecurityException e){
            throw new ReflectionException("Security violation while getting constructor for class: " + c.getName(), e);
        }catch(NoSuchMethodException e){
            throw new ReflectionException("Constructor not found for class: " + c.getName(), e);
        }
    }

    /**
     * Returns a {@link Constructor} that represents the constructor for the supplied class which takes the supplied parameter
     * types.
     */
    public static Constructor getDeclaredConstructor(Class c, Class... parameterTypes) throws ReflectionException{
        try{
            return new Constructor(ReflectionCache.getType(c).getDeclaredConstructor(parameterTypes));
        }catch(SecurityException e){
            throw new ReflectionException("Security violation while getting constructor for class: " + c.getName(), e);
        }catch(NoSuchMethodException e){
            throw new ReflectionException("Constructor not found for class: " + c.getName(), e);
        }
    }

    /** Returns the elements of this enum class or null if this Class object does not represent an enum type. */
    public static Object[] getEnumConstants(Class c){
        return ReflectionCache.getType(c).getEnumConstants();
    }

    /** Returns an array of {@link Method} containing the public member methods of the class represented by the supplied Class. */
    public static Method[] getMethods(Class c){
        io.anuke.gwtref.client.Method[] methods = ReflectionCache.getType(c).getMethods();
        Method[] result = new Method[methods.length];
        for(int i = 0, j = methods.length; i < j; i++){
            result[i] = new Method(methods[i]);
        }
        return result;
    }

    /**
     * Returns a {@link Method} that represents the public member method for the supplied class which takes the supplied parameter
     * types.
     */
    public static Method getMethod(Class c, String name, Class... parameterTypes) throws ReflectionException{
        try{
            return new Method(ReflectionCache.getType(c).getMethod(name, parameterTypes));
        }catch(SecurityException e){
            throw new ReflectionException("Security violation while getting method: " + name + ", for class: " + c.getName(), e);
        }catch(NoSuchMethodException e){
            throw new ReflectionException("Method not found: " + name + ", for class: " + c.getName(), e);
        }
    }

    /** Returns an array of {@link Method} containing the methods declared by the class represented by the supplied Class. */
    public static Method[] getDeclaredMethods(Class c){
        io.anuke.gwtref.client.Method[] methods = ReflectionCache.getType(c).getDeclaredMethods();
        Method[] result = new Method[methods.length];
        for(int i = 0, j = methods.length; i < j; i++){
            result[i] = new Method(methods[i]);
        }
        return result;
    }

    /** Returns a {@link Method} that represents the method declared by the supplied class which takes the supplied parameter types. */
    public static Method getDeclaredMethod(Class c, String name, Class... parameterTypes) throws ReflectionException{
        try{
            return new Method(ReflectionCache.getType(c).getDeclaredMethod(name, parameterTypes));
        }catch(SecurityException e){
            throw new ReflectionException("Security violation while getting method: " + name + ", for class: " + c.getName(), e);
        }catch(NoSuchMethodException e){
            throw new ReflectionException("Method not found: " + name + ", for class: " + c.getName(), e);
        }
    }

    /** Returns an array of {@link Field} containing the public fields of the class represented by the supplied Class. */
    public static Field[] getFields(Class c){
        io.anuke.gwtref.client.Field[] fields = ReflectionCache.getType(c).getFields();
        Field[] result = new Field[fields.length];
        for(int i = 0, j = fields.length; i < j; i++){
            result[i] = new Field(fields[i]);
        }
        return result;
    }

    /** Returns a {@link Field} that represents the specified public member field for the supplied class. */
    public static Field getField(Class c, String name) throws ReflectionException{
        try{
            return new Field(ReflectionCache.getType(c).getField(name));
        }catch(SecurityException e){
            throw new ReflectionException("Security violation while getting field: " + name + ", for class: " + c.getName(), e);
        }catch(NoSuchFieldException e){
            throw new ReflectionException("Field not found: " + name + ", for class: " + c.getName(), e);
        }
    }

    /** Returns an array of {@link Field} objects reflecting all the fields declared by the supplied class. */
    public static Field[] getDeclaredFields(Class c){
        io.anuke.gwtref.client.Field[] fields = ReflectionCache.getType(c).getDeclaredFields();
        Field[] result = new Field[fields.length];
        for(int i = 0, j = fields.length; i < j; i++){
            result[i] = new Field(fields[i]);
        }
        return result;
    }

    /** Returns a {@link Field} that represents the specified declared field for the supplied class. */
    public static Field getDeclaredField(Class c, String name) throws ReflectionException{
        try{
            return new Field(ReflectionCache.getType(c).getDeclaredField(name));
        }catch(SecurityException e){
            throw new ReflectionException("Security violation while getting field: " + name + ", for class: " + c.getName(), e);
        }catch(NoSuchFieldException e){
            throw new ReflectionException("Field not found: " + name + ", for class: " + c.getName(), e);
        }

    }

    /** Returns true if the supplied class has an annotation of the given type. */
    public static boolean isAnnotationPresent(Class c, Class<? extends java.lang.annotation.Annotation> annotationType){
        Annotation[] annotations = getAnnotations(c);
        for(Annotation annotation : annotations){
            if(annotation.getAnnotationType().equals(annotationType)) return true;
        }
        return false;
    }

    /**
     * Returns an array of {@link Annotation} objects reflecting all annotations declared by the supplied class, and inherited
     * from its superclass. Returns an empty array if there are none.
     */
    public static Annotation[] getAnnotations(Class c){
        Type declType = ReflectionCache.getType(c);
        java.lang.annotation.Annotation[] annotations = declType.getDeclaredAnnotations();

        // annotations of supplied class
        Annotation[] result = new Annotation[annotations.length];
        for(int i = 0; i < annotations.length; i++){
            result[i] = new Annotation(annotations[i]);
        }

        // search super classes, until Object.class is reached
        Type superType = declType.getSuperclass();
        java.lang.annotation.Annotation[] superAnnotations;
        while(!superType.getClassOfType().equals(Object.class)){
            superAnnotations = superType.getDeclaredAnnotations();
            for(int i = 0; i < superAnnotations.length; i++){
                // check for annotation types marked as Inherited
                Type annotationType = ReflectionCache.getType(superAnnotations[i].annotationType());
                if(annotationType.getDeclaredAnnotation(Inherited.class) != null){
                    // ignore duplicates
                    boolean duplicate = false;
                    for(Annotation annotation : result){
                        if(annotation.getAnnotationType().equals(annotationType)){
                            duplicate = true;
                            break;
                        }
                    }
                    // append to result set
                    if(!duplicate){
                        Annotation[] copy = new Annotation[result.length + 1];
                        for(int j = 0; j < result.length; j++){
                            copy[j] = result[j];
                        }
                        copy[result.length] = new Annotation(superAnnotations[i]);
                        result = copy;
                    }
                }
            }
            superType = superType.getSuperclass();
        }

        return result;
    }

    /**
     * Returns an {@link Annotation} object reflecting the annotation provided, or null of this class doesn't have, or doesn't
     * inherit, such an annotation. This is a convenience function if the caller knows already which annotation type he's looking
     * for.
     */
    public static Annotation getAnnotation(Class c, Class<? extends java.lang.annotation.Annotation> annotationType){
        Annotation[] annotations = getAnnotations(c);
        for(Annotation annotation : annotations){
            if(annotation.getAnnotationType().equals(annotationType)) return annotation;
        }
        return null;
    }

    /**
     * Returns an array of {@link Annotation} objects reflecting all annotations declared by the supplied class, or an empty
     * array if there are none. Does not include inherited annotations.
     */
    public static Annotation[] getDeclaredAnnotations(Class c){
        java.lang.annotation.Annotation[] annotations = ReflectionCache.getType(c).getDeclaredAnnotations();
        Annotation[] result = new Annotation[annotations.length];
        for(int i = 0; i < annotations.length; i++){
            result[i] = new Annotation(annotations[i]);
        }
        return result;
    }

    /**
     * Returns an {@link Annotation} object reflecting the annotation provided, or null of this class doesn't have such an
     * annotation. This is a convenience function if the caller knows already which annotation type he's looking for.
     */
    public static Annotation getDeclaredAnnotation(Class c, Class<? extends java.lang.annotation.Annotation> annotationType){
        java.lang.annotation.Annotation annotation = ReflectionCache.getType(c).getDeclaredAnnotation(annotationType);
        if(annotation != null) return new Annotation(annotation);
        return null;
    }

    public static Class[] getInterfaces(Class c){
        return ReflectionCache.getType(c).getInterfaces();
    }
}
