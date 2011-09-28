/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package org.apache.directory.server.xdbm;


import java.io.File;

import org.apache.directory.shared.ldap.cursor.Cursor;
import org.apache.directory.shared.ldap.schema.AttributeType;


/**
 * An index into the master table which returns one or more entry's positions
 * in the master table for those entries which posses an attribute with the
 * specified value.  Cursors over indices can also be gotten to traverse the
 * values of the index.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 917312 $
 */
public interface Index<K, O, ID>
{
    int DEFAULT_INDEX_CACHE_SIZE = 100;


    // -----------------------------------------------------------------------
    // C O N F I G U R A T I O N   M E T H O D S
    // -----------------------------------------------------------------------

    /**
     * Gets the attribute identifier set at configuration time for this index which may not
     * be the OID but an alias name for the attributeType associated with this Index
     *
     * @return configured attribute oid or alias name
     */
    String getAttributeId();


    /**
     * Sets the attribute identifier set at configuration time for this index which may not
     * be the OID but an alias name for the attributeType associated with this Index
     *
     * @param attributeId configured attribute oid or alias name
     */
    void setAttributeId( String attributeId );


    /**
     * Gets the size of the index cache in terms of the number of index entries to be cached.
     *
     * @return the size of the index cache
     */
    int getCacheSize();


    /**
     * Sets the size of the index cache in terms of the number of index entries to be cached.
     *
     * @param cacheSize the size of the index cache
     */
    void setCacheSize( int cacheSize );


    /**
     * Sets the working directory path to something other than the default. Sometimes more
     * performance is gained by locating indices on separate disk spindles.
     *
     * @param wkDirPath optional working directory path
     */
    void setWkDirPath( File wkDirPath );


    /**
     * Gets the working directory path to something other than the default. Sometimes more
     * performance is gained by locating indices on separate disk spindles.
     *
     * @return optional working directory path
     */
    File getWkDirPath();


    /**
     * Checks whether or not calls to count the number of keys greater than or
     * less than the key are exact.
     *
     * Checking to see the number of values greater than or less than some key
     * may be excessively costly.  Since this is not a critical function but
     * one that assists in optimizing searches some implementations can just
     * return a worst case (maximum) guess.
     *
     * @return true if the count is an exact value or a worst case guess
     */
    boolean isCountExact();


    // -----------------------------------------------------------------------
    // E N D   C O N F I G U R A T I O N   M E T H O D S
    // -----------------------------------------------------------------------

    /**
     * Gets the attribute this Index is built upon.
     *
     * @return the id of the Index's attribute
     */
    AttributeType getAttribute();


    /**
     * Gets the normalized value for an attribute.
     *
     * @param attrVal the user provided value to normalize
     * @return the normalized value.
     * @throws Exception if something goes wrong.
     */
    K getNormalized( K attrVal ) throws Exception;


    /**
     * Gets the total scan count for this index.
     *
     * @return the number of key/value pairs in this index
     * @throws Exception on failure to access index db files
     */
    int count() throws Exception;


    /**
     * Gets the scan count for the occurance of a specific attribute value 
     * within the index.
     *
     * @param attrVal the value of the attribute to get a scan count for
     * @return the number of key/value pairs in this index with the value value
     * @throws Exception on failure to access index db files
     */
    int count( K attrVal ) throws Exception;


    int greaterThanCount( K attrVal ) throws Exception;


    int lessThanCount( K attrVal ) throws Exception;


    ID forwardLookup( K attrVal ) throws Exception;


    K reverseLookup( ID id ) throws Exception;


    void add( K attrVal, ID id ) throws Exception;


    /**
     * Remove all the reference to an entry from the index.
     * 
     * As an entry might be referenced more than once in the forward index,
     * depending on which index we are dealing with, we need to iterate 
     * over all the values contained into the reverse index for this entryId.
     * 
     * For instance, considering the ObjectClass index for an entry having
     * three ObjectClasses (top, person, inetOrgPerson), then the reverse
     * index will contain :
     * 
     * [entryId, [top, person, inetOrgPerson]]
     * 
     * and the forward index will contain many entries like :
     * [top, [..., entryId, ...]]
     * [person,  [..., entryId, ...]]
     * [inetOrgPerson,  [..., entryId, ...]]
     * 
     * So dropping the entryId means that we must first get all the values from
     * the reverse index (and we will get [top, person, inetOrgPerson]) then to
     * iterate through all those values to remove entryId from the associated 
     * list of entryIds.
     * 
     * 
     * @param entryId The master table entry ID to remove
     * @throws Exception
     */
    void drop( ID entryId ) throws Exception;


    void drop( K attrVal, ID id ) throws Exception;


    IndexCursor<K, O, ID> reverseCursor() throws Exception;


    IndexCursor<K, O, ID> forwardCursor() throws Exception;


    IndexCursor<K, O, ID> reverseCursor( ID id ) throws Exception;


    IndexCursor<K, O, ID> forwardCursor( K key ) throws Exception;


    Cursor<K> reverseValueCursor( ID id ) throws Exception;


    Cursor<ID> forwardValueCursor( K key ) throws Exception;


    boolean forward( K attrVal ) throws Exception;


    boolean forward( K attrVal, ID id ) throws Exception;


    boolean reverse( ID id ) throws Exception;


    boolean reverse( ID id, K attrVal ) throws Exception;


    boolean forwardGreaterOrEq( K attrVal ) throws Exception;


    boolean forwardGreaterOrEq( K attrVal, ID id ) throws Exception;


    boolean reverseGreaterOrEq( ID id ) throws Exception;


    boolean reverseGreaterOrEq( ID id, K attrVal ) throws Exception;


    boolean forwardLessOrEq( K attrVal ) throws Exception;


    boolean forwardLessOrEq( K attrVal, ID id ) throws Exception;


    boolean reverseLessOrEq( ID id ) throws Exception;


    boolean reverseLessOrEq( ID id, K attrVal ) throws Exception;


    void close() throws Exception;


    void sync() throws Exception;
}
