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
package org.apache.directory.server.core.event;


import java.util.Comparator;

import org.apache.directory.server.i18n.I18n;
import org.apache.directory.shared.ldap.NotImplementedException;
import org.apache.directory.shared.ldap.entry.StringValue;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.exception.LdapInvalidSearchFilterException;
import org.apache.directory.shared.ldap.filter.ApproximateNode;
import org.apache.directory.shared.ldap.filter.EqualityNode;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.ExtensibleNode;
import org.apache.directory.shared.ldap.filter.GreaterEqNode;
import org.apache.directory.shared.ldap.filter.LessEqNode;
import org.apache.directory.shared.ldap.filter.PresenceNode;
import org.apache.directory.shared.ldap.filter.ScopeNode;
import org.apache.directory.shared.ldap.filter.SimpleNode;
import org.apache.directory.shared.ldap.filter.SubstringNode;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.LdapComparator;
import org.apache.directory.shared.ldap.schema.MatchingRule;
import org.apache.directory.shared.ldap.schema.Normalizer;
import org.apache.directory.shared.ldap.schema.SchemaManager;


/**
 * Evaluates LeafNode assertions on candidates using a database.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 928945 $
 */
public class LeafEvaluator implements Evaluator
{
    /** equality matching type constant */
    private static final int EQUALITY_MATCH = 0;
    
    /** ordering matching type constant */
    private static final int ORDERING_MATCH = 1;
    
    /** substring matching type constant */
    private static final int SUBSTRING_MATCH = 3;

    /** SchemaManager needed for normalizing and comparing values */
    private SchemaManager schemaManager;
    
    /** Substring node evaluator we depend on */
    private SubstringEvaluator substringEvaluator;
    
    /** ScopeNode evaluator we depend on */
    private ScopeEvaluator scopeEvaluator;

    /** Constants used for comparisons */
    private static final boolean COMPARE_GREATER = true;
    private static final boolean COMPARE_LESSER = false;


    /**
     * Creates a leaf expression node evaluator.
     *
     * @param substringEvaluator
     */
    public LeafEvaluator( SchemaManager schemaManager,
        SubstringEvaluator substringEvaluator )
    {
        this.schemaManager = schemaManager;
        this.scopeEvaluator = new ScopeEvaluator();
        this.substringEvaluator = substringEvaluator;
    }


    public ScopeEvaluator getScopeEvaluator()
    {
        return scopeEvaluator;
    }


    public SubstringEvaluator getSubstringEvaluator()
    {
        return substringEvaluator;
    }


    /**
     * @see Evaluator#evaluate(ExprNode, String, ServerEntry)
     */
    public boolean evaluate( ExprNode node, String dn, ServerEntry entry ) throws LdapException
    {
        if ( node instanceof ScopeNode )
        {
            return scopeEvaluator.evaluate( node, dn, entry );
        }

        if ( node instanceof PresenceNode )
        {
            String attrId = ( ( PresenceNode ) node ).getAttribute();
            return evalPresence( attrId, entry );
        }
        else if ( ( node instanceof EqualityNode ) || ( node instanceof ApproximateNode ) )
        {
            return evalEquality( ( EqualityNode<?> ) node, entry );
        }
        else if ( node instanceof GreaterEqNode )
        {
            return evalGreaterOrLesser( ( GreaterEqNode<?> ) node, entry, COMPARE_GREATER );
        }
        else if ( node instanceof LessEqNode )
        {
            return evalGreaterOrLesser( ( LessEqNode<?> ) node, entry, COMPARE_LESSER );
        }
        else if ( node instanceof SubstringNode )
        {
            return substringEvaluator.evaluate( node, dn, entry );
        }
        else if ( node instanceof ExtensibleNode )
        {
            throw new NotImplementedException();
        }
        else
        {
            throw new LdapInvalidSearchFilterException( I18n.err( I18n.ERR_245, node ) );
        }
    }


    /**
     * Evaluates a simple greater than or less than attribute value assertion on
     * a perspective candidate.
     * 
     * @param node the greater than or less than node to evaluate
     * @param entry the perspective candidate
     * @param isGreater true if it is a greater than or equal to comparison,
     *      false if it is a less than or equal to comparison.
     * @return the ava evaluation on the perspective candidate
     * @throws LdapException if there is a database access failure
     */
    @SuppressWarnings("unchecked")
    private boolean evalGreaterOrLesser( SimpleNode<?> node, ServerEntry entry, boolean isGreaterOrLesser )
        throws LdapException
    {
        String attrId = node.getAttribute();

        // get the attribute associated with the node
        AttributeType type = schemaManager.lookupAttributeTypeRegistry( attrId );
        EntryAttribute attr = entry.get( type );

        // If we do not have the attribute just return false
        if ( null == attr )
        {
            return false;
        }

        /*
         * We need to iterate through all values and for each value we normalize
         * and use the comparator to determine if a match exists.
         */
        Normalizer normalizer = getNormalizer( attrId );
        Comparator comparator = getComparator( attrId );
        Object filterValue = normalizer.normalize( node.getValue() );

        /*
         * Cheaper to not check isGreater in one loop - better to separate
         * out into two loops which you choose to execute based on isGreater
         */
        if ( isGreaterOrLesser == COMPARE_GREATER )
        {
            for ( Value<?> value : attr )
            {
                Object normValue = normalizer.normalize( value );

                // Found a value that is greater than or equal to the ava value
                if ( 0 >= comparator.compare( normValue, filterValue ) )
                {
                    return true;
                }
            }
        }
        else
        {
            for ( Value<?> value : attr )
            {
                Object normValue = normalizer.normalize( value );

                // Found a value that is less than or equal to the ava value
                if ( 0 <= comparator.compare( normValue, filterValue ) )
                {
                    return true;
                }
            }
        }

        // no match so return false
        return false;
    }


    /**
     * Evaluates a simple presence attribute value assertion on a perspective
     * candidate.
     * 
     * @param attrId the name of the attribute tested for presence 
     * @param entry the perspective candidate
     * @return the ava evaluation on the perspective candidate
     */
    private boolean evalPresence( String attrId, ServerEntry entry ) throws LdapException
    {
        if ( entry == null )
        {
            return false;
        }

        return null != entry.get( attrId );
    }


    /**
     * Evaluates a simple equality attribute value assertion on a perspective
     * candidate.
     *
     * @param node the equality node to evaluate
     * @param entry the perspective candidate
     * @return the ava evaluation on the perspective candidate
     * @throws LdapException if there is a database access failure
     */
    @SuppressWarnings("unchecked")
    private boolean evalEquality( EqualityNode<?> node, ServerEntry entry ) throws LdapException
    {
        Normalizer normalizer = getNormalizer( node.getAttribute() );
        Comparator comparator = getComparator( node.getAttribute() );

        // get the attribute associated with the node
        EntryAttribute attr = entry.get( node.getAttribute() );

        // If we do not have the attribute just return false
        if ( null == attr )
        {
            return false;
        }

        // check if AVA value exists in attribute
        AttributeType at = schemaManager.lookupAttributeTypeRegistry( node.getAttribute() );
        Value<?> value = null;
        
        if ( at.getSyntax().isHumanReadable() )
        {
            if ( node.getValue().isBinary() )
            {
                value = new StringValue( node.getValue().getString() );
            }
            else
            {
                value = node.getValue();
            }
        }
        else
        {
            value = node.getValue();
        }
        
        if ( attr.contains( value ) )
        {
            return true;
        }

        // get the normalized AVA filter value
        Value<?> filterValue = normalizer.normalize( value );

        // check if the normalized value is present
        if ( attr.contains( filterValue ) )
        {
            return true;
        }

        /*
         * We need to now iterate through all values because we could not get
         * a lookup to work.  For each value we normalize and use the comparator
         * to determine if a match exists.
         */
        for ( Value<?> val : attr )
        {
            Value<?> normValue = normalizer.normalize( val );

            if ( 0 == comparator.compare( normValue.get(), filterValue.get() ) )
            {
                return true;
            }
        }

        // no match so return false
        return false;
    }


    /**
     * Gets the comparator for equality matching.
     *
     * @param attrId the attribute identifier
     * @return the comparator for equality matching
     * @throws LdapException if there is a failure
     */
    private LdapComparator<? super Object> getComparator( String attrId ) throws LdapException
    {
        MatchingRule mrule = getMatchingRule( attrId, EQUALITY_MATCH );
        return mrule.getLdapComparator();
    }


    /**
     * Gets the normalizer for equality matching.
     *
     * @param attrId the attribute identifier
     * @return the normalizer for equality matching
     * @throws LdapException if there is a failure
     */
    private Normalizer getNormalizer( String attrId ) throws LdapException
    {
        MatchingRule mrule = getMatchingRule( attrId, EQUALITY_MATCH );
        return mrule.getNormalizer();
    }


    /**
     * Gets the matching rule for an attributeType.
     *
     * @param attrId the attribute identifier
     * @return the matching rule
     * @throws LdapException if there is a failure
     */
    private MatchingRule getMatchingRule( String attrId, int matchType ) throws LdapException
    {
        MatchingRule mrule = null;
        AttributeType type = schemaManager.lookupAttributeTypeRegistry( attrId );

        switch ( matchType )
        {
            case ( EQUALITY_MATCH ):
                mrule = type.getEquality();
                break;

            case ( SUBSTRING_MATCH ):
                mrule = type.getSubstring();
                break;

            case ( ORDERING_MATCH ):
                mrule = type.getOrdering();
                break;

            default:
                throw new LdapException( I18n.err( I18n.ERR_246, matchType ) );
        }

        if ( ( mrule == null ) && ( matchType != EQUALITY_MATCH ) )
        {
            mrule = type.getEquality();
        }

        return mrule;
    }
}
