/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */
package org.apache.directory.server.core.partition.avl;


import java.util.Comparator;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.directory.server.xdbm.MasterTable;


/**
 * TODO Make it so the master table does not extend table interface - not needed
 * with this single use of delete so we should just use containment.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class AvlMasterTable<E> extends AvlTable<Long, E> implements MasterTable<E>
{
    private Properties props = new Properties();
    private AtomicLong counter = new AtomicLong( 0 );
    
    
    public AvlMasterTable( String name, Comparator<Long> keyComparator, Comparator<E> valComparator, 
        boolean dupsEnabled )
    {
        super( name, keyComparator, valComparator, dupsEnabled );
    }

    
    public void delete( Long id ) throws Exception
    {
        super.remove( id );
    }


    public Long getCurrentId() throws Exception
    {
        return counter.longValue();
    }

    public Long getNextId() throws Exception
    {
        return counter.incrementAndGet();
    }

    
    public String getProperty( String property ) throws Exception
    {
        return props.getProperty( property );
    }

    
    public void setProperty( String property, String value ) throws Exception
    {
        props.setProperty( property, value );
    }
}
