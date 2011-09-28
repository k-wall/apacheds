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
package org.apache.directory.server.core.trigger;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.directory.server.core.entry.ClonedServerEntry;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.OperationContext;
import org.apache.directory.server.core.partition.ByPassConstants;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.exception.LdapInvalidDnException;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.trigger.StoredProcedureParameter;


public class ModifyStoredProcedureParameterInjector extends AbstractStoredProcedureParameterInjector
{
    private DN modifiedEntryName;
    private List<Modification> modifications;
    private ServerEntry oldEntry;
    
    
    public ModifyStoredProcedureParameterInjector( ModifyOperationContext opContext ) throws Exception
    {
        super( opContext );
        modifiedEntryName = opContext.getDn();
        modifications = opContext.getModItems();
        this.oldEntry = getEntry( opContext );
        Map<Class<?>, MicroInjector> injectors = super.getInjectors();
        injectors.put( StoredProcedureParameter.Modify_OBJECT.class, $objectInjector );
        injectors.put( StoredProcedureParameter.Modify_MODIFICATION.class, $modificationInjector );
        injectors.put( StoredProcedureParameter.Modify_OLD_ENTRY.class, $oldEntryInjector );
        injectors.put( StoredProcedureParameter.Modify_NEW_ENTRY.class, $newEntryInjector );
    }
    
    
    MicroInjector $objectInjector = new MicroInjector()
    {
        public Object inject( OperationContext opContext, StoredProcedureParameter param ) throws LdapInvalidDnException
        {
            // Return a safe copy constructed with user provided name.
            return new DN( modifiedEntryName.getName() );
        }
    };
    
    
    MicroInjector $modificationInjector = new MicroInjector()
    {
        public Object inject( OperationContext opContext, StoredProcedureParameter param ) throws LdapException
        {
            List<Modification> newMods = new ArrayList<Modification>();
            
            for ( Modification mod:modifications )
            {
                newMods.add( mod.clone() );
            }
            
            return newMods;
        }
    };
    
    
    MicroInjector $oldEntryInjector = new MicroInjector()
    {
        public Object inject( OperationContext opContext, StoredProcedureParameter param ) throws LdapException
        {
            return oldEntry;
        }
    };
    
    
    MicroInjector $newEntryInjector = new MicroInjector()
    {
        public Object inject( OperationContext opContext, StoredProcedureParameter param ) throws Exception
        {
            return getEntry( opContext );
        }
    };
    
    
    private ClonedServerEntry getEntry( OperationContext opContext ) throws Exception
    {
        /**
         * Using LOOKUP_EXCLUDING_OPR_ATTRS_BYPASS here to exclude operational attributes
         * especially subentry related ones like "triggerExecutionSubentries".
         */
        return opContext.lookup( modifiedEntryName, ByPassConstants.LOOKUP_EXCLUDING_OPR_ATTRS_BYPASS );
    }
}
