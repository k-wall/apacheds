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
package org.apache.directory.server.core.subtree;


import java.util.Iterator;

import org.apache.directory.server.core.event.Evaluator;
import org.apache.directory.server.core.event.ExpressionEvaluator;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.schema.registries.OidRegistry;
import org.apache.directory.shared.ldap.subtree.SubtreeSpecification;
import org.apache.directory.shared.ldap.util.NamespaceTools;


/**
 * An evaluator used to determine if an entry is included in the collection
 * represented by a subtree specification.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 927146 $
 */
public class SubtreeEvaluator
{
    /** A refinement filter evaluator */
    private final Evaluator evaluator;


    /**
     * Creates a subtreeSpecification evaluatior which can be used to determine
     * if an entry is included within the collection of a subtree.
     *
     * @param oidRegistry a registry used to lookup objectClass names for OIDs
     * @param attrRegistry registry to be looked up
     */
    public SubtreeEvaluator( OidRegistry oidRegistry, SchemaManager schemaManager )
    {
        evaluator = new ExpressionEvaluator( oidRegistry, schemaManager );
    }


    /**
     * Determines if an entry is selected by a subtree specification.
     *
     * @param subtree the subtree specification
     * @param apDn the distinguished name of the administrative point containing the subentry
     * @param entryDn the distinguished name of the candidate entry
     * @return true if the entry is selected by the specification, false if it is not
     * @throws LdapException if errors are encountered while evaluating selection
     */
    public boolean evaluate( SubtreeSpecification subtree, DN apDn, DN entryDn, ServerEntry entry )
        throws LdapException
    {
        // TODO: Try to make this cast unnecessary.
        DN dnEntryDn = (DN) entryDn;
        
        /* =====================================================================
         * NOTE: Regarding the overall approach, we try to narrow down the
         * possibilities by slowly pruning relative names off of the entryDn.
         * For example we check first if the entry is a descendant of the AP.
         * If so we use the relative name thereafter to calculate if it is
         * a descendant of the base.  This means shorter names to compare and
         * less work to do while we continue to deduce inclusion by the subtree
         * specification.
         * =====================================================================
         */

        /*
         * First we simply check if the candidate entry is a descendant of the
         * administrative point.  In the process we calculate the relative
         * distinguished name relative to the administrative point.
         */
        DN apRelativeRdn;
        
        if ( !NamespaceTools.isDescendant( apDn, entryDn ) )
        {
            return false;
        }
        else if ( apDn.equals( entryDn ) )
        {
            apRelativeRdn = new DN();
        }
        else
        {
            apRelativeRdn = NamespaceTools.getRelativeName( apDn, entryDn );
        }

        /*
         * We do the same thing with the base as we did with the administrative
         * point: check if the entry is a descendant of the base and find the
         * relative name of the entry with respect to the base rdn.  With the
         * baseRelativeRdn we can later make comparisons with specific exclusions.
         */
        DN baseRelativeRdn;
        
        if ( subtree.getBase() != null && subtree.getBase().size() == 0 )
        {
            baseRelativeRdn = apRelativeRdn;
        }
        else if ( apRelativeRdn.equals( subtree.getBase() ) )
        {
            baseRelativeRdn = new DN();
        }
        else if ( !NamespaceTools.isDescendant( subtree.getBase(), apRelativeRdn ) )
        {
            return false;
        }
        else
        {
            baseRelativeRdn = NamespaceTools.getRelativeName( subtree.getBase(), apRelativeRdn );
        }

        /*
         * Evaluate based on minimum and maximum chop values.  Here we simply
         * need to compare the distances respectively with the size of the
         * baseRelativeRdn.  For the max distance entries with a baseRelativeRdn
         * size greater than the max distance are rejected.  For the min distance
         * entries with a baseRelativeRdn size less than the minimum distance
         * are rejected.
         */
        if ( subtree.getMaxBaseDistance() != SubtreeSpecification.UNBOUNDED_MAX )
        {
            if ( subtree.getMaxBaseDistance() < baseRelativeRdn.size() )
            {
                return false;
            }
        }

        if ( subtree.getMinBaseDistance() > 0 )
        {
            if ( baseRelativeRdn.size() < subtree.getMinBaseDistance() )
            {
                return false;
            }
        }

        /*
         * For specific exclusions we must iterate through the set and check
         * if the baseRelativeRdn is a descendant of the exclusion.  The
         * isDescendant() function will return true if the compared names
         * are equal so for chopAfter exclusions we must check for equality
         * as well and reject if the relative names are equal.
         */
        Iterator list = subtree.getChopBeforeExclusions().iterator();
        
        while ( list.hasNext() )
        {
            DN chopBefore = ( DN ) list.next();
            
            if ( NamespaceTools.isDescendant( chopBefore, baseRelativeRdn ) )
            {
                return false;
            }
        }

        list = subtree.getChopAfterExclusions().iterator();
        
        while ( list.hasNext() )
        {
            DN chopAfter = ( DN ) list.next();
            
            if ( NamespaceTools.isDescendant( chopAfter, baseRelativeRdn ) && !chopAfter.equals( baseRelativeRdn ) )
            {
                return false;
            }
        }

        /*
         * The last remaining step is to check and see if the refinement filter
         * selects the entry candidate based on objectClass attribute values.
         * To do this we invoke the refinement evaluator members evaluate() method.
         */
        if ( subtree.getRefinement() != null )
        {
            return evaluator.evaluate( subtree.getRefinement(), dnEntryDn.getNormName(), entry );
        }

        /*
         * If nothing has rejected the candidate entry and there is no refinement
         * filter then the entry is included in the collection represented by the
         * subtree specification so we return true.
         */
        return true;
    }
}
