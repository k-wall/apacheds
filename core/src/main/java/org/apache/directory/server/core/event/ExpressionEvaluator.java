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


import org.apache.directory.server.i18n.I18n;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.exception.LdapInvalidSearchFilterException;
import org.apache.directory.shared.ldap.filter.AndNode;
import org.apache.directory.shared.ldap.filter.BranchNode;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.NotNode;
import org.apache.directory.shared.ldap.filter.OrNode;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.schema.registries.OidRegistry;



/**
 * Top level filter expression evaluator implemenation.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 927146 $
 */
public class ExpressionEvaluator implements Evaluator
{
    /** Leaf Evaluator flyweight use for leaf filter assertions */
    private LeafEvaluator leafEvaluator;


    // ------------------------------------------------------------------------
    // C O N S T R U C T O R S
    // ------------------------------------------------------------------------

    /**
     * Creates a top level Evaluator where leaves are delegated to a leaf node
     * evaluator which is already provided.
     *
     * @param leafEvaluator handles leaf node evaluation.
     */
    public ExpressionEvaluator( LeafEvaluator leafEvaluator )
    {
        this.leafEvaluator = leafEvaluator;
    }


    /**
     * Creates a top level Evaluator where leaves are delegated to a leaf node
     * evaluator which will be created.
     *
     * @param oidRegistry the oid reg used for attrID to oid resolution
     * @param attributeTypeRegistry the attribtype reg used for value comparison
     */
    public ExpressionEvaluator( OidRegistry oidRegistry, SchemaManager schemaManager )
    {
        SubstringEvaluator substringEvaluator = null;
        substringEvaluator = new SubstringEvaluator( schemaManager );
        leafEvaluator = new LeafEvaluator( schemaManager, substringEvaluator );
    }


    /**
     * Gets the leaf evaluator used by this top level expression evaluator.
     *
     * @return the leaf evaluator used by this top level expression evaluator
     */
    public LeafEvaluator getLeafEvaluator()
    {
        return leafEvaluator;
    }


    // ------------------------------------------------------------------------
    // Evaluator.evaluate() implementation
    // ------------------------------------------------------------------------

    /**
     * @see Evaluator#evaluate(ExprNode, String, ServerEntry)
     */
    public boolean evaluate( ExprNode node, String dn, ServerEntry entry ) throws LdapException
    {
        if ( node.isLeaf() )
        {
            return leafEvaluator.evaluate( node, dn, entry );
        }

        BranchNode bnode = ( BranchNode ) node;

        if ( bnode instanceof OrNode )
        {
            for ( ExprNode child: bnode.getChildren() )
            {
                if ( evaluate( child, dn, entry ) )
                {
                    return true;
                }
            }

            return false;
        }
        else if ( bnode instanceof AndNode )
        {
            for ( ExprNode child: bnode.getChildren() )
            {
                boolean res = evaluate( child, dn, entry );
                
                if ( !res )
                {
                    return false;
                }
            }

            return true;
        }
        else if ( bnode instanceof NotNode )
        {
            if ( null != bnode.getFirstChild() )
            {
                return !evaluate( bnode.getFirstChild(), dn, entry );
            }

            throw new LdapInvalidSearchFilterException( I18n.err( I18n.ERR_243, node ) );
        }
        else
        {
                throw new LdapInvalidSearchFilterException( I18n.err( I18n.ERR_244, bnode ) );
        }
    }
}
