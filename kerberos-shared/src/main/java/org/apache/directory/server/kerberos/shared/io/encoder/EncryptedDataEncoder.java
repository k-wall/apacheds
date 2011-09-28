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

import org.apache.directory.server.kerberos.shared.messages.value.EncryptedData;
import org.apache.directory.shared.asn1.der.ASN1OutputStream;
import org.apache.directory.shared.asn1.der.DERInteger;
import org.apache.directory.shared.asn1.der.DEROctetString;
import org.apache.directory.shared.asn1.der.DERSequence;
import org.apache.directory.shared.asn1.der.DERTaggedObject;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 587682 $, $Date: 2007-10-24 00:47:43 +0200 (Mer, 24 oct 2007) $
 */
public class EncryptedDataEncoder
{
    /**
     * Encodes an {@link EncryptedData} into a byte array.
     *
     * @param encryptedData
     * @return The byte array.
     * @throws IOException
     */
    public static byte[] encode( EncryptedData encryptedData ) throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ASN1OutputStream aos = new ASN1OutputStream( baos );

        aos.writeObject( encodeSequence( encryptedData ) );
        aos.close();

        return baos.toByteArray();
    }


    /**
     * Encodes an {@link EncryptedData} into a {@link DERSequence}.
     * 
     * EncryptedData ::=   SEQUENCE {
     *             etype[0]     INTEGER, -- EncryptionEngine
     *             kvno[1]      INTEGER OPTIONAL,
     *             cipher[2]    OCTET STRING -- ciphertext
     * }
     * 
     * @param encryptedData 
     * @return The {@link DERSequence}.
     */
    public static DERSequence encodeSequence( EncryptedData encryptedData )
    {
        DERSequence sequence = new DERSequence();

        sequence.add( new DERTaggedObject( 0, DERInteger.valueOf( encryptedData.getEType().getOrdinal() ) ) );

        if ( encryptedData.hasKvno() )
        {
            sequence.add( new DERTaggedObject( 1, DERInteger.valueOf( encryptedData.getKvno() ) ) );
        }

        sequence.add( new DERTaggedObject( 2, new DEROctetString( encryptedData.getCipher() ) ) );

        return sequence;
    }
}
