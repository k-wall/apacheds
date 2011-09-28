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
package org.apache.directory.server.kerberos.shared.io.encoder;


import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.directory.server.kerberos.shared.messages.value.PaData;
import org.apache.directory.shared.asn1.der.ASN1OutputStream;
import org.apache.directory.shared.asn1.der.DERInteger;
import org.apache.directory.shared.asn1.der.DEROctetString;
import org.apache.directory.shared.asn1.der.DERSequence;
import org.apache.directory.shared.asn1.der.DERTaggedObject;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 587624 $, $Date: 2007-10-23 21:22:08 +0200 (Mar, 23 oct 2007) $
 */
public class PreAuthenticationDataEncoder
{
    /**
     * Encodes an array of {@link PaData}s into a byte array.
     *
     * @param preAuth
     * @return The byte array.
     * @throws IOException
     */
    public static byte[] encode( PaData[] preAuth ) throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ASN1OutputStream aos = new ASN1OutputStream( baos );

        aos.writeObject( encodeSequence( preAuth ) );
        aos.close();

        return baos.toByteArray();
    }


    /**
     * METHOD-DATA     ::= SEQUENCE OF PA-DATA
     */
    protected static DERSequence encodeSequence( PaData[] preAuth )
    {
        DERSequence sequence = new DERSequence();

        for ( int ii = 0; ii < preAuth.length; ii++ )
        {
            sequence.add( encode( preAuth[ii] ) );
        }

        return sequence;
    }


    /**
     * PA-DATA ::=        SEQUENCE {
     *         padata-type[1]        INTEGER,
     *         padata-value[2]       OCTET STRING
     * }
     */
    protected static DERSequence encode( PaData preAuth )
    {
        DERSequence sequence = new DERSequence();

        sequence.add( new DERTaggedObject( 1, DERInteger.valueOf( preAuth.getPaDataType().getOrdinal() ) ) );

        if ( preAuth.getPaDataValue() != null )
        {
            sequence.add( new DERTaggedObject( 2, new DEROctetString( preAuth.getPaDataValue() ) ) );
        }

        return sequence;
    }
}
