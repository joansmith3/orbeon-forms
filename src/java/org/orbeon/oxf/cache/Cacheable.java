/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.cache;

/**
 * Interface that cacheable objects can optionally implement to support callback methods.
 */
public interface Cacheable {

    /**
     * Called when the object is added to the cache.
     */
    void added();

    /**
     * Called when the object is explicitly removed from the cache.
     */
    void removed();

    /**
     * Called when the object is being evicted from cache.
     */
    void evicted();
}
