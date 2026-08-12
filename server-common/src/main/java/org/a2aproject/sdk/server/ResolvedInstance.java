package org.a2aproject.sdk.server;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Iterator;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.util.TypeLiteral;

import org.jspecify.annotations.Nullable;

/**
 * An {@link Instance} wrapper for a pre-resolved value, for use in non-CDI contexts
 * such as convenience constructors and tests.
 *
 * @param <T> the bean type
 */
public class ResolvedInstance<T> implements Instance<T> {

    private final @Nullable T value;

    public ResolvedInstance(@Nullable T value) {
        this.value = value;
    }

    @Override
    public T get() {
        if (value == null) {
            throw new UnsatisfiedResolutionException("No value available");
        }
        return value;
    }

    @Override
    public boolean isUnsatisfied() {
        return value == null;
    }

    @Override
    public boolean isAmbiguous() {
        return false;
    }

    @Override
    public Iterator<T> iterator() {
        return value != null ? Collections.singleton(value).iterator() : Collections.emptyIterator();
    }

    @Override
    public void destroy(T instance) {
    }

    @Override
    public Instance<T> select(Annotation... qualifiers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <U extends T> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Handle<T> getHandle() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<? extends Handle<T>> handles() {
        throw new UnsupportedOperationException();
    }
}
