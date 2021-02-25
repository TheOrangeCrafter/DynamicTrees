package com.ferreusveritas.dynamictrees.api.datapacks;

/**
 *
 *
 * @author Harley O'Connor
 */
public interface IPropertyApplier<T, V> {

    /**
     * Applies the given property value to the given object.
     *
     * @param object The object to apply the value to.
     * @param value The value to apply.
     * @return A {@link PropertyApplierResult} object.
     */
    PropertyApplierResult apply (final T object, final V value);

}