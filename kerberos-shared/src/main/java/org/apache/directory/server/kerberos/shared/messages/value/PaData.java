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
package org.apache.directory.server.kerberos.shared.messages.value;


import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import org.apache.directory.server.i18n.I18n;
import org.apache.directory.server.kerberos.shared.messages.value.types.PaDataType;
import org.apache.directory.shared.asn1.AbstractAsn1Object;
import org.apache.directory.shared.asn1.ber.tlv.TLV;
import org.apache.directory.shared.asn1.ber.tlv.UniversalTag;
import org.apache.directory.shared.asn1.ber.tlv.Value;
import org.apache.directory.shared.asn1.codec.EncoderException;
import org.apache.directory.shared.ldap.util.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The Pre-Authentication data. Tha ASN.1 GRAMMAR IS :
 * 
 * PA-DATA         ::= SEQUENCE {
 *         -- NOTE: first tag is [1], not [0]
 *         padata-type     [1] Int32,
 *         padata-value    [2] OCTET STRING -- might be encoded AP-REQ
 * }
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 902575 $, $Date: 2010-01-24 15:38:06 +0100 (Dim, 24 jan 2010) $
 */
public class PaData extends AbstractAsn1Object
{
    /** The logger */
    private static final Logger log = LoggerFactory.getLogger( PaData.class );

    /** Speedup for logs */
    private static final boolean IS_DEBUG = log.isDebugEnabled();

    /** The Pre-authentication type */
    private PaDataType paDataType;
    
    /** The authentication data */
    private byte[] paDataValue;

    // Storage for computed lengths
    private transient int paDataTypeTagLength;
    private transient int paDataValueTagLength;
    private transient int preAuthenticationDataSeqLength;
    

    /**
     * Creates a new instance of PreAuthenticationData.
     */
    public PaData()
    {
    }

    
    /**
     * Creates a new instance of PreAuthenticationData.
     *
     * @param paDataType
     * @param paDataValue
     */
    public PaData( PaDataType paDataType, byte[] paDataValue )
    {
        this.paDataType = paDataType;
        this.paDataValue = paDataValue;
    }


    /**
     * Returns the {@link PaDataType}.
     *
     * @return The {@link PaDataType}.
     */
    public PaDataType getPaDataType()
    {
        return paDataType;
    }


    /**
     * Set the PA-DATA type
     *
     * @param paDataType The PA-DATA type
     */
    public void setPaDataType( int paDataType )
    {
        this.paDataType = PaDataType.getTypeByOrdinal( paDataType );
    }

    
    /**
     * Set the PA-DATA type
     *
     * @param paDataType The PA-DATA type
     */
    public void setPaDataType( PaDataType paDataType )
    {
        this.paDataType = paDataType;
    }

    
    /**
     * Returns the raw bytes of the {@link PaData}.
     *
     * @return The raw bytes of the {@link PaData}.
     */
    public byte[] getPaDataValue()
    {
        return paDataValue;
    }


    /**
     * Set the PA-DATA value
     *
     * @param paDataValue The PA-DATA value
     */
    public void setPaDataValue( byte[] paDataValue )
    {
        this.paDataValue = paDataValue;
    }

    
    /**
     * Compute the PreAuthenticationData length
     * 
     * PreAuthenticationData :
     * 
     * 0x30 L1 PreAuthenticationData sequence
     *  |
     *  +--> 0xA0 L2 padata-type tag
     *  |     |
     *  |     +--> 0x02 L2-1 padata-type (int)
     *  |
     *  +--> 0xA1 L3 padata-value tag
     *        |
     *        +--> 0x04 L3-1 padata-value (OCTET STRING)
     *        
     *  where L1 = L2 + lenght(0xA0) + length(L2) +
     *             L3 + lenght(0xA1) + length(L3) 
     *  and
     *  L2 = L2-1 + length(0x02) + length( L2-1) 
     *  L3 = L3-1 + length(0x04) + length( L3-1) 
     */
    public int computeLength()
    {
        // Compute the paDataType. The Length will always be contained in 1 byte
        int paDataTypeLength = Value.getNbBytes( paDataType.getOrdinal() );
        paDataTypeTagLength = 1 + TLV.getNbBytes( paDataTypeLength ) + paDataTypeLength;
        preAuthenticationDataSeqLength = 1 + TLV.getNbBytes( paDataTypeTagLength ) + paDataTypeTagLength;

        // Compute the paDataValue
        if ( paDataValue == null )
        {
            paDataValueTagLength = 1 + 1;
        }
        else
        {
            paDataValueTagLength = 1 + TLV.getNbBytes( paDataValue.length ) + paDataValue.length;
        }

        // Compute the whole sequence length
        preAuthenticationDataSeqLength += 1 + TLV.getNbBytes( paDataValueTagLength ) + paDataValueTagLength;

        return 1 + TLV.getNbBytes( preAuthenticationDataSeqLength ) + preAuthenticationDataSeqLength;

    }


    /**
     * Encode the PreAuthenticationData message to a PDU. 
     * 
     * PreAuthenticationData :
     * 
     * 0x30 LL
     *   0xA0 LL 
     *     0x02 0x01 padata-type
     *   0xA1 LL 
     *     0x04 LL padata-value
     * 
     * @param buffer The buffer where to put the PDU. It should have been allocated
     * before, with the right size.
     * @return The constructed PDU.
     */
    public ByteBuffer encode( ByteBuffer buffer ) throws EncoderException
    {
        if ( buffer == null )
        {
            throw new EncoderException( I18n.err( I18n.ERR_148 ) );
        }

        try
        {
            // The Checksum SEQ Tag
            buffer.put( UniversalTag.SEQUENCE_TAG );
            buffer.put( TLV.getBytes( preAuthenticationDataSeqLength ) );

            // The cksumtype, first the tag, then the value
            buffer.put( ( byte ) 0xA1 );
            buffer.put( TLV.getBytes( paDataTypeTagLength ) );
            Value.encode( buffer, paDataType.getOrdinal() );

            // The checksum, first the tag, then the value
            buffer.put( ( byte ) 0xA2 );
            buffer.put( TLV.getBytes( paDataValueTagLength ) );
            Value.encode( buffer, paDataValue );
        }
        catch ( BufferOverflowException boe )
        {
            log.error( I18n.err( I18n.ERR_145, 1 + TLV.getNbBytes( preAuthenticationDataSeqLength )
                + preAuthenticationDataSeqLength, buffer.capacity() ) );
            throw new EncoderException( I18n.err( I18n.ERR_138 ) );
        }

        if ( IS_DEBUG )
        {
            log.debug( "PreAuthenticationData encoding : {}", StringTools.dumpBytes( buffer.array() ) );
            log.debug( "PreAuthenticationData initial value : {}", toString() );
        }

        return buffer;
    }

    /**
     * @see Object#toString()
     */
    public String toString()
    {
        return toString( "" );
    }


    /**
     * @see Object#toString()
     */
    public String toString( String tabs )
    {
        StringBuilder sb = new StringBuilder();

        sb.append( tabs ).append( "PreAuthenticationData : {\n" );
        sb.append( tabs ).append( "    padata-type: " ).append( paDataType ).append( '\n' );

        if ( paDataValue != null )
        {
            sb.append( tabs + "    padata-value:" ).append( StringTools.dumpBytes( paDataValue ) ).append( '\n' );
        }

        sb.append( tabs + "}\n" );

        return sb.toString();
    }
}
