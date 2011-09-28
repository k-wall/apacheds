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


import org.apache.directory.server.i18n.I18n;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.filter.AndNode;
import org.apache.directory.shared.ldap.filter.BranchNode;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.NotNode;
import org.apache.directory.shared.ldap.filter.OrNode;
import org.apache.directory.shared.ldap.filter.SimpleNode;


/**
 * The top level evaluation node for a refinement.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 927839 $
 */
public class RefinementEvaluator
{
    /** Leaf Evaluator flyweight use for leaf filter assertions */
    private RefinementLeafEvaluator leafEvaluator;


    // ------------------------------------------------------------------------
    // C O N S T R U C T O R S
    // ------------------------------------------------------------------------

    public RefinementEvaluator(RefinementLeafEvaluator leafEvaluator)
    {
        this.leafEvaluator = leafEvaluator;
    }


    public boolean evaluate( ExprNode node, EntryAttribute objectClasses ) throws LdapException
    {
        if ( node == null )
        {
            throw new IllegalArgumentException( I18n.err( I18n.ERR_295 ) );
        }
        
        if ( objectClasses == null )
        {
            throw new IllegalArgumentException( I18n.err( I18n.ERR_296 ) );
        }
        
        if ( !objectClasses.instanceOf( SchemaConstants.OBJECT_CLASS_AT ) )
        {
            throw new IllegalArgumentException( I18n.err( I18n.ERR_297 ) );
        }
        
        if ( node.isLeaf() )
        {
            return leafEvaluator.evaluate( ( SimpleNode ) node, objectClasses );
        }

        BranchNode bnode = ( BranchNode ) node;

        if ( node instanceof OrNode )
        {
            for ( ExprNode child:bnode.getChildren() )
            {
                if ( evaluate( child, objectClasses ) )
                {
                    return true;
                }
            }

            return false;
        }
        else if ( node instanceof AndNode )
        {
            for ( ExprNode child:bnode.getChildren() )
            {
                if ( !evaluate( child, objectClasses ) )
                {
                    return false;
                }
            }

            return true;
            
        }
        else if ( node instanceof NotNode )
        {
            if ( null != bnode.getFirstChild() )
            {
                return !evaluate( bnode.getFirstChild(), objectClasses );
            }

            throw new IllegalArgumentException( I18n.err( I18n.ERR_243, node ) );
            
        }
        else
        {
            throw new IllegalArgumentException( I18n.err( I18n.ERR_244, bnode ) );
        }
    }
}
